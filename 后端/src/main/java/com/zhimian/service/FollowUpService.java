package com.zhimian.service;

import com.zhimian.config.AiProperties;
import com.zhimian.dto.FollowUpRequest;
import com.zhimian.dto.FollowUpResponse;
import com.zhimian.service.ai.DeepSeekClient;
import com.zhimian.service.ai.FollowUpPromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 动态追问服务：优先用 DeepSeek 生成贴合回答的追问，
 * 在「回答过短 / AI 未启用 / AI 调用失败或返回空」时回退到规则化兜底。
 * <p>
 * source = AI 表示 DeepSeek 生成；source = RULE 表示规则兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpService {

    private static final String SOURCE_AI = "AI";

    private final AiProperties aiProps;
    private final DeepSeekClient deepSeekClient;
    private final FollowUpPromptBuilder promptBuilder;
    private final RuleBasedFollowUpGenerator ruleGenerator;

    public FollowUpResponse generate(FollowUpRequest req) {
        String answer = req.getAnswer() == null ? "" : req.getAnswer().trim();
        boolean tooShort = answer.length() < RuleBasedFollowUpGenerator.MIN_ANSWER_LENGTH;

        // 规则 6：回答过短直接走规则兜底，不浪费一次 AI 调用
        if (tooShort) {
            return ruleGenerator.generate(req.getPosition(), req.getQuestion(), req.getAnswer(), true);
        }

        // AI 未启用或 key 未正确配置：直接规则兜底
        if (!aiProps.isUsable()) {
            log.debug("AI 未启用或未配置有效 key，使用规则兜底");
            return ruleGenerator.generate(req.getPosition(), req.getQuestion(), req.getAnswer(), false);
        }

        // 调用 DeepSeek：成功且非空 → source = AI
        String aiQuestion = deepSeekClient.chat(
                promptBuilder.systemPrompt(),
                promptBuilder.userPrompt(req.getPosition(), req.getQuestion(), req.getAnswer()));

        if (aiQuestion != null && !aiQuestion.isBlank()) {
            return FollowUpResponse.of(cleanup(aiQuestion), SOURCE_AI, "基于考生回答由 AI 生成的深入追问");
        }

        // 规则 7：AI 失败 / 超时 / 返回空内容 → 规则兜底
        log.debug("DeepSeek 未返回有效内容，使用规则兜底");
        return ruleGenerator.generate(req.getPosition(), req.getQuestion(), req.getAnswer(), false);
    }

    /** 清洗模型输出：去掉多余换行后的内容与成对引号，保证只剩一句追问 */
    private String cleanup(String text) {
        String t = text.trim();
        // 模型偶尔换行追加多余内容，仅取第一行有效内容
        int nl = t.indexOf('\n');
        if (nl > 0) {
            t = t.substring(0, nl).trim();
        }
        // 去除成对包裹的中英文引号
        if (t.length() >= 2
                && ((t.startsWith("\"") && t.endsWith("\""))
                || (t.startsWith("“") && t.endsWith("”")))) {
            t = t.substring(1, t.length() - 1).trim();
        }
        return t;
    }
}
