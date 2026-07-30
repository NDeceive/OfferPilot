package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.config.UserContext;
import com.zhimian.entity.*;
import com.zhimian.mapper.*;
import com.zhimian.service.ModulePreferenceService.PreferenceItem;
import com.zhimian.service.ai.DeepSeekClient;
import com.zhimian.service.ai.ScorePromptBuilder;
import com.zhimian.service.ai.ScorePromptBuilder.ScoringContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 模块评分服务（Layer 1-3）。
 * <p>
 * Layer 1: 信号提取（规则，确定性）→ 15 个原子信号
 * Layer 2: 模块评分 → DeepSeek AI（TODO Step5）或规则兜底
 * Layer 3: 匹配度计算 + 画像 + 警报（规则，确定性）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleScoreService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final JobPositionMapper jobMapper;
    private final InterviewReportMapper reportMapper;
    private final InterviewModuleScoreMapper moduleScoreMapper;
    private final ScoreModuleMapper scoreModuleMapper;
    private final ModulePreferenceService preferenceService;
    private final ScorePromptBuilder scorePromptBuilder;
    private final DeepSeekClient deepSeekClient;

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String ROLE_INTERVIEWER = "INTERVIEWER";
    private static final String ROLE_CANDIDATE = "CANDIDATE";
    private static final String MSG_MAIN = "MAIN";
    private static final String MSG_FOLLOWUP = "FOLLOWUP";
    private static final String MSG_ANSWER = "ANSWER";

    /** 新模块（旧5维未覆盖的） */
    private static final List<String> NEW_MODULE_CODES = List.of(
            "technical_depth", "engineering_practice", "problem_analysis",
            "expression_clarity", "project_review"
    );

    /** 旧维度 → 新模块编码映射 */
    private static final Map<String, String> OLD_TO_NEW = Map.of(
            "专业知识掌握", "technical_base",
            "项目实践表达", "project_expression",
            "逻辑表达能力", "logical_structure",
            "岗位匹配程度", "position_cognition",
            "动态追问应对", "followup_adaptability"
    );

    // ============================ 内部数据结构 ============================

    /** AI评分完整结果（含分数、证据、建议、置信度） */
    static class ScoreResult {
        final Map<String, BigDecimal> scores = new LinkedHashMap<>();
        final Map<String, String> evidences = new HashMap<>();
        final Map<String, String> suggestions = new HashMap<>();
        final Map<String, BigDecimal> confidences = new HashMap<>();
        String overallComment;
        String scoringSource;
    }

    // ============================ 入口 ============================

    /**
     * 为已结束的会话生成模块评分并更新报告。
     * 由 InterviewFlowService.finish() 在 reportService.generateForSession() 之后调用。
     */
    public void scoreAndUpdateReport(Long sessionId, Long reportId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) return;

        InterviewReport report = reportMapper.selectById(reportId);
        if (report == null) return;

        JobPosition job = jobMapper.selectById(session.getJobId());

        // 1. 收集全部 Q&A
        List<InterviewMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<InterviewMessage>()
                        .eq(InterviewMessage::getSessionId, sessionId)
                        .orderByAsc(InterviewMessage::getId));

        // 2. 读取用户模块选择
        List<PreferenceItem> preferences = preferenceService.getPreference(sessionId);

        // 3. Layer 1: 信号提取
        Signals signals = extractSignals(messages, job);

        // 4. Layer 2: 模块评分
        ScoreResult scoreResult = scoreAllModules(messages, job, session, preferences, signals);
        signals.scoringSource = scoreResult.scoringSource;

        // 5. 读取权重和目标线
        Map<String, Double> weights = preferenceService.getWeightsSnapshot(sessionId);
        Map<String, Integer> targets = preferenceService.getTargetsSnapshot(sessionId);

        // 如果没有用户偏好（旧面试），使用默认
        if (weights.isEmpty()) {
            weights = defaultWeights();
            targets = defaultTargets();
        }

        // 6. Layer 3: 匹配度 + 画像 + 警报
        MatchResult matchResult = calculateMatch(scoreResult.scores, weights, targets, preferences);
        List<String> profileLabels = generateProfileLabel(scoreResult.scores, weights, preferences);
        List<String> alerts = checkAlerts(scoreResult.scores, weights, targets, preferences);

        // 7. 保存模块评分明细
        saveModuleScores(reportId, scoreResult, weights, targets, matchResult);

        // 8. 更新报告
        updateReport(report, matchResult, profileLabels, alerts);

        log.info("模块评分完成 sessionId={} reportId={} overallMatch={} source={}",
                sessionId, reportId, matchResult.overallMatch, signals.scoringSource);
    }

    // ============================ Layer 1: 信号提取 ============================

    /** 量化信号（扩展原 Metrics，新增字段供 DeepSeek 参考） */
    public static class Signals {
        int answerCount;
        int followupAsked;
        int followupAnswered;
        int totalLength;
        int vagueHits;
        int techHits;
        int projectHits;
        int logicHits;
        int jobHits;
        int shortAnswers;
        int longAnswers;         // >200字
        int hasStarPattern;      // STAR结构检测
        int hasReflection;       // 复盘反思信号
        String scoringSource;    // AI / RULE

        double avgLength() {
            return answerCount == 0 ? 0 : (double) totalLength / answerCount;
        }

        String followupRatio() {
            if (followupAsked == 0) return "N/A（未触发追问）";
            return String.format("%d/%d (%.0f%%)", followupAnswered, followupAsked,
                    100.0 * followupAnswered / followupAsked);
        }
    }

    // 含糊词
    private static final List<String> VAGUE_WORDS = List.of(
            "不知道", "不清楚", "不会", "不太了解", "不了解", "没用过", "忘了", "可能", "应该", "大概");

    // 项目信号词
    private static final List<String> PROJECT_WORDS = List.of(
            "项目", "负责", "实现", "系统", "接口", "数据库", "模块", "功能", "优化", "搭建", "设计", "重构");

    // 逻辑连接词
    private static final List<String> LOGIC_WORDS = List.of(
            "首先", "其次", "然后", "接着", "最后", "因为", "所以", "因此", "总结", "综上", "例如", "比如", "一方面", "另一方面");

    // 深度信号词
    private static final List<String> DEPTH_WORDS = List.of(
            "底层", "原理", "源码", "机制", "优缺点", "权衡", "边界", "极端", "tradeoff");

    // STAR结构信号词
    private static final List<String> STAR_WORDS = List.of(
            "背景", "目标", "方案", "结果", "收益", "量化", "提升", "降低了");

    // 反思信号词
    private static final List<String> REFLECTION_WORDS = List.of(
            "不足", "改进", "优化空间", "学到了", "成长", "如果重来", "下次会");

    private Signals extractSignals(List<InterviewMessage> messages, JobPosition job) {
        Signals s = new Signals();

        // 先标出哪些题被追问过
        Set<Long> followedUpQuestionIds = new HashSet<>();
        for (InterviewMessage msg : messages) {
            if (ROLE_INTERVIEWER.equals(msg.getRole()) && MSG_FOLLOWUP.equals(msg.getMsgType())) {
                s.followupAsked++;
                if (msg.getQuestionId() != null) {
                    followedUpQuestionIds.add(msg.getQuestionId());
                }
            }
        }

        // 岗位关键词
        List<String> jobKeywords = parseJsonList(job != null ? job.getKeywords() : null);
        Set<String> matchedJobKeywords = new HashSet<>();

        // 每题的答案计数（首条→主回答，其后→追问回答）
        Map<Long, Integer> answerSeenPerQuestion = new HashMap<>();

        for (InterviewMessage msg : messages) {
            if (!ROLE_CANDIDATE.equals(msg.getRole()) || !MSG_ANSWER.equals(msg.getMsgType())) {
                continue;
            }
            String text = msg.getContent() == null ? "" : msg.getContent().trim();
            Long qId = msg.getQuestionId();
            int seen = answerSeenPerQuestion.getOrDefault(qId, 0);
            answerSeenPerQuestion.put(qId, seen + 1);

            boolean isFollowupAnswer = seen >= 1 && qId != null && followedUpQuestionIds.contains(qId);

            if (isFollowupAnswer) {
                if (isMeaningful(text)) {
                    s.followupAnswered++;
                }
                continue; // 追问回答不计入主指标
            }

            // 主问回答
            s.answerCount++;
            s.totalLength += text.length();
            if (text.length() < 15) s.shortAnswers++;
            if (text.length() > 200) s.longAnswers++;

            String lower = text.toLowerCase();

            // 含糊词
            for (String vw : VAGUE_WORDS) {
                if (text.contains(vw)) s.vagueHits++;
            }
            // 项目信号
            for (String pw : PROJECT_WORDS) {
                if (text.contains(pw)) s.projectHits++;
            }
            // 逻辑连接
            for (String lw : LOGIC_WORDS) {
                if (text.contains(lw)) s.logicHits++;
            }
            // 深度信号
            for (String dw : DEPTH_WORDS) {
                if (lower.contains(dw)) s.techHits++;
            }
            // STAR结构
            for (String sw : STAR_WORDS) {
                if (text.contains(sw)) s.hasStarPattern++;
            }
            // 反思信号
            for (String rw : REFLECTION_WORDS) {
                if (text.contains(rw)) s.hasReflection++;
            }

            // 岗位关键词
            for (String jk : jobKeywords) {
                if (!jk.isEmpty() && lower.contains(jk.toLowerCase())) {
                    matchedJobKeywords.add(jk.toLowerCase());
                }
            }
        }
        s.jobHits = matchedJobKeywords.size();
        return s;
    }

    private boolean isMeaningful(String text) {
        if (text == null || text.trim().length() < 15) return false;
        for (String vw : VAGUE_WORDS) {
            if (text.contains(vw) && text.trim().length() < 30) return false;
        }
        return true;
    }

    // ============================ Layer 2: 模块评分 ============================

    /**
     * 对全部10个模块打分。优先DeepSeek AI，失败则规则兜底。
     */
    private ScoreResult scoreAllModules(List<InterviewMessage> messages,
                                         JobPosition job,
                                         InterviewSession session,
                                         List<PreferenceItem> preferences,
                                         Signals signals) {
        // 1. 尝试 DeepSeek AI 评分
        try {
            ScoreResult aiResult = callDeepSeekScoring(messages, job, session, preferences, signals);
            if (aiResult != null && aiResult.scores.size() >= 10) {
                log.info("DeepSeek评分完成 sessionId={}", session.getId());
                return aiResult;
            }
        } catch (Exception e) {
            log.warn("DeepSeek评分异常，降级规则兜底 sessionId={}: {}", session.getId(), e.getMessage());
        }

        // 2. 规则兜底
        log.info("使用规则兜底评分 sessionId={}", session.getId());
        ScoreResult ruleResult = new ScoreResult();
        ruleResult.scores.putAll(fallbackRuleScoring(messages, job, signals));
        ruleResult.scoringSource = "RULE";
        return ruleResult;
    }

    /**
     * 规则兜底：5个旧维度用现有公式，5个新模块给中性分60。
     */
    private Map<String, BigDecimal> fallbackRuleScoring(List<InterviewMessage> messages,
                                                         JobPosition job, Signals s) {
        Map<String, BigDecimal> scores = new LinkedHashMap<>();

        // 旧5维公式
        scores.put("technical_base", scoreKnowledge(s));
        scores.put("project_expression", scoreProject(s));
        scores.put("logical_structure", scoreLogic(s));
        scores.put("position_cognition", scoreMatch(s));
        scores.put("followup_adaptability", scoreFollowup(s));

        // 新5模块：信号辅助的中性分（不完全等于60，有一点区分度）
        scores.put("technical_depth", scoreDepth(s));
        scores.put("engineering_practice", clamp(55 + Math.min(15, s.projectHits * 3.0)));
        scores.put("problem_analysis", clamp(50 + Math.min(20, (s.logicHits + s.hasStarPattern) * 4.0)));
        scores.put("expression_clarity", clamp(60 - s.vagueHits * 5.0 - s.shortAnswers * 4.0));
        scores.put("project_review", clamp(50 + Math.min(25, s.hasReflection * 8.0)));

        return scores;
    }

    // -- 旧5维公式（与 ReportService 保持一致） --
    private BigDecimal scoreKnowledge(Signals m) {
        double score = 50 + Math.min(40, m.techHits * 8.0) - m.vagueHits * 6.0 - m.shortAnswers * 5.0;
        return clamp(m.answerCount == 0 ? 0 : score);
    }

    private BigDecimal scoreProject(Signals m) {
        double score = 48 + Math.min(42, m.projectHits * 7.0) - m.shortAnswers * 5.0 - m.vagueHits * 4.0;
        return clamp(m.answerCount == 0 ? 0 : score);
    }

    private BigDecimal scoreLogic(Signals m) {
        double score = 50 + Math.min(30, m.logicHits * 8.0);
        if (m.avgLength() >= 60) score += 14;
        else if (m.avgLength() >= 30) score += 8;
        else if (m.avgLength() > 0 && m.avgLength() < 15) score -= 10;
        score -= m.vagueHits * 4.0;
        return clamp(m.answerCount == 0 ? 0 : score);
    }

    private BigDecimal scoreMatch(Signals m) {
        double score = 52 + Math.min(40, m.jobHits * 10.0) - m.shortAnswers * 4.0;
        return clamp(m.answerCount == 0 ? 0 : score);
    }

    private BigDecimal scoreFollowup(Signals m) {
        if (m.answerCount == 0) return clamp(0);
        if (m.followupAsked == 0) return clamp(75);
        double ratio = (double) m.followupAnswered / m.followupAsked;
        return clamp(45 + ratio * 45);
    }

    private BigDecimal scoreDepth(Signals m) {
        // 技术深度：基于深度信号词 + 回答篇幅
        double score = 45 + Math.min(35, m.techHits * 8.0);
        if (m.avgLength() >= 80) score += 10;
        else if (m.avgLength() >= 50) score += 5;
        score -= m.vagueHits * 4.0;
        return clamp(m.answerCount == 0 ? 0 : score);
    }

    // ============================ DeepSeek 评分调用 ============================

    /**
     * 调用 DeepSeek 对10个模块打分。
     * 成功返回 ScoreResult（含证据/建议/置信度）；失败返回 null。
     */
    private ScoreResult callDeepSeekScoring(List<InterviewMessage> messages,
                                             JobPosition job,
                                             InterviewSession session,
                                             List<PreferenceItem> preferences,
                                             Signals signals) {
        // 1. 构建 Q&A 文本
        String qaText = buildQaTranscript(messages);

        // 2. 构建用户训练目标文本
        String prefsText = buildPreferencesText(preferences);

        // 3. 组装 ScoringContext
        int durationMinutes = session.getDurationSeconds() != null
                ? session.getDurationSeconds() / 60 : 30;
        ScoringContext ctx = ScoringContext.builder()
                .jobName(job != null ? job.getName() : "")
                .jobKeywords(formatJobKeywords(job))
                .durationMinutes(durationMinutes)
                .answerCount(signals.answerCount)
                .followupCount(signals.followupAsked)
                .preferencesText(prefsText)
                .totalLength(signals.totalLength)
                .avgLength(signals.avgLength())
                .techHits(signals.techHits)
                .projectHits(signals.projectHits)
                .logicHits(signals.logicHits)
                .vagueHits(signals.vagueHits)
                .shortAnswers(signals.shortAnswers)
                .followupRatio(signals.followupRatio())
                .qaTranscript(qaText)
                .build();

        // 4. 调用 DeepSeek
        String systemPrompt = scorePromptBuilder.systemPrompt();
        String userPrompt = scorePromptBuilder.userPrompt(ctx);
        JsonNode result = deepSeekClient.chatJson(systemPrompt, userPrompt);
        if (result == null) {
            return null;
        }

        // 5. 解析模块评分 + 证据 + 建议 + 置信度
        JsonNode modulesNode = result.path("modules");
        if (!modulesNode.isArray() || modulesNode.size() == 0) {
            log.warn("DeepSeek返回的modules为空或非数组");
            return null;
        }

        ScoreResult sr = new ScoreResult();
        sr.scoringSource = "AI";
        sr.overallComment = result.path("overall_comment").asText("");

        for (JsonNode node : modulesNode) {
            String code = node.path("code").asText();
            double score = node.path("score").asDouble();
            if (code.isEmpty() || score < 0 || score > 100) continue;

            sr.scores.put(code, BigDecimal.valueOf(score).setScale(1, RoundingMode.HALF_UP));

            // 证据（数组 → 逗号拼接）
            JsonNode evidenceArr = node.path("evidence");
            if (evidenceArr.isArray()) {
                List<String> items = new ArrayList<>();
                for (JsonNode e : evidenceArr) {
                    String txt = e.asText();
                    if (txt != null && !txt.isBlank()) items.add(txt);
                }
                if (!items.isEmpty()) sr.evidences.put(code, String.join("；", items));
            }

            // 建议
            String suggestion = node.path("suggestion").asText();
            if (!suggestion.isBlank()) sr.suggestions.put(code, suggestion);

            // 置信度
            double conf = node.path("confidence").asDouble();
            if (conf > 0) sr.confidences.put(code, BigDecimal.valueOf(conf));
        }

        if (sr.scores.size() < 10) {
            log.warn("DeepSeek返回的模块数不足10个: {}", sr.scores.size());
            return null;
        }

        return sr;
    }

    /** 将用户偏好格式化为可读文本 */
    private String buildPreferencesText(List<PreferenceItem> preferences) {
        if (preferences == null || preferences.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        Map<String, String> moduleNames = loadModuleNameMap();
        for (PreferenceItem p : preferences) {
            String name = moduleNames.getOrDefault(p.getCode(), p.getCode());
            String levelName = switch (p.getLevel()) {
                case 3 -> "核心突破(85分)";
                case 2 -> "重点提升(75分)";
                default -> "简单关注(65分)";
            };
            sb.append("- ").append(name).append("：排第").append(p.getRank())
                    .append("位，目标").append(levelName).append("\n");
        }
        return sb.toString();
    }

    private Map<String, String> loadModuleNameMap() {
        List<ScoreModule> all = scoreModuleMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, String> map = new HashMap<>();
        for (ScoreModule m : all) map.put(m.getCode(), m.getName());
        return map;
    }

    private String formatJobKeywords(JobPosition job) {
        if (job == null || job.getKeywords() == null) return "";
        try {
            List<String> kws = JSON.readValue(job.getKeywords(), new TypeReference<List<String>>() {});
            return String.join(", ", kws);
        } catch (Exception e) {
            return "";
        }
    }

    // ============================ Q&A 文本格式化 ============================

    /**
     * 将消息列表格式化为易读的Q&A文本。TODO Step5: 由 ScorePromptBuilder.userPrompt() 使用。
     */
    String buildQaTranscript(List<InterviewMessage> messages) {
        StringBuilder sb = new StringBuilder();
        int roundNo = 0;
        for (InterviewMessage msg : messages) {
            if (ROLE_INTERVIEWER.equals(msg.getRole()) && MSG_MAIN.equals(msg.getMsgType())) {
                roundNo = msg.getRoundNo();
                sb.append("\n--- 第").append(roundNo).append("题 ---\n");
                sb.append("Q").append(roundNo).append(": ").append(safe(msg.getContent())).append("\n");
            } else if (ROLE_CANDIDATE.equals(msg.getRole()) && MSG_ANSWER.equals(msg.getMsgType())) {
                String label = msg.getContent() != null && msg.getContent().length() < 15
                        ? "A" + roundNo + "(过短): " : "A" + roundNo + ": ";
                sb.append(label).append(safe(msg.getContent())).append("\n");
            } else if (ROLE_INTERVIEWER.equals(msg.getRole()) && MSG_FOLLOWUP.equals(msg.getMsgType())) {
                sb.append("  追问: ").append(safe(msg.getContent())).append("\n");
            }
        }
        return sb.toString();
    }

    // ============================ Layer 3: 匹配度计算 ============================

    static class MatchResult {
        double overallMatch;
        Map<String, Double> moduleMatches = new LinkedHashMap<>();
        Map<String, Double> gaps = new LinkedHashMap<>();
        Map<String, Double> priorities = new LinkedHashMap<>();
    }

    private MatchResult calculateMatch(Map<String, BigDecimal> moduleScores,
                                        Map<String, Double> weights,
                                        Map<String, Integer> targets,
                                        List<PreferenceItem> preferences) {
        MatchResult r = new MatchResult();

        // 确定参与计算的模块：有偏好用偏好，否则用全部模块（等权）
        Set<String> selectedModules;
        if (preferences != null && !preferences.isEmpty()) {
            selectedModules = preferences.stream().map(PreferenceItem::getCode).collect(Collectors.toSet());
        } else {
            selectedModules = moduleScores.keySet();
        }

        double totalMatch = 0;
        double totalWeight = 0;

        for (String code : selectedModules) {
            BigDecimal score = moduleScores.getOrDefault(code, BigDecimal.ZERO);
            double rawScore = score.doubleValue();
            double target = targets.getOrDefault(code, 75); // 默认75
            double weight = weights.getOrDefault(code, 0.20); // 默认等权

            double moduleMatch = Math.min(rawScore / Math.max(target, 1), 1.2);
            double gap = Math.max(target - rawScore, 0);
            double priority = weight * gap;

            r.moduleMatches.put(code, round2(moduleMatch));
            r.gaps.put(code, round2(gap));
            r.priorities.put(code, round2(priority));

            totalMatch += weight * moduleMatch;
            totalWeight += weight;
        }

        // 归一化
        r.overallMatch = totalWeight > 0 ? round2(totalMatch / totalWeight * 100) : 0;
        return r;
    }

    // ============================ 画像标签 ============================

    private List<String> generateProfileLabel(Map<String, BigDecimal> scores,
                                               Map<String, Double> weights,
                                               List<PreferenceItem> preferences) {
        List<String> labels = new ArrayList<>();

        double projScore = scores.getOrDefault("project_expression", BigDecimal.ZERO).doubleValue();
        double followScore = scores.getOrDefault("followup_adaptability", BigDecimal.ZERO).doubleValue();
        double engScore = scores.getOrDefault("engineering_practice", BigDecimal.ZERO).doubleValue();
        double depthScore = scores.getOrDefault("technical_depth", BigDecimal.ZERO).doubleValue();
        double posScore = scores.getOrDefault("position_cognition", BigDecimal.ZERO).doubleValue();

        if (projScore >= 80) labels.add("项目表达突破型");
        if (followScore >= 80) labels.add("追问补救成长型");
        if (engScore < 65 && isHighRank("engineering_practice", weights)) labels.add("工程实践补强型");
        if (depthScore >= 85) labels.add("技术深度冲刺型");
        if (posScore >= 80) labels.add("岗位认知强化型");

        // 均衡训练模式：全并列 + 分数标准差小
        if (preferences != null && preferences.size() == 5) {
            boolean allSameRank = preferences.stream().map(PreferenceItem::getRank).distinct().count() == 1;
            if (allSameRank) {
                double avg = scores.values().stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
                double variance = scores.values().stream()
                        .mapToDouble(s -> Math.pow(s.doubleValue() - avg, 2)).average().orElse(0);
                if (Math.sqrt(variance) < 8) labels.add("均衡提升型");
            }
        }

        if (labels.isEmpty()) labels.add("综合发展型");
        return labels;
    }

    private boolean isHighRank(String moduleCode, Map<String, Double> weights) {
        if (weights.isEmpty()) return false;
        Double w = weights.get(moduleCode);
        if (w == null) return false;
        return w >= weights.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.30) * 0.9;
    }

    // ============================ 警报检查 ============================

    private List<String> checkAlerts(Map<String, BigDecimal> scores,
                                      Map<String, Double> weights,
                                      Map<String, Integer> targets,
                                      List<PreferenceItem> preferences) {
        List<String> alerts = new ArrayList<>();
        if (preferences == null || preferences.isEmpty()) return alerts;

        for (PreferenceItem pref : preferences) {
            double rawScore = scores.getOrDefault(pref.getCode(), BigDecimal.ZERO).doubleValue();
            double target = targets.getOrDefault(pref.getCode(), 75);
            double moduleMatch = Math.min(rawScore / Math.max(target, 1), 1.2);

            // 警报1: 排第1的模块且匹配度 < 0.75
            if (pref.getRank() == 1 && moduleMatch < 0.75) {
                alerts.add("核心训练目标差距明显：" + pref.getCode() + " 匹配度仅 " + round2(moduleMatch * 100) + "%");
            }
            // 警报2: 核心突破(level=3)且匹配度 < 0.85
            if (pref.getLevel() == 3 && moduleMatch < 0.85) {
                alerts.add("核心突破目标未达成：" + pref.getCode() + " 匹配度 " + round2(moduleMatch * 100) + "%");
            }
        }
        return alerts;
    }

    // ============================ 持久化 ============================

    private void saveModuleScores(Long reportId, ScoreResult sr,
                                   Map<String, Double> weights, Map<String, Integer> targets,
                                   MatchResult match) {
        for (Map.Entry<String, BigDecimal> entry : sr.scores.entrySet()) {
            String code = entry.getKey();
            double rawScore = entry.getValue().doubleValue();
            double target = targets.getOrDefault(code, 75);
            double weight = weights.getOrDefault(code, 0.20);
            double moduleMatch = match.moduleMatches.getOrDefault(code, 0.0);
            double gap = match.gaps.getOrDefault(code, 0.0);
            double priority = match.priorities.getOrDefault(code, 0.0);

            InterviewModuleScore ms = new InterviewModuleScore();
            ms.setReportId(reportId);
            ms.setModuleCode(code);
            ms.setRawScore(bd(rawScore));
            ms.setTargetScore(bd(target));
            ms.setModuleMatch(bd(moduleMatch));
            ms.setBaseWeight(bd(weight));
            ms.setGapScore(bd(gap));
            ms.setImprovementPriority(bd(priority));
            ms.setScoreSource(sr.scoringSource);
            ms.setEvidence(sr.evidences.get(code));
            ms.setSuggestion(sr.suggestions.get(code));
            ms.setAiConfidence(sr.confidences.get(code));
            moduleScoreMapper.insert(ms);
        }
    }

    private void updateReport(InterviewReport report, MatchResult match,
                               List<String> profileLabels, List<String> alerts) {
        report.setOverallMatchScore(bd(match.overallMatch));
        report.setDisplayLevel(matchLevel(match.overallMatch));
        report.setProfileLabel(String.join(",", profileLabels));
        // reportJson 和 visualizationJson 暂由前端自行组装，后续可扩展
        reportMapper.updateById(report);
    }

    // ============================ 默认配置（旧面试兼容） ============================

    private Map<String, Double> defaultWeights() {
        // 5个最常见的模块等权
        Map<String, Double> w = new LinkedHashMap<>();
        String[] defaults = {"technical_base", "project_expression", "logical_structure",
                "position_cognition", "followup_adaptability"};
        for (String code : defaults) w.put(code, 0.20);
        return w;
    }

    private Map<String, Integer> defaultTargets() {
        Map<String, Integer> t = new LinkedHashMap<>();
        String[] defaults = {"technical_base", "project_expression", "logical_structure",
                "position_cognition", "followup_adaptability"};
        for (String code : defaults) t.put(code, 75);
        return t;
    }

    // ============================ 工具方法 ============================

    private String matchLevel(double overallMatch) {
        if (overallMatch >= 100) return "已达到本轮训练目标";
        if (overallMatch >= 85) return "接近本轮训练目标";
        if (overallMatch >= 70) return "部分达到，需继续训练";
        return "差距较大，需专项补强";
    }

    private BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal clamp(double v) {
        if (v < 0) v = 0;
        if (v > 100) v = 100;
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP);
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
