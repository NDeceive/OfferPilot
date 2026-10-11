package com.zhimian.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Keeps resume generation independent of interview follow-ups and Zhipu ASR. */
@Component
public class ResumeAiGateway {
    private final DeepSeekClient existingClient;
    private final ResumeDeepSeekClient resumeClient;
    private final boolean useResumeDeepSeek;

    public ResumeAiGateway(DeepSeekClient existingClient, ResumeDeepSeekClient resumeClient,
                           @Value("${resume-ai.deepseek.enabled:true}") boolean useResumeDeepSeek) {
        this.existingClient = existingClient;
        this.resumeClient = resumeClient;
        this.useResumeDeepSeek = useResumeDeepSeek;
    }

    public record Status(String provider, boolean configured, String model) {}

    public Status status() {
        return useResumeDeepSeek
                ? new Status("deepseek", resumeClient != null && resumeClient.isUsable(),
                        resumeClient == null ? null : resumeClient.modelName())
                : new Status("zhipu", existingClient.isUsable(), existingClient.modelName());
    }

    public boolean isUsable() {
        return useResumeDeepSeek ? resumeClient != null && resumeClient.isUsable() : existingClient.isUsable();
    }

    public boolean usesResumeDeepSeek() { return useResumeDeepSeek; }

    public DeepSeekClient.JsonCallResult chatJsonDetailed(String systemPrompt, String userPrompt) {
        return useResumeDeepSeek
                ? resumeClient == null
                    ? new DeepSeekClient.JsonCallResult(null, DeepSeekClient.JsonFailure.NOT_CONFIGURED)
                    : resumeClient.chatJsonDetailed(systemPrompt, userPrompt)
                : existingClient.chatJsonDetailed(systemPrompt, userPrompt);
    }

    public JsonNode chatJson(String systemPrompt, String userPrompt) {
        return useResumeDeepSeek ? chatJsonDetailed(systemPrompt, userPrompt).value()
                : existingClient.chatJson(systemPrompt, userPrompt);
    }
}
