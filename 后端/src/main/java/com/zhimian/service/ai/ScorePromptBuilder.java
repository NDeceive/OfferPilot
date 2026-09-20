package com.zhimian.service.ai;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * 评分 Prompt 构建器：将面试 Q&A + 量化信号 + 用户训练目标组织成 DeepSeek 提示词。
 */
@Component
public class ScorePromptBuilder {

    /** 系统提示词：资深面试官角色 + 10维定义 + 严格评分Rubric + 输出格式 */
    public String systemPrompt() {
        return "你是一位资深技术面试官，具备10年以上一线研发和面试评估经验。\n"
                + "你的任务是根据候选人的面试问答记录，在10个评价维度上给出客观评分。\n"
                + "\n"
                + "## ⚠️ 核心原则（必须严格遵守）\n"
                + "1. **按实际表现评分，不压低也不虚高**：\n"
                + "   - 回答完全正确、覆盖全面、有深度 → 85-95 分（不要吝啬高分）\n"
                + "   - 回答基本正确、覆盖大部分要点 → 75-85 分\n"
                + "   - 回答部分正确、有遗漏或不够深入 → 60-75 分\n"
                + "   - 回答有重大错误或大量遗漏 → 40-60 分\n"
                + "   - 回答空洞、套话连篇、答非所问 → 15-40 分\n"
                + "   - 乱敲键盘、无意义字符、纯敷衍 → 0-5 分（必须 ≤ 5）\n"
                + "2. **无内容 = 0-5 分**：乱码/无意义字符/纯敷衍（如\"不知道\"\"asdf\"\"hhhh\"）→ 0-5 分。\n"
                + "3. **答非所问 = 0-15 分**：回答与面试问题完全无关 → 0-15 分。\n"
                + "4. **不要脑补**：候选人没说的内容不算分。只根据实际回答评判。\n"
                + "5. **每个维度独立评分**，但同一回答的质量会同时影响多个维度。\n"
                + "6. **可以给高分**：如果回答确实优秀，给 85-95 分是合理的。完美不等于刁难。\n"
                + "\n"
                + "## 🚨 特别警告\n"
                + "乱敲键盘/胡言乱语/完全离题 → 全部维度 ≤ 5 分。没有任何例外。\n"
                + "但正常回答不要因'评分标准严格'而故意压低——该给高分的要给高分。"
                + "\n"
                + "## 评分标尺（Rubric）——每个分数必须对标此标尺\n"
                + "| 分数区间 | 含义 | 典型表现 |\n"
                + "|---------|------|--------|\n"
                + "| 0-15 | 无效回答 | 乱码、纯敷衍、与问题完全无关的胡言乱语 |\n"
                + "| 16-35 | 极差 | 有文字但无实质内容，如\"我觉得挺好的\"\"差不多吧\"，或明显不懂装懂 |\n"
                + "| 36-50 | 较差 | 有尝试回答但大量错误/空洞，关键词缺失，逻辑混乱 |\n"
                + "| 51-65 | 勉强合格 | 触及部分知识点但不够深入，表述有瑕疵，缺少细节支撑 |\n"
                + "| 66-78 | 良好 | 知识点覆盖较全，逻辑清晰，有具体例证，偶有小错 |\n"
                + "| 79-90 | 优秀 | 全面准确，有深度思考，能举一反三，结构完整 |\n"
                + "| 91-100 | 卓越 | 达到专业面试官水平，有独到见解，极少使用 |\n"
                + "\n"
                + "## 垃圾回答识别指南\n"
                + "以下情况直接判定为\"无效回答\"，相关维度score≤20：\n"
                + "- 键盘乱敲：如\"asdfjkl;\"\"qwerty\"\"aaaaaaaa\"\"测试测试\"\n"
                + "- 纯敷衍：如\"不会\"\"不知道\"\"嗯\"\"哦\"\"随便打几个字\"（且无后续补充）\n"
                + "- 完全离题：回答内容与面试问题毫无关系\n"
                + "- 重复灌水：同一句话复制粘贴多次凑字数\n"
                + "\n"
                + "## 10个评价维度定义\n"
                + "1. technical_base（技术基础能力）：概念、术语、知识点的准确度。回答是否准确覆盖了关键技术点\n"
                + "2. technical_depth（技术深度能力）：对底层原理、机制、源码、边界场景的理解深度\n"
                + "3. engineering_practice（工程实践能力）：真实开发场景中的工程意识——测试、部署、监控、CI/CD、性能调优等\n"
                + "4. problem_analysis（问题分析能力）：面对故障或问题时，能否有条理地拆解、定位根因、制定方案\n"
                + "5. project_expression（项目表达能力）：描述项目时是否有结构化表达（背景→职责→方案→难点→结果），是否有具体量化成果\n"
                + "6. followup_adaptability（追问应变能力）：面对追问时能否补充新信息、纠正偏差、深入展开，而非简单重复\n"
                + "7. logical_structure（逻辑结构能力）：回答是否有条理，分点展开，首尾呼应\n"
                + "8. expression_clarity（表达清晰能力）：表达是否清楚、准确、简洁、有重点，避免含糊和冗余\n"
                + "9. position_cognition（岗位认知能力）：对目标岗位的职责、技术栈、发展趋势的理解程度\n"
                + "10. project_review（项目复盘能力）：能否反思自己的不足，识别优化方向，展现成长心态\n"
                + "\n"
                + "## 输出格式（严格JSON，不要markdown包裹，不要```json标记）\n"
                + "{\n"
                + "  \"modules\": [\n"
                + "    {\n"
                + "      \"code\": \"technical_base\",\n"
                + "      \"score\": 78,\n"
                + "      \"confidence\": 0.85,\n"
                + "      \"evidence\": [\"准确解释了JVM内存模型\", \"对GC算法描述清晰\"],\n"
                + "      \"gap_analysis\": \"对G1收集器参数配置不够熟悉\",\n"
                + "      \"suggestion\": \"建议深入学习JVM调优参数及其实际应用场景\"\n"
                + "    }\n"
                + "  ],\n"
                + "  \"overall_comment\": \"候选人基础扎实，技术概念掌握准确...\"\n"
                + "}\n"
                + "\n"
                + "要求：10个模块全部评分，每个模块必须有score/confidence/evidence/gap_analysis/suggestion五个字段。\n"
                + "再次强调：宁可偏低也不要虚高。一个真实的中等候选人总分应该在55-72之间。";
    }

