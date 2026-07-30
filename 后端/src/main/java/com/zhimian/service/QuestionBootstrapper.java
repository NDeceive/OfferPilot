package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.zhimian.config.AiProperties;
import com.zhimian.entity.SkillQuestion;
import com.zhimian.entity.SkillQuestionTagRel;
import com.zhimian.entity.SkillTag;
import com.zhimian.mapper.SkillQuestionMapper;
import com.zhimian.mapper.SkillQuestionTagRelMapper;
import com.zhimian.mapper.SkillTagMapper;
import com.zhimian.service.ai.DeepSeekClient;
import com.zhimian.service.ai.QuestionBootPromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 题库引导器：确保每个标签都有题目可用。
 * <p>
 * 在面试启动前调用，对缺题的技能标签通过 DeepSeek 自动生成题目并入库。
 * 生成失败（AI 不可用 / 超时 / 解析失败）不阻塞主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionBootstrapper {

    private final AiProperties aiProps;
    private final DeepSeekClient deepSeekClient;
    private final QuestionBootPromptBuilder promptBuilder;
    private final SkillTagMapper tagMapper;
    private final SkillQuestionMapper questionMapper;
    private final SkillQuestionTagRelMapper relMapper;

    /**
     * 确保每个标签都至少有 1 道题目。缺失标签会先创建，缺题标签会调 AI 生成。
     *
     * @param tagNames  标签名列表（来自岗位 + 简历，已去重）
     * @param family    岗位族中文名（如"后端开发"），用于 Prompt 上下文
     * @param difficulty 面试难度等级
     */
    public void ensure(List<String> tagNames, String family, int difficulty) {
        if (tagNames.isEmpty()) return;

        // 查出所有已有标签
        List<SkillTag> existingTags = tagMapper.selectList(new LambdaQueryWrapper<>());
        var nameToTag = existingTags.stream()
                .collect(Collectors.toMap(t -> t.getName().toLowerCase().trim(), t -> t, (a, b) -> a));

        for (String rawName : tagNames) {
            String name = rawName.trim();
            if (name.isEmpty()) continue;

            try {
                SkillTag tag = resolveOrCreateTag(name, nameToTag);
                if (tag == null) continue;

                // 检查该标签是否已有题目
                long count = relMapper.selectCount(
                        new LambdaQueryWrapper<SkillQuestionTagRel>()
                                .eq(SkillQuestionTagRel::getTagId, tag.getId()));
                if (count > 0) {
                    continue; // 已有题目，跳过
                }

                // AI 不可用 → 跳过
                if (!aiProps.isUsable()) {
                    log.info("[题库引导] AI 不可用，跳过标签: {}", name);
                    continue;
                }

                // 调 AI 生成题目
                JsonNode result = deepSeekClient.chatJson(
                        promptBuilder.systemPrompt(),
                        promptBuilder.userPrompt(name, family, difficulty));

                if (result == null) {
                    log.warn("[题库引导] AI 返回为空，跳过标签: {}", name);
                    continue;
                }

                JsonNode questions = result.path("questions");
                if (!questions.isArray() || questions.isEmpty()) {
                    log.warn("[题库引导] questions 数组为空，跳过标签: {}", name);
                    continue;
                }

                for (JsonNode qNode : questions) {
                    String content = qNode.path("content").asText("");
                    String refAnswer = qNode.path("referenceAnswer").asText("");
                    int diff = qNode.path("difficulty").asInt(difficulty);
                    String abilityTag = qNode.path("abilityTag").asText(name);

                    if (content.isBlank()) continue;

                    SkillQuestion sq = new SkillQuestion();
                    sq.setContent(content.trim());
                    sq.setReferenceAnswer(refAnswer.isBlank() ? "待补充" : refAnswer.trim());
                    sq.setDifficulty(Math.max(1, Math.min(3, diff)));
                    questionMapper.insert(sq);

                    SkillQuestionTagRel rel = new SkillQuestionTagRel();
                    rel.setQuestionId(sq.getId());
                    rel.setTagId(tag.getId());
                    relMapper.insert(rel);

                    log.info("[题库引导] 已生成题目 tag={}, questionId={}, content={}",
                            name, sq.getId(), content.substring(0, Math.min(40, content.length())));
                }
            } catch (Exception e) {
                log.warn("[题库引导] 生成失败，跳过标签: {}, err={}", name, e.getMessage());
            }
        }
    }

    /**
     * 查找已有标签或创建新标签。
     */
    private SkillTag resolveOrCreateTag(String name, java.util.Map<String, SkillTag> nameToTag) {
        String key = name.toLowerCase().trim();
        SkillTag existing = nameToTag.get(key);
        if (existing != null) return existing;

        try {
            SkillTag tag = new SkillTag();
            tag.setName(name);
            tag.setCategory("自动生成");
            tag.setDescription("由 AI 根据岗位标签自动创建");
            tag.setSortOrder(999);
            tagMapper.insert(tag);
            // 更新缓存，避免同一批次重复创建
            nameToTag.put(key, tag);
            log.info("[题库引导] 创建新标签: {}", name);
            return tag;
        } catch (Exception e) {
            log.warn("[题库引导] 创建标签失败: {}, err={}", name, e.getMessage());
            return null;
        }
    }
}
