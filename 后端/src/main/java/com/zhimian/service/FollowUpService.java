package com.zhimian.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhimian.config.AiProperties;
import com.zhimian.dto.FollowUpRequest;
import com.zhimian.dto.FollowUpResponse;
import com.zhimian.service.ai.DeepSeekClient;
import com.zhimian.service.ai.FollowUpPromptBuilder;
import com.zhimian.service.followup.FollowupDimension;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 动态追问生成 V3：决策（问不问）已经交给 FollowupGate，这里只负责「在指定方向上把问题写好」。
 * <p>
 * Flow:
 * <ol>
 *   <li>回答短于 15 字：规则兜底，不调用大模型</li>
 *   <li>AI 不可用：按方向的规则模板兜底</li>
 *   <li>AI 可用：带上层号、方向、此前问答链生成追问</li>
 *   <li>调用失败或返回空：规则兜底</li>
 * </ol>
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
        int layer = req.getLayer() == null || req.getLayer() < 1 ? 1 : req.getLayer();
        FollowupDimension planned = FollowupDimension.fromCode(req.getDimension());

        // 回答过短：规则兜底，不浪费一次模型调用
        if (answer.length() < RuleBasedFollowUpGenerator.MIN_ANSWER_LENGTH) {
            FollowUpResponse resp = ruleGenerator.tooShortResponse();
            resp.setDimension(planned == null ? FollowupDimension.CLARIFY.name() : planned.name());
            return resp;
        }

        if (!aiProps.isUsable()) {
            log.debug("AI not usable, using rule-based fallback");
            return ruleGenerator.responseFor(planned, layer);
        }

        JsonNode aiResult = deepSeekClient.chatJson(
                promptBuilder.systemPrompt(layer, planned, req.getAskedDimensions()),
                promptBuilder.userPrompt(req.getPosition(), req.getQuestion(),
                        req.getReferenceAnswer(), req.getChainText(), req.getAnswer()));

        if (aiResult != null) {
            try {
                String aiQuestion = aiResult.path("followUpQuestion").asText("");
                String reason = aiResult.path("reason").asText("AI judgement");

                if (aiQuestion != null && !aiQuestion.isBlank()) {
                    FollowupDimension returned = FollowupDimension.fromCode(
                            aiResult.path("dimension").asText(null));
                    FollowupDimension used = returned != null ? returned : planned;

                    FollowUpResponse resp = FollowUpResponse.of(cleanup(aiQuestion), SOURCE_AI, reason);
                    resp.setDimension(used == null ? FollowupDimension.CLARIFY.name() : used.name());
                    resp.setErrorDetected(aiResult.path("errorDetected").asBoolean(false));
                    JsonNode uncovered = aiResult.path("uncoveredPoints");
                    resp.setUncoveredCount(uncovered.isArray() ? uncovered.size() : -1);
                    log.info("[AI-FollowUp] layer={}, dimension={}, reason={}", layer, resp.getDimension(), reason);
                    return resp;
                }
                log.warn("DeepSeek returned no follow-up question, reason={}", reason);
            } catch (Exception e) {
                log.warn("Failed to parse DeepSeek follow-up JSON, fallback to rule: {}", e.getMessage());
            }
        }

        log.debug("DeepSeek did not return a usable follow-up question, using rule-based fallback");
        return ruleGenerator.responseFor(planned, layer);
    }

    /** Clean model output: strip extra newlines and surrounding quotes. */
    private String cleanup(String text) {
        String t = text.trim();
        int nl = t.indexOf('\n');
        if (nl > 0) {
            t = t.substring(0, nl).trim();
        }
        if (t.length() >= 2
                && ((t.startsWith("\"") && t.endsWith("\""))
                    || (t.startsWith("'") && t.endsWith("'")))) {
            t = t.substring(1, t.length() - 1).trim();
        }
        return t;
    }
}
