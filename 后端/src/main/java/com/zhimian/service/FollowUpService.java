package com.zhimian.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhimian.config.AiProperties;
import com.zhimian.dto.FollowUpRequest;
import com.zhimian.dto.FollowUpResponse;
import com.zhimian.service.ai.DeepSeekClient;
import com.zhimian.service.ai.FollowUpPromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Dynamic follow-up service V2: DeepSeek handles both the "whether to follow up" decision
 * and the "follow-up question text" generation.
 * <p>
 * Flow:
 * <ol>
 *   <li>Answer shorter than 15 chars: rule-based fallback (no AI call)</li>
 *   <li>AI not usable: rule-based fallback</li>
 *   <li>AI usable: send original question + reference answer + user answer, let AI decide</li>
 *   <li>AI returns a focused follow-up question for every interview round</li>
 *   <li>AI call fails: rule-based fallback</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpService {

    private static final String SOURCE_AI = "AI";
    private static final String SOURCE_RULE = "RULE";

    private final AiProperties aiProps;
    private final DeepSeekClient deepSeekClient;
    private final FollowUpPromptBuilder promptBuilder;
    private final RuleBasedFollowUpGenerator ruleGenerator;

    /**
     * Generate one focused follow-up question for the current interview round.
     *
     * @param req follow-up request (position / question / answer / referenceAnswer)
     * @return AI follow-up response, or a rule-based fallback when AI is unavailable
     */
    public FollowUpResponse generate(FollowUpRequest req) {
        String answer = req.getAnswer() == null ? "" : req.getAnswer().trim();
        boolean tooShort = answer.length() < RuleBasedFollowUpGenerator.MIN_ANSWER_LENGTH;

        // Answer too short: rule-based fallback, don't waste an AI call
        if (tooShort) {
            return ruleGenerator.tooShortResponse();
        }

        // AI not enabled or key not configured: rule-based fallback
        if (!aiProps.isUsable()) {
            log.debug("AI not usable, using rule-based fallback");
            return ruleGenerator.defaultResponse();
        }

        // Call DeepSeek: original question + reference answer + user answer
        JsonNode aiResult = deepSeekClient.chatJson(
                promptBuilder.systemPrompt(),
                promptBuilder.userPrompt(
                        req.getPosition(),
                        req.getQuestion(),
                        req.getReferenceAnswer(),
                        req.getAnswer()));

        if (aiResult != null) {
            try {
                String aiQuestion = aiResult.path("followUpQuestion").asText("");
                String reason = aiResult.path("reason").asText("AI judgement");

                // Follow-up is a core part of every interview round. Keep compatibility
                // with older model responses that incorrectly set shouldFollowUp=false.
                if (aiQuestion != null && !aiQuestion.isBlank()) {
                    log.info("[AI-FollowUp] generated, reason={}", reason);
                    return FollowUpResponse.of(cleanup(aiQuestion), SOURCE_AI, reason);
                }
            } catch (Exception e) {
                log.warn("Failed to parse DeepSeek follow-up JSON, fallback to rule: {}", e.getMessage());
            }
        }

        // AI failed / timed out / returned empty: rule-based fallback
        log.debug("DeepSeek did not return valid JSON, using rule-based fallback");
        return ruleGenerator.defaultResponse();
    }

    /** Clean model output: strip extra newlines and surrounding quotes. */
    private String cleanup(String text) {
        String t = text.trim();
        // Model sometimes appends extra content after newlines; only take the first line
        int nl = t.indexOf('\n');
        if (nl > 0) {
            t = t.substring(0, nl).trim();
        }
        // Remove surrounding ASCII double or single quotes
        if (t.length() >= 2
                && ((t.startsWith("\"") && t.endsWith("\""))
                    || (t.startsWith("'") && t.endsWith("'")))) {
            t = t.substring(1, t.length() - 1).trim();
        }
        return t;
    }
}
