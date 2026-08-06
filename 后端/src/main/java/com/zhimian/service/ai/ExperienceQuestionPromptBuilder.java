package com.zhimian.service.ai;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 体验式题目 Prompt 构建器。
 * 根据简历画像和岗位，让 DeepSeek 生成一个情景化面试题 + 参考答案。
 */
@Component
public class ExperienceQuestionPromptBuilder {

    public String systemPrompt() {
        return "你是一名资深技术面试官。你的任务是根据指定的「焦点技能」设计一个情景化面试题。\n"
                + "\n"
                + "出题规则：\n"
                + "1. 你必须围绕「本期焦点技能」来出题，这是系统已经为你选好的考察点，不要自作主张换成别的技能。\n"
                + "2. 仔细阅读候选人的技能列表和项目经历，了解他可能接触过哪些技术场景，但不要假定他一定用过。\n"
                + "3. 用「假设」「假如」「如果」等假设语气来构建场景。题目应有深度，考察实际工程能力。\n"
                + "4. 场景描述控制在 2-3 句话以内，简洁明了。不要铺垫过多背景细节，直接抛出核心问题。\n"
                + "5. ★ 一次只问一个问题！不要在同一道题目里拆成多个小问（如「请描述...并说明...同时分析...」）。\n"
                + "   追问是面试官的事，你只需要给出一个聚焦的、可以深入展开的好问题即可。\n"
                + "6. ⚠️ 严禁编造经历：\n"
                + "   - 不能说「你提到过...」「你在项目中用了...」「你的XX系统...」\n"
                + "   - 候选人简历里有某技能 ≠ 他真的在项目里用过。一律用假设语气提问。\n"
                + "7. 同时生成参考答案（简练要点，2-4句话）。\n"
                + "8. 确定能力标签（如：系统设计、问题排查、性能优化、团队协作等）。\n"
                + "\n"
                + "严格按 JSON 输出（不要加 markdown 代码块）：\n"
                + "{\"question\": \"题目内容\", \"referenceAnswer\": \"参考答案\", \"abilityTag\": \"能力标签\"}";
    }

    /**
     * 生成体验题（带上焦点技能 + 已问过的题目）
     * @param focusSkill 代码层随机选定的焦点技能，AI 必须围绕此技能出题
     */
    public String userPrompt(String position, String skills, String keywords, String projects,
                             String focusSkill, List<String> askedQuestions) {
        StringBuilder sb = new StringBuilder();
        sb.append("【目标岗位】").append(safe(position)).append("\n");
        sb.append("【候选人技能】").append(safe(skills)).append("\n");
        sb.append("【候选人关键词】").append(safe(keywords)).append("\n");
        sb.append("【候选人项目经历】").append(safe(projects)).append("\n");
        sb.append("【本期焦点技能（你必须围绕这个技能来出题，不要选别的）】").append(safe(focusSkill)).append("\n");

        if (askedQuestions != null && !askedQuestions.isEmpty()) {
            sb.append("\n【本次面试已问过的题目（避免出类似的题）】\n");
            for (int i = 0; i < askedQuestions.size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(askedQuestions.get(i)).append("\n");
            }
        }

        sb.append("\n请围绕「焦点技能」生成一个情景化面试题目并输出 JSON：");
        return sb.toString();
    }

    private String safe(String s) {
        return s == null || s.isBlank() ? "未提供" : s.trim();
    }
}
