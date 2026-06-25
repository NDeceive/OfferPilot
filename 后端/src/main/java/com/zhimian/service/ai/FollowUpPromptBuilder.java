package com.zhimian.service.ai;

import org.springframework.stereotype.Component;

/**
 * 追问 Prompt 构建器：把岗位 / 原始问题 / 回答组织成 DeepSeek 的系统与用户提示词。
 * 单独成类，便于后续调优措辞而不影响调用方逻辑。
 */
@Component
public class FollowUpPromptBuilder {

    /** 系统提示词：设定「面向大学生的模拟面试官」角色与输出约束 */
    public String systemPrompt() {
        return "你是一名面向中国高校大学生的模拟面试官。"
                + "请根据给定的「岗位」「原始问题」和「考生回答」，生成且仅生成一个追问问题。"
                + "要求：追问要具体、自然，紧扣考生回答中的薄弱点或值得深入之处，"
                + "语气友好专业，符合真实面试场景。"
                + "只输出这一个追问问题本身，不要输出任何解释、分析、前缀、编号或引号。";
    }

    /** 用户提示词：填入本轮上下文 */
    public String userPrompt(String position, String question, String answer) {
        return "岗位：" + safe(position) + "\n"
                + "原始问题：" + safe(question) + "\n"
                + "考生回答：" + safe(answer) + "\n"
                + "请给出一个追问问题：";
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
