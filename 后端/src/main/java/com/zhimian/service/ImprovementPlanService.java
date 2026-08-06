package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.zhimian.config.AiProperties;
import com.zhimian.entity.InterviewMessage;
import com.zhimian.entity.InterviewModuleScore;
import com.zhimian.entity.InterviewReport;
import com.zhimian.entity.InterviewSession;
import com.zhimian.entity.JobPosition;
import com.zhimian.entity.ScoreModule;
import com.zhimian.mapper.InterviewMessageMapper;
import com.zhimian.mapper.InterviewModuleScoreMapper;
import com.zhimian.mapper.InterviewReportMapper;
import com.zhimian.mapper.InterviewSessionMapper;
import com.zhimian.mapper.JobPositionMapper;
import com.zhimian.mapper.ScoreModuleMapper;
import com.zhimian.service.ai.DeepSeekClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 个性化提升计划服务。
 * 将候选人实际 Q&A + 模块差距发给 DeepSeek，
 * 生成引用原话、针对具体问题的建议（非套话）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImprovementPlanService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewReportMapper reportMapper;
    private final InterviewModuleScoreMapper moduleScoreMapper;
    private final ScoreModuleMapper scoreModuleMapper;
    private final JobPositionMapper jobMapper;
    private final DeepSeekClient deepSeekClient;
    private final AiProperties aiProps;

    private static final String ROLE_INTERVIEWER = "INTERVIEWER";
    private static final String ROLE_CANDIDATE = "CANDIDATE";
    private static final String MSG_MAIN = "MAIN";
    private static final String MSG_ANSWER = "ANSWER";
    private static final String MSG_FOLLOWUP = "FOLLOWUP";

    /**
     * 为指定报告生成个性化提升建议。
     * @return moduleCode → SuggestionItem 的映射
     */
    public Map<String, SuggestionItem> generate(Long reportId) {
        InterviewReport report = reportMapper.selectById(reportId);
        if (report == null || report.getSessionId() == null) return Collections.emptyMap();

        InterviewSession session = sessionMapper.selectById(report.getSessionId());
        if (session == null) return Collections.emptyMap();

        // 1. 加载有差距的模块评分
        List<InterviewModuleScore> gapModules = moduleScoreMapper.selectList(
                new LambdaQueryWrapper<InterviewModuleScore>()
                        .eq(InterviewModuleScore::getReportId, reportId)
                        .gt(InterviewModuleScore::getGapScore, BigDecimal.ZERO)
                        .orderByDesc(InterviewModuleScore::getImprovementPriority));
        if (gapModules.isEmpty()) return Collections.emptyMap();

        // 2. 加载 Q&A 记录
        List<InterviewMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<InterviewMessage>()
                        .eq(InterviewMessage::getSessionId, session.getId())
                        .orderByAsc(InterviewMessage::getId));

        // 3. 加载模块中文名
        Map<String, String> moduleNames = loadModuleNames();

        // 4. 加载岗位
        JobPosition job = jobMapper.selectById(session.getJobId());

        // 5. 尝试 DeepSeek
        Map<String, SuggestionItem> result;
        if (aiProps.isUsable()) {
            try {
                result = callDeepSeek(gapModules, messages, moduleNames, job);
            } catch (Exception e) {
                log.warn("DeepSeek 提升建议生成失败，使用规则兜底 reportId={}: {}", reportId, e.getMessage());
                result = ruleBasedSuggestions(gapModules, messages, moduleNames);
            }
        } else {
            result = ruleBasedSuggestions(gapModules, messages, moduleNames);
        }

        // 6. 回头审查：确保每条建议句尾都有标点
        for (SuggestionItem item : result.values()) {
            item.diagnosis = ensureTrailingPunctuation(item.diagnosis);
            item.actionPlan = ensureTrailingPunctuation(item.actionPlan);
        }
        return result;
    }

    // ============================ DeepSeek 调用 ============================

    private Map<String, SuggestionItem> callDeepSeek(
            List<InterviewModuleScore> gapModules,
            List<InterviewMessage> messages,
            Map<String, String> moduleNames,
            JobPosition job) {

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(gapModules, messages, moduleNames, job);
        JsonNode result = deepSeekClient.chatJson(systemPrompt, userPrompt);

        if (result == null) return Collections.emptyMap();

        JsonNode arr = result.path("suggestions");
        if (!arr.isArray() || arr.size() == 0) return Collections.emptyMap();

        Map<String, SuggestionItem> map = new LinkedHashMap<>();
        for (JsonNode node : arr) {
            String code = node.path("moduleCode").asText();
            if (code.isEmpty()) continue;
            map.put(code, new SuggestionItem(
                    normalizePunctuation(node.path("diagnosis").asText("")),
                    normalizePunctuation(node.path("actionPlan").asText("")),
                    node.path("tone").asText("neutral")));
        }
        return map;
    }

    private String buildSystemPrompt() {
        return """
                你是一个面试反馈教练。你的任务是根据候选人的面试回答记录，
                为每个薄弱模块生成个性化、具体、可操作的提升建议。

                ## 核心原则
                1. **看菜吃饭、实事求是**
                   - 候选人乱敲键盘、答非所问 → 直接指出"你的回答是无效内容，请认真对待每次练习"
                   - 候选人该模块已经表现很好、差距很小 → 先肯定"这一方面已经表现完善"，再提1个可细化/加强的小方向
                   - 候选人答得好、只是距离目标还有差距 → 真诚肯定优点，再给进阶建议
                   - 候选人有明显知识盲区 → 精准指出哪里错了，引用原话，给正确方向
                   - 措辞不要总是"建议XXX"，可以用"可以进一步""还可以细化""值得深挖"等更自然的表达

                2. **严禁套话 & 严禁推荐具体教材（这是硬性规则）**
                   禁止使用这些空洞表述：
                   - "建议多练习" "加强学习" "注重细节" "提升能力"
                   - "建议系统学习" "建议深入了解" "建议多加思考"
                   🚫 禁止推荐任何具名学习资料，包括但不限于：
                   - 书籍/教材及其章节：如《XXX》《XXX实战》《XXX权威指南》"第X章"
                   - 论文、博客、视频课程、网站链接、官方文档之外的具名资源
                   - 即使你觉得推荐某本书对候选人有帮助，也绝不可以提书名
                   ✅ 正确做法：只描述需要学习的技术方向或知识点
                   - ❌ "建议阅读《Java并发编程实战》第1-2章"
                   - ✅ "建议重点掌握线程安全、锁机制和并发容器的使用场景"

                3. **简洁有力**
                   - diagnosis: 1-2句话，问题在哪、为什么是问题
                   - actionPlan: 1-2句话，具体可以怎么做
                   - 不要写小作文

                4. **tone 字段**
                   - "critical": 候选人乱答/敷衍 → 严肃提醒
                   - "encouraging": 候选人有基础但需提升 → 鼓励为主
                   - "neutral": 客观分析

                ## 输出格式（严格JSON，无markdown包裹）
                {
                  "suggestions": [
                    {
                      "moduleCode": "technical_base",
                      "diagnosis": "你在第2题被问到JVM内存模型时，回答'不太清楚，可能和堆栈有关'，这暴露出基础知识薄弱。",
                      "actionPlan": "建议重点掌握JVM内存区域的职责划分——堆、栈、方法区各自存什么、生命周期如何，下次面试能用准确术语描述清楚。",
                      "tone": "critical"
                    }
                  ]
                }
                """;
    }

    private String buildUserPrompt(
            List<InterviewModuleScore> gapModules,
            List<InterviewMessage> messages,
            Map<String, String> moduleNames,
            JobPosition job) {

        StringBuilder sb = new StringBuilder();
        sb.append("## 岗位\n");
        sb.append(job != null ? job.getName() : "未知").append("\n\n");

        sb.append("## 薄弱模块（有差距的模块）\n");
        for (InterviewModuleScore ms : gapModules) {
            String name = moduleNames.getOrDefault(ms.getModuleCode(), ms.getModuleCode());
            sb.append("- ").append(name)
                    .append("：得分 ").append(ms.getRawScore())
                    .append(" / 目标 ").append(ms.getTargetScore())
                    .append("（差距 ").append(ms.getGapScore()).append("分）\n");
        }
        sb.append("\n");

        sb.append("## 候选人完整问答记录\n");
        sb.append(formatMessages(messages));

        return sb.toString();
    }

    private String formatMessages(List<InterviewMessage> messages) {
        StringBuilder sb = new StringBuilder();
        int qNo = 0;
        for (InterviewMessage msg : messages) {
            if (ROLE_INTERVIEWER.equals(msg.getRole()) && MSG_MAIN.equals(msg.getMsgType())) {
                qNo++;
                sb.append("\n【第").append(qNo).append("题】");
                sb.append(msg.getContent()).append("\n");
            } else if (ROLE_CANDIDATE.equals(msg.getRole()) && MSG_ANSWER.equals(msg.getMsgType())) {
                String content = msg.getContent() == null ? "" : msg.getContent().trim();
                sb.append("→ 候选人回答：").append(content).append("\n");
                if (content.length() < 15) sb.append("  ⚠️ 过短回答\n");
            } else if (ROLE_INTERVIEWER.equals(msg.getRole()) && MSG_FOLLOWUP.equals(msg.getMsgType())) {
                sb.append("  追问：").append(msg.getContent()).append("\n");
            }
        }
        return sb.toString();
    }

    // ============================ 规则兜底 ============================

    private Map<String, SuggestionItem> ruleBasedSuggestions(
            List<InterviewModuleScore> gapModules,
            List<InterviewMessage> messages,
            Map<String, String> moduleNames) {

        // 检测是否为垃圾回答
        boolean allGarbage = isAllGarbage(messages);

        Map<String, SuggestionItem> map = new LinkedHashMap<>();
        for (InterviewModuleScore ms : gapModules) {
            String name = moduleNames.getOrDefault(ms.getModuleCode(), ms.getModuleCode());
            if (allGarbage) {
                map.put(ms.getModuleCode(), new SuggestionItem(
                        "你在本次面试中的回答大部分是无效内容（乱敲键盘或敷衍短语）。",
                        "模拟面试的目的是帮助你发现不足。请认真对待每一次练习，用真实的技术回答来获得有价值的反馈。",
                        "critical"));
            } else {
                map.put(ms.getModuleCode(), new SuggestionItem(
                        "「" + name + "」得分 " + ms.getRawScore() + "，距离目标 " + ms.getTargetScore() + " 差了 " + ms.getGapScore() + " 分。",
                        "建议针对「" + name + "」做专项训练，下次面试时重点展示该维度的能力。",
                        "encouraging"));
            }
        }
        return map;
    }

    private boolean isAllGarbage(List<InterviewMessage> messages) {
        int answerCount = 0, garbageCount = 0;
        for (InterviewMessage msg : messages) {
            if (ROLE_CANDIDATE.equals(msg.getRole()) && MSG_ANSWER.equals(msg.getMsgType())) {
                answerCount++;
                String text = msg.getContent() == null ? "" : msg.getContent().trim();
                if (text.isEmpty() || text.length() < 5 || isKeyboardMash(text)) {
                    garbageCount++;
                }
            }
        }
        return answerCount > 0 && (double) garbageCount / answerCount > 0.6;
    }

    private boolean isKeyboardMash(String text) {
        String t = text.toLowerCase();
        // 纯乱敲检测
        if (t.matches(".*([a-z])\\1{4,}.*")) return true;
        if (t.length() > 5 && t.matches("[a-z]+") && !t.contains(" ")) {
            int vowels = 0;
            for (char c : t.toCharArray()) { if ("aeiou".indexOf(c) >= 0) vowels++; }
            if ((double) vowels / t.length() < 0.2) return true;
        }
        return false;
    }

    private Map<String, String> loadModuleNames() {
        return scoreModuleMapper.selectList(new LambdaQueryWrapper<>())
                .stream().collect(Collectors.toMap(ScoreModule::getCode, ScoreModule::getName, (a, b) -> a));
    }

    /** 规范化标点：确保句尾有终止符 + 过滤掉书名推荐 */
    private String normalizePunctuation(String text) {
        if (text == null || text.isBlank()) return "";
        // 0. 兜底：过滤掉任何书名/教材引用
        String t = text.strip()
                // 中文书名号《XXX》（含各种右括号变体：》〉> »）
                .replaceAll("《[^《》〉>»\\)]+[》〉>»\\)]\\s*第?[\\d\\-一二三四五六七八九十]+章?", "")
                .replaceAll("《[^《》〉>»\\)]+[》〉>»\\)]", "")
                // "建议从《XXX》开始" → 去掉"从开始"这种断句
                .replaceAll("[，,]?\\s*从[^，,。！？!?]{0,6}开始入手", "")
                .replaceAll("[，,]?\\s*建议从[^，,。！？!?]{0,6}开始入手", "")
                // 英文书名 "XXX" by Author, 第X章
                .replaceAll("[\"']?[A-Z][a-zA-Z\\s]+[\"']?\\s*(by\\s+\\w+)?\\s*第[\\d]+章", "")
                // 不要的书名残留变成空句 → 清理
                .replaceAll("建议从\\s*开始入手", "")
                .replaceAll("从\\s*开始入手", "")
                .replaceAll("[\\x00-\\x1f\\x7f\\u200b\\u200e\\u200f\\ufeff]", "")
                .replaceAll("[，,;；]+$", "")
                .replaceAll("\\s{2,}", " ")
                .strip();
        if (t.isEmpty()) return "";
        // 最后一个字符如果不是终止符就补上
        char last = t.charAt(t.length() - 1);
        if (last != '。' && last != '！' && last != '？' && last != '!' && last != '?' && last != '…' && last != '.') {
            t += "。";
        }
        return t;
    }

    // ============================ DTO ============================

    /** 回头审查：确保句尾有终止符（。！？!?…），没有就补上 */
    private static String ensureTrailingPunctuation(String text) {
        if (text == null || text.isBlank()) return "";
        // 从末尾往前找到第一个可见字符
        String t = text.strip();
        for (int i = t.length() - 1; i >= 0; i--) {
            char c = t.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) continue;
            if (c == '。' || c == '！' || c == '？' || c == '!' || c == '?' || c == '…') return t;
            if (i == t.length() - 1) return t + "。";
            // 末尾有不可见字符 → 截掉它们再补句号
            return t.substring(0, i + 1) + "。";
        }
        return t + "。";
    }

    // ============================ DTO ============================

    public static class SuggestionItem {
        public String diagnosis;
        public String actionPlan;
        public final String tone;

        public SuggestionItem(String diagnosis, String actionPlan, String tone) {
            this.diagnosis = diagnosis;
            this.actionPlan = actionPlan;
            this.tone = tone;
        }
    }
}
