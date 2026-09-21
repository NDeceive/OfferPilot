package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.config.AiProperties;
import com.zhimian.dto.FollowUpRequest;
import com.zhimian.dto.FollowUpResponse;
import com.zhimian.service.ai.DeepSeekClient;
import com.zhimian.service.ai.FollowUpPromptBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FollowUpServiceTest {

    @Test
    void keepsGeneratedQuestionEvenWhenLegacyDecisionFlagIsFalse() throws Exception {
        AiProperties props = new AiProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");
        DeepSeekClient client = mock(DeepSeekClient.class);
        when(client.chatJson(anyString(), anyString())).thenReturn(new ObjectMapper().readTree("""
                {"shouldFollowUp":false,"followUpQuestion":"你提到缓存击穿，具体如何避免热点键同时回源？","reason":"验证方案细节"}
                """));
        FollowUpService service = new FollowUpService(props, client,
                new FollowUpPromptBuilder(), new RuleBasedFollowUpGenerator());

        FollowUpResponse response = service.generate(requestWithLongAnswer());

        assertNotNull(response);
        assertEquals("AI", response.getSource());
        assertEquals("你提到缓存击穿，具体如何避免热点键同时回源？", response.getFollowUpQuestion());
    }

    @Test
    void fallsBackWhenModelReturnsNoQuestion() throws Exception {
        AiProperties props = new AiProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");
        DeepSeekClient client = mock(DeepSeekClient.class);
        when(client.chatJson(anyString(), anyString())).thenReturn(new ObjectMapper().readTree("""
                {"shouldFollowUp":false,"followUpQuestion":"","reason":"回答完整"}
                """));
        FollowUpService service = new FollowUpService(props, client,
                new FollowUpPromptBuilder(), new RuleBasedFollowUpGenerator());

        FollowUpResponse response = service.generate(requestWithLongAnswer());

        assertNotNull(response);
        assertEquals("RULE", response.getSource());
    }

    private FollowUpRequest requestWithLongAnswer() {
        FollowUpRequest request = new FollowUpRequest();
        request.setPosition("Java 后端工程师");
        request.setQuestion("如何处理缓存击穿？");
        request.setReferenceAnswer("互斥锁、逻辑过期与热点预热");
        request.setAnswer("我使用互斥锁限制同一时刻只有一个请求回源，其他请求短暂等待后重试。");
        return request;
    }
}
