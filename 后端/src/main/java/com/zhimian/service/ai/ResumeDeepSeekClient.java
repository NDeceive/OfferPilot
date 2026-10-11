package com.zhimian.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.config.AiProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Dedicated DeepSeek configuration for resume AI; does not alter the interview LLM. */
@Component
public class ResumeDeepSeekClient {
    private final DeepSeekClient delegate;
    private final String model;

    public ResumeDeepSeekClient(ObjectMapper json,
            @Value("${resume-ai.deepseek.api-key:}") String apiKey,
            @Value("${resume-ai.deepseek.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${resume-ai.deepseek.model:deepseek-flash}") String model,
            @Value("${resume-ai.deepseek.enabled:true}") boolean enabled) {
        this.model = model;
        AiProperties properties = new AiProperties();
        properties.setProvider("deepseek");
        properties.setBaseUrl(baseUrl);
        properties.setApiKey(apiKey);
        properties.setModel(model);
        properties.setEnabled(enabled);
        properties.setTimeoutMs(90000);
        this.delegate = new DeepSeekClient(properties, json);
    }

    public String modelName() { return model; }

    public boolean isUsable() { return delegate.isUsable(); }

    public DeepSeekClient.JsonCallResult chatJsonDetailed(String systemPrompt, String userPrompt) {
        return delegate.chatJsonDetailed(systemPrompt, userPrompt);
    }
}
