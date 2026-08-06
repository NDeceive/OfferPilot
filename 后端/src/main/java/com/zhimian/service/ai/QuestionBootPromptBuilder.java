package com.zhimian.service.ai;

import org.springframework.stereotype.Component;

/**
 * 题库引导 Prompt 构建器（缺题自动生成）。
 * 根据技能标签 + 岗位方向，让 DeepSeek 生成面试题 + 参考答案。
 */
@Component
public class QuestionBootPromptBuilder {

    public String systemPrompt() {
        return "你是一位资深技术面试官和题库建设专家。你需要为指定的技能标签编写面试题目。\n"
                + "要求：\n"
                + "1. 题目应考察该技能的核心知识点或常见面试重点。\n"
                + "2. 题目本身清晰、可独立理解，不需要额外上下文。\n"
                + "3. 参考答案应覆盖关键要点，2-5 句话即可。\n"
                + "4. 难度分 3 档：1=入门级基础概念，2=中等深度原理与实践（默认），3=深入底层/架构/调优。\n"
                + "   - 为每个标签生成 1 道题，如果题目明显偏浅或偏深请说明原因。\n"
                + "5. abilityTag 取该技能标签本身（或最相近的能力标签名）。\n"
                + "\n"
                + "严格按以下 JSON 格式输出（不要加 markdown 代码块标记）：\n"
                + "{\"questions\":[{\"content\":\"题目内容\",\"referenceAnswer\":\"参考答案\",\"difficulty\":2,\"abilityTag\":\"标签名\"}]}";
    }

    public String userPrompt(String tagName, String family, int difficulty) {
        String diffLabel = switch (difficulty) {
            case 1 -> "入门";
            case 3 -> "困难";
            default -> "中等";
        };
        return "【技能标签】" + safe(tagName) + "\n"
                + "【岗位方向】" + safe(family) + "\n"
                + "【目标难度】" + diffLabel + "\n"
                + "请生成 1 道面试题并输出 JSON：";
    }

    private String safe(String s) {
        return s == null || s.isBlank() ? "通用" : s.trim();
    }
}
