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

        // 4.5 🆕 后校验：信号极差时强制压分（防止AI虚高）
        validateAndCapScores(scoreResult, signals);

        // 5. 读取权重和目标线
        Map<String, Double> weights = preferenceService.getWeightsSnapshot(sessionId);
        Map<String, Integer> targets = preferenceService.getTargetsSnapshot(sessionId);

        // 如果没有用户偏好（旧面试），使用默认
        if (weights.isEmpty()) {
            weights = defaultWeights();
            targets = defaultTargets();
            log.info("使用默认权重/目标 sessionId={}", sessionId);
        } else {
            log.info("加载用户偏好权重={} 目标={} sessionId={}", weights, targets, sessionId);
        }

        // 6. 确定本次面试涉及的模块：有偏好则只用选中的5个，否则用默认
        Set<String> selectedModules;
        if (preferences != null && !preferences.isEmpty()) {
            selectedModules = preferences.stream().map(PreferenceItem::getCode).collect(Collectors.toSet());
        } else {
            selectedModules = weights.keySet(); // 旧面试兼容：默认5模块
        }

        // 7. Layer 3: 匹配度 + 画像 + 警报
        MatchResult matchResult = calculateMatch(scoreResult.scores, weights, targets, preferences);
        List<String> profileLabels = generateProfileLabel(scoreResult.scores, weights, preferences);
        List<String> alerts = checkAlerts(scoreResult.scores, weights, targets, preferences);

        // 8. 保存模块评分明细（只保存用户选中的模块）
        saveModuleScores(reportId, scoreResult, weights, targets, matchResult, selectedModules);

        // 9. 更新报告
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
        int garbageHits;         // 🆕 垃圾/乱敲检测命中次数
        int totalAnswers;        // 🆕 总回答条数（含追问回答）
        String scoringSource;    // AI / RULE

        double avgLength() {
            return answerCount == 0 ? 0 : (double) totalLength / answerCount;
        }

        String followupRatio() {
            if (followupAsked == 0) return "N/A（未触发追问）";
            return String.format("%d/%d (%.0f%%)", followupAnswered, followupAsked,
                    100.0 * followupAnswered / followupAsked);
        }

        /** 🆕 内容质量评分 0-1：综合垃圾率、信号丰富度、回答长度 */
        double contentQuality() {
            if (totalAnswers == 0) return 0;

            double garbageRate = (double) garbageHits / totalAnswers;
            double shortRate = (double) shortAnswers / Math.max(totalAnswers, 1);
            double vagueRate = (double) vagueHits / Math.max(totalAnswers, 1);
            double avgLen = avgLength();

            // ===== 垃圾回答占比 =====
            if (garbageRate >= 0.6) return 0.0;  // 全垃圾 → 精确 0
            if (garbageRate >= 0.4) return 0.04;
            if (garbageRate >= 0.2) return 0.10;

            // ===== 零有效信号 → 可能是简洁但正确的回答，不应过度惩罚 =====
            // 关键词信号（techHits/projectHits/logicHits等）只是辅助指标，
            // 不代表回答质量。AI评分才是质量判断的主要依据。
            boolean hasAnySignal = techHits > 0 || projectHits > 0 || logicHits > 0
                    || hasStarPattern > 0 || hasReflection > 0 || jobHits > 0;
            if (answerCount > 0 && !hasAnySignal) {
                // 只有伴随垃圾/敷衍时才是真问题
                if (garbageRate > 0) return 0.0;
                // 大量过短回答 → 质量偏低但不为0
                if (shortRate >= 0.5) return 0.2;
                // 没有垃圾、没有过短 → 可能是简洁的正确回答，给中等质量让AI判断
                return 0.55;
            }

            double quality = 1.0;

            // 垃圾/敷衍每条扣分加重
            quality -= garbageRate * 2.0;
            quality -= shortRate * 0.8;
            quality -= vagueRate * 0.5;

            // 平均长度
            if (avgLen < 10 && avgLen > 0) quality -= 0.6;
            else if (avgLen < 20 && avgLen > 0) quality -= 0.4;
            else if (avgLen < 30 && avgLen > 0) quality -= 0.2;

            // 信号贫乏惩罚
            if (techHits == 0 && answerCount > 0) quality -= 0.15;
            if (logicHits == 0 && answerCount > 0) quality -= 0.1;
            if (projectHits == 0 && answerCount > 0) quality -= 0.1;

            return Math.max(0, Math.min(1.0, quality));
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

    // 🆕 垃圾/乱敲检测模式
    private static final List<String> GARBAGE_PATTERNS = List.of(
            // 英文键盘乱敲
            "asdf", "qwer", "zxcv", "uiop", "jkl;", "hhhh", "aaaa", "ssss", "dddd",
            "ffff", "gggg", "jjjj", "kkkk", "llll", "tyui", "ghjk", "bnm,",
            // 中文乱敲/敷衍
            "测试测试", "随便", "乱打", "凑字数", "灌水", "12345", "abcd",
            "。。。", "......", "哈哈哈哈", "呵呵呵呵", "啦啦啦啦", "哦哦哦哦",
            "嗯嗯嗯嗯", "啊啊啊啊", "无语", "不知道说啥", "不想写", "懒得写",
            "随便写写", "凑合", "将就", "打发", "糊弄", "应付", "敷衍",
            // 无意义填充
            "字数补丁", "占位", "填充", "充数", "打酱油", "路过",
            "没什么可说", "无话可说", "无话");

    // 🆕 纯敷衍短语（整个回答就是这些之一）
    private static final List<String> BRUSH_OFF_PHRASES = List.of(
            "不会", "不知道", "不清楚", "不了解", "嗯", "哦", "好", "行", "可以", "还行",
            "差不多", "就这样", "没什么好说的", "随便", "你猜",
            "算了", "不会做", "太难了", "放弃", "跳过", "过", "pass", "skip",
            "不想回答", "拒绝回答", "无可奉告");

    /** 🆕 检测文本是否为无意义/乱敲 */
    private boolean isGarbage(String text) {
        if (text == null || text.trim().isEmpty()) return true;

        String t = text.trim().toLowerCase();

        // 1. 纯敷衍短语（整条回答就是敷衍词）
        for (String bp : BRUSH_OFF_PHRASES) {
            if (t.equals(bp) || t.replaceAll("[\\s.,;!?，。；！？…、\"']", "").equals(bp)) return true;
        }

        // 2. 极短且无意义（< 5字符的纯字母/数字/标点）
        if (t.length() < 5 && t.matches("[a-z0-9\\s.,;!?，。；！？…]+")) return true;

        // 3. 键盘乱敲/敷衍模式检测
        int garbagePatternHits = 0;
        for (String gp : GARBAGE_PATTERNS) {
            if (t.contains(gp)) garbagePatternHits++;
        }
        // 短文本只需命中1个模式即可判垃圾（如"随便打几个字"只有1个"随便"）
        if (t.length() < 25 && garbagePatternHits >= 1) return true;
        // 长文本需2个
        if (garbagePatternHits >= 2) return true;

        // 4. 纯字母连续重复5次以上（如 "aaaaaaa"）
        if (t.matches(".*([a-zA-Z])\\1{4,}.*")) return true;

        // 5. 纯中文字符但总长<8且全是高频敷衍字
        if (t.length() < 8 && t.matches("[一-鿿]+")) {
            String stripped = t.replaceAll("[不不知知道道会清清懂解了了没没用过忘随随便便算算了了]", "");
            if (stripped.length() < 3) return true;
        }

        // 6. 内容完全是标点/空格/数字
        if (t.replaceAll("[\\s.,;!?，。；！？…、\"'0-9]", "").length() < 3) return true;

        // 7. 🆕 中文字符熵检测：同一个字出现 > 总长50%（如 "哈哈哈哈哈测试哈"）
        if (t.length() >= 6) {
            int[] freq = new int[65536];
            int chineseChars = 0;
            for (char c : t.toCharArray()) {
                if (c >= 0x4e00 && c <= 0x9fff) {
                    freq[c]++;
                    chineseChars++;
                }
            }
            if (chineseChars >= 4) {
                int maxFreq = 0;
                for (int f : freq) { if (f > maxFreq) maxFreq = f; }
                if ((double) maxFreq / chineseChars > 0.5) return true;
            }
        }

        // 8. 🆕 纯英文无空格超长字符串（键盘滚过）
        if (t.length() > 10 && t.matches("[a-z]+") && !t.contains(" ")) {
            // 检查是否为自然英文：不含元音的比例过高则判垃圾
            int vowels = 0;
            for (char c : t.toCharArray()) {
                if ("aeiou".indexOf(c) >= 0) vowels++;
            }
            if ((double) vowels / t.length() < 0.15) return true;
        }

        return false;
    }

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
                s.totalAnswers++;
                if (isMeaningful(text)) {
                    s.followupAnswered++;
                }
                if (isGarbage(text)) s.garbageHits++;
                continue; // 追问回答不计入主指标
            }

            // 主问回答
            s.answerCount++;
            s.totalAnswers++;
            s.totalLength += text.length();
            if (text.length() < 15) s.shortAnswers++;
            if (text.length() > 200) s.longAnswers++;

            // 🆕 垃圾检测
            if (isGarbage(text)) s.garbageHits++;

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
     * 规则兜底：5个旧维度用现有公式，5个新模块给中性分。
     * 🆕 所有公式结果乘以内容质量系数，垃圾回答被大幅压低。
     */
    private Map<String, BigDecimal> fallbackRuleScoring(List<InterviewMessage> messages,
                                                         JobPosition job, Signals s) {
        Map<String, BigDecimal> scores = new LinkedHashMap<>();
        double quality = s.contentQuality();

        // 旧5维公式（基准分已降低 + 乘以质量系数）
        scores.put("technical_base", clamp(scoreKnowledgeRaw(s) * quality));
        scores.put("project_expression", clamp(scoreProjectRaw(s) * quality));
        scores.put("logical_structure", clamp(scoreLogicRaw(s) * quality));
        scores.put("position_cognition", clamp(scoreMatchRaw(s) * quality));
        scores.put("followup_adaptability", clamp(scoreFollowupRaw(s) * quality));

        // 新5模块：信号辅助 × 质量系数
        double depthScore = 45 + Math.min(35, s.techHits * 8.0);
        // 长度加分仅在有技术深度信号时才给
        if (s.techHits > 0 || s.hasStarPattern > 0) {
            depthScore += (s.avgLength() >= 80 ? 10 : s.avgLength() >= 50 ? 5 : 0);
        }
        depthScore -= s.vagueHits * 4.0;
        scores.put("technical_depth", clamp(depthScore * quality));
        scores.put("engineering_practice", clamp((55 + Math.min(15, s.projectHits * 3.0)) * quality));
        scores.put("problem_analysis", clamp((50 + Math.min(20, (s.logicHits + s.hasStarPattern) * 4.0)) * quality));
        scores.put("expression_clarity", clamp((60 - s.vagueHits * 5.0 - s.shortAnswers * 4.0) * quality));
        scores.put("project_review", clamp((50 + Math.min(25, s.hasReflection * 8.0)) * quality));

        return scores;
    }

    // 🆕 原始分计算（不含质量系数），供 fallbackRuleScoring 使用
    private double scoreKnowledgeRaw(Signals m) {
        if (m.answerCount == 0) return 0;
        return 50 + Math.min(40, m.techHits * 8.0) - m.vagueHits * 6.0 - m.shortAnswers * 5.0;
    }

    private double scoreProjectRaw(Signals m) {
        if (m.answerCount == 0) return 0;
        // 未涉及项目内容时给中性分而非低分（可能面试官没问项目）
        double base = m.projectHits > 0 ? 48 : 60;
        return base + Math.min(42, m.projectHits * 7.0) - m.shortAnswers * 5.0 - m.vagueHits * 4.0;
    }

    private double scoreLogicRaw(Signals m) {
        if (m.answerCount == 0) return 0;
        double score = 50 + Math.min(30, m.logicHits * 8.0);
        // 长度加分仅在回答有实质内容时才给（至少有逻辑/技术/项目/反思任一信号）
        boolean hasSubstance = m.logicHits > 0 || m.techHits > 0 || m.projectHits > 0
                || m.hasStarPattern > 0 || m.hasReflection > 0;
        if (hasSubstance) {
            if (m.avgLength() >= 60) score += 14;
            else if (m.avgLength() >= 30) score += 8;
        }
        if (m.avgLength() > 0 && m.avgLength() < 15) score -= 10;
        score -= m.vagueHits * 4.0;
        return score;
    }

    private double scoreMatchRaw(Signals m) {
        if (m.answerCount == 0) return 0;
        return 52 + Math.min(40, m.jobHits * 10.0) - m.shortAnswers * 4.0;
    }

    private double scoreFollowupRaw(Signals m) {
        if (m.answerCount == 0) return 0;
        // 未触发任何追问 → 回答完整全面，无需追问 → 接近满分
        if (m.followupAsked == 0) return 95;
        double ratio = (double) m.followupAnswered / m.followupAsked;
        return 45 + ratio * 45;
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

    // ============================ 🆕 后校验：信号压分 ============================

    /**
     * 根据信号质量对评分结果进行后校验，防止AI对垃圾/无关回答给出虚高分数。
     *
     * 压分策略：
     * - quality == 0（纯垃圾/全乱敲）   → 上限 0 分（不给任何分）
     * - quality < 0.08（严重无效内容）   → 上限 8 分
     * - quality < 0.15（大量无效内容）   → 上限 18 分
     * - quality < 0.30（内容贫乏）       → 上限 35 分
     * - quality < 0.45（明显偏弱）       → 上限 50 分
     * - quality >= 0.45                  → 不干预
     */
    private void validateAndCapScores(ScoreResult sr, Signals signals) {
        double quality = signals.contentQuality();
        int cap;

        if (quality == 0) {
            cap = 0;
        } else if (quality < 0.08) {
            cap = 8;
        } else if (quality < 0.15) {
            cap = 18;
        } else if (quality < 0.30) {
            cap = 35;
        } else if (quality < 0.45) {
            cap = 50;
        } else {
            return; // 质量尚可，不干预
        }

        int cappedCount = 0;
        for (Map.Entry<String, BigDecimal> entry : sr.scores.entrySet()) {
            double original = entry.getValue().doubleValue();
            if (original > cap) {
                entry.setValue(BigDecimal.valueOf(cap));
                cappedCount++;
            }
        }

        if (cappedCount > 0) {
            log.info("后校验压分: quality={} cap={} 压了{}/{}个模块",
                    String.format("%.3f", quality), cap, cappedCount, sr.scores.size());
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
                                   MatchResult match, Set<String> selectedModules) {
        for (Map.Entry<String, BigDecimal> entry : sr.scores.entrySet()) {
            String code = entry.getKey();
            // 只保存用户选中的模块（selectedModules 为空时保存全部，兼容旧数据）
            if (!selectedModules.isEmpty() && !selectedModules.contains(code)) {
                continue;
            }
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
            ms.setSuggestion(ensureDot(sr.suggestions.get(code)));
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

    /** 确保字符串以句号结尾 */
    private String ensureDot(String s) {
        if (s == null || s.isBlank()) return s;
        String t = s.strip();
        char last = t.charAt(t.length() - 1);
        if (last != '。' && last != '！' && last != '？' && last != '!' && last != '?' && last != '…' && last != '.') {
            return t + "。";
        }
        return t;
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
