package com.zhimian.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhimian.config.AiProperties;
import com.zhimian.entity.Resume;
import com.zhimian.service.ai.DeepSeekClient;
import com.zhimian.service.ai.ExperienceQuestionPromptBuilder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 体验式题目服务：调用 DeepSeek 根据简历画像 + 岗位生成情景化面试题。
 * AI 调用失败时回退到简单模板题目。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExperienceQuestionService {

    private final AiProperties aiProps;
    private final DeepSeekClient deepSeekClient;
    private final ExperienceQuestionPromptBuilder promptBuilder;

    /**
     * 生成一道体验式情景题 + 参考答案。
     *
     * @param position       目标岗位名
     * @param resume         候选人简历画像
     * @param askedQuestions 本场面试已问过的题目内容（用于避免重复）
     * @param jobTags        目标岗位的能力标签（abilities + keywords）
     * @return 题目结果；AI 不可用时回退模板
     */
    public ExperienceQuestionResult generate(String position, Resume resume,
                                             List<String> askedQuestions, List<String> jobTags) {
        // 从简历技能中随机选一个作为本题焦点，避免每次都选同一技能
        String focusSkill = pickFocusSkill(resume, askedQuestions, jobTags);
        log.info("[体验题] 焦点技能={}", focusSkill);

        if (aiProps.isUsable()) {
            JsonNode result = deepSeekClient.chatJson(
                    promptBuilder.systemPrompt(),
                    promptBuilder.userPrompt(
                            position,
                            resume != null ? resume.getSkills() : "",
                            resume != null ? resume.getKeywords() : "",
                            resume != null ? resume.getProjects() : "",
                            focusSkill,
                            askedQuestions));
            if (result != null) {
                try {
                    String question = result.path("question").asText("");
                    String refAnswer = result.path("referenceAnswer").asText("");
                    String abilityTag = result.path("abilityTag").asText("综合能力");
                    if (!question.isBlank()) {
                        log.info("[体验题] DeepSeek生成成功, abilityTag={}", abilityTag);
                        return new ExperienceQuestionResult(question, refAnswer, abilityTag);
                    }
                } catch (Exception e) {
                    log.warn("[体验题] JSON解析失败: {}", e.getMessage());
                }
            }
        }
        // 回退到模板题目
        log.info("[体验题] AI不可用，回退模板");
        return fallbackQuestion(position, resume, focusSkill, jobTags, askedQuestions);
    }

    /**
     * 选本题的焦点技能。**优先从「简历 ∩ 岗位」里选**。
     *
     * 只看简历会跑题：student 账号的简历是 Java 后端方向，于是 Python 后端面试被问
     * 「结合你在 Kafka、Java、Spring 方面的经验」、业务数据分析师面试被问
     * 「结合你在 Maven、Java、Spring 方面的经验」——题干里的技术栈全来自简历，
     * 岗位形同虚设。交集为空时才退回全量简历技能。
     */
    private String pickFocusSkill(Resume resume, List<String> askedQuestions, List<String> jobTags) {
        List<String> allSkills = resumeSkills(resume);
        if (allSkills.isEmpty()) return "综合能力";

        List<String> pool = relevantSkills(allSkills, jobTags);
        if (pool.isEmpty()) pool = allSkills;

        // 排除已问题目中明显涉及到的技能
        List<String> fresh = new java.util.ArrayList<>(pool);
        if (askedQuestions != null) {
            String askedText = String.join(" ", askedQuestions).toLowerCase();
            fresh.removeIf(skill -> askedText.contains(skill.toLowerCase()));
        }
        // 如果全部被覆盖了，就用候选池
        if (fresh.isEmpty()) fresh = pool;

        java.util.Collections.shuffle(fresh);
        return fresh.get(0);
    }

    /** 纯 ASCII 串（拉丁字母/数字/符号），决定要不要做词边界校验。 */
    private static final java.util.regex.Pattern ASCII_ONLY =
            java.util.regex.Pattern.compile("^[\\x00-\\x7f]+$");

    /**
     * 简历里写了、且与岗位沾边的技能。交集为空时返回空列表。
     *
     * ASCII 词走词边界：简历里的 "Java" 不能因为岗位写了 "JavaScript" 就被当成沾边
     * （反之亦然），否则前端面试会报出 Java。中文仍走子串，`性能` 才能命中 `性能优化`。
     */
    private List<String> relevantSkills(List<String> resumeSkills, List<String> jobTags) {
        if (jobTags == null || jobTags.isEmpty()) return java.util.Collections.emptyList();
        List<String> jobLower = jobTags.stream()
                .map(s -> s.toLowerCase().trim())
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toList());

        List<String> out = new java.util.ArrayList<>();
        for (String skill : resumeSkills) {
            String sl = skill.toLowerCase().trim();
            if (sl.isEmpty()) continue;
            for (String jt : jobLower) {
                // 双向包含：简历写 "Spring Boot"、岗位写 "Spring"，或反之，都算沾边
                if (containsToken(jt, sl) || containsToken(sl, jt)) {
                    out.add(skill);
                    break;
                }
            }
        }
        return out;
    }

    private static boolean containsToken(String haystack, String needle) {
        if (haystack.isEmpty() || needle.isEmpty() || !haystack.contains(needle)) return false;
        if (!ASCII_ONLY.matcher(needle).matches()) return true; // 中文走子串
        return java.util.regex.Pattern
                .compile("(?<![a-z0-9])" + java.util.regex.Pattern.quote(needle) + "(?![a-z0-9])")
                .matcher(haystack).find();
    }

    private List<String> resumeSkills(Resume resume) {
        List<String> all = new java.util.ArrayList<>();
        if (resume != null) {
            all.addAll(parseJsonList(resume.getSkills()));
            all.addAll(parseJsonList(resume.getKeywords()));
        }
        return all.stream().distinct().collect(java.util.stream.Collectors.toList());
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return java.util.Collections.emptyList();
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode arr = om.readTree(json);
            if (!arr.isArray()) return java.util.Collections.emptyList();
            List<String> list = new java.util.ArrayList<>();
            arr.forEach(n -> list.add(n.asText()));
            return list;
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    private ExperienceQuestionResult fallbackQuestion(String position, Resume resume,
                                                      String focusSkill, List<String> jobTags,
                                                      List<String> askedQuestions) {
        // 注意：resume.getSkills() 是 JSON 数组原文，直接拼进题干会变成
        // 「请结合你在["Java","Spring","Spring Boot"]方面…」，必须解析成语义文本
        List<String> skills = resumeSkills(resume);

        // 题干里只报「简历 ∩ 岗位」的技能。交集为空就退回岗位自己的能力项——
        // 宁可说「该岗位相关的技术栈」，也不要在一个产品经理面试里报出 Maven 和 Spring。
        List<String> usable = relevantSkills(skills, jobTags);
        boolean fromResume = !usable.isEmpty();
        if (usable.isEmpty() && jobTags != null) {
            usable = jobTags.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());
        }
        if (usable.isEmpty()) usable = skills;

        // 焦点技能只在它确实来自「简历 ∩ 岗位」时才排到最前。
        // 交集为空时 focusSkill 是从整份简历里随机抽的（PM 面到 JVM 就是这么来的），
        // 这种技能与岗位无关，绝不能因为「被抽中」就显示在题干里。
        if (fromResume && focusSkill != null && !focusSkill.isBlank()) {
            usable.remove(focusSkill);
            usable.add(0, focusSkill); // 本题焦点技能排最前
        }

        // 技能太少时补上岗位自己的能力项。实测 ALG-ML 的「简历 ∩ 岗位」只交出一个 SQL，
        // 题干会退化成「请结合你在『SQL』方面…」，而且单项列表再怎么轮换也只有一种排列。
        if (usable.size() < 4 && jobTags != null) {
            List<String> topped = new java.util.ArrayList<>(usable);
            for (String jt : jobTags) {
                if (jt != null && !jt.isBlank() && !topped.contains(jt)) {
                    topped.add(jt);
                }
            }
            usable = topped;
        }

        // 按「本场已经出过几道体验题」旋转展示窗口：第一道不转（焦点技能保持在最前），
        // 第二道起整体前移一位。不轮换的话，交集为空（顺序等于岗位画像原始顺序）或
        // 交集只有一项时，同一场面试的第二道体验题会和第一道逐字相同——实测 11 场次 100% 命中。
        usable = rotate(usable, countExperienceAsked(askedQuestions));

        String skillText = usable.isEmpty()
                ? "相关技术栈"
                : String.join("、", usable.subList(0, Math.min(4, usable.size())));

        String question = "请结合你在「" + skillText + "」方面的实际项目经验，"
                + "谈一谈在「" + position + "」岗位上，你曾经遇到过的最大技术挑战是什么？"
                + "你是如何分析、设计并最终解决它的？请详细说明你的思考过程和技术方案。";
        String refAnswer = "候选人应能描述：1) 具体的项目背景和挑战；2) 分析过程和技术选型；"
                + "3) 实施方案和关键决策；4) 最终成果和量化效果。";
        return new ExperienceQuestionResult(question, refAnswer, "综合能力");
    }

    /** 体验题题干的固定前缀；也用它数本场已经出过几道体验题。 */
    private static final String EXPERIENCE_PREFIX = "请结合你在";

    private int countExperienceAsked(List<String> askedQuestions) {
        if (askedQuestions == null) {
            return 0;
        }
        int n = 0;
        for (String q : askedQuestions) {
            if (q != null && q.startsWith(EXPERIENCE_PREFIX)) {
                n++;
            }
        }
        return n;
    }

    /** 把列表整体左移 offset 位（按长度取模）。返回新列表，不改动入参。 */
    private List<String> rotate(List<String> list, int offset) {
        int n = list.size();
        if (n <= 1) {
            return new java.util.ArrayList<>(list);
        }
        int k = ((offset % n) + n) % n;
        List<String> out = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(list.get((k + i) % n));
        }
        return out;
    }

    @Data
    public static class ExperienceQuestionResult {
        private final String question;
        private final String referenceAnswer;
        private final String abilityTag;
    }
}
