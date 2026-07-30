package com.zhimian.service.ai;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * 评分 Prompt 构建器：将面试 Q&A + 量化信号 + 用户训练目标组织成 DeepSeek 提示词。
 */
@Component
public class ScorePromptBuilder {

    /** 系统提示词：资深面试官角色 + 10维定义 + 输出格式 */
    public String systemPrompt() {
        return "你是一位资深技术面试官，具备10年以上一线研发和面试评估经验。\n"
                + "你的任务是根据候选人的面试问答记录，在10个评价维度上给出客观评分。\n"
                + "\n"
                + "## 评分原则\n"
                + "- 每个维度独立评分，不要因为一个维度差就压低其他维度\n"
                + "- 评分基于候选人实际回答内容，不要过度推测\n"
                + "- 参考给出的量化信号数据，但最终以回答质量为唯一依据\n"
                + "- 65分=基本合格，75分=良好水平，85分=优秀水平，100分=完美（极少使用）\n"
                + "- 如果某个维度在本次面试中缺乏足够信息（如无追问则追问应变不可评估），给出60-70的中性分并标记低置信度\n"
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
                + "要求：10个模块全部评分，每个模块必须有score/confidence/evidence/gap_analysis/suggestion五个字段。";
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