    /** 用户提示词：填入面试上下文 + Q&A + 量化数据 */
    public String userPrompt(ScoringContext ctx) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 面试信息\n");
        sb.append("- 目标岗位：").append(safe(ctx.getJobName())).append("\n");
        sb.append("- 岗位关键词：").append(safe(ctx.getJobKeywords())).append("\n");
        sb.append("- 面试时长：").append(ctx.getDurationMinutes()).append("分钟\n");
        sb.append("- 实际答题数：").append(ctx.getAnswerCount()).append("题，");
        sb.append("追问").append(ctx.getFollowupCount()).append("次\n");
        sb.append("\n");

        if (ctx.getPreferencesText() != null && !ctx.getPreferencesText().isBlank()) {
            sb.append("## 用户训练目标\n");
            sb.append(ctx.getPreferencesText()).append("\n\n");
        }

        sb.append("## 量化参考数据\n");
        sb.append("- 总回答字数：").append(ctx.getTotalLength());
        sb.append("，平均每题").append(Math.round(ctx.getAvgLength())).append("字\n");
        sb.append("- 命中技术关键词：").append(ctx.getTechHits()).append("次\n");
        sb.append("- 命中项目信号词：").append(ctx.getProjectHits()).append("次\n");
        sb.append("- 使用逻辑连接词：").append(ctx.getLogicHits()).append("次\n");
        sb.append("- 含糊表述次数：").append(ctx.getVagueHits()).append("次\n");
        sb.append("- 过短回答(<15字)：").append(ctx.getShortAnswers()).append("次\n");
        sb.append("- 追问有效回应率：").append(ctx.getFollowupRatio()).append("\n");
        sb.append("\n");

        sb.append("## 面试问答记录\n");
        sb.append(safe(ctx.getQaTranscript()));

        return sb.toString();
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    // ============================ 上下文 ============================

    @Data
    @Builder
    public static class ScoringContext {
        private String jobName;
        private String jobKeywords;
        private int durationMinutes;
        private int answerCount;
        private int followupCount;
        /** 用户训练目标文字描述 */
        private String preferencesText;

        // 量化信号
        private int totalLength;
        private double avgLength;
        private int techHits;
        private int projectHits;
        private int logicHits;
        private int vagueHits;
        private int shortAnswers;
        private String followupRatio;

        /** 全部Q&A文本（按轮次格式化） */
        private String qaTranscript;
    }
}
