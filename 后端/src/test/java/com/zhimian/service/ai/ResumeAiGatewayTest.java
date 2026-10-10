package com.zhimian.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResumeAiGatewayTest {
    @Test void resumeDeepSeekRequiresItsOwnKeyAndDoesNotBorrowInterviewKey() {
        var resume = new ResumeDeepSeekClient(new ObjectMapper(), "", "https://api.deepseek.com",
                "deepseek-flash", true);
        DeepSeekClient interview = mock(DeepSeekClient.class);
        when(interview.isUsable()).thenReturn(true);
        var gateway = new ResumeAiGateway(interview, resume, true);

        assertFalse(gateway.isUsable());
        assertEquals("deepseek", gateway.status().provider());
        assertFalse(gateway.status().configured());
        assertEquals(DeepSeekClient.JsonFailure.NOT_CONFIGURED,
                gateway.chatJsonDetailed("仅返回 JSON", "测试").failure());
        verify(interview, never()).chatJsonDetailed(anyString(), anyString());
    }

    @Test void explicitRollbackUsesExistingInterviewClient() {
        DeepSeekClient interview = mock(DeepSeekClient.class);
        when(interview.isUsable()).thenReturn(true);
        var gateway = new ResumeAiGateway(interview, null, false);

        assertTrue(gateway.isUsable());
        assertEquals("zhipu", gateway.status().provider());
    }
}
