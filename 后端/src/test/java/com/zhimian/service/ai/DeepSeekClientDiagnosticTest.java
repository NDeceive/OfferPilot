package com.zhimian.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DeepSeekClientDiagnosticTest {
    private final DeepSeekClient client = new DeepSeekClient(null, new ObjectMapper());

    @Test void extractsNestedProviderCodeAndClassifiesRateLimit() {
        var result = client.providerDiagnostic("{\"error\":{\"code\":\"1302\",\"message\":\"请求频率限制\"}}");
        assertEquals("1302", result.code());
        assertEquals("RATE_LIMIT", result.reasonClass());
    }

    @Test void supportsTopLevelCodeAndOtherKnownReasons() {
        var concurrency = client.providerDiagnostic("{\"code\":\"429_CONCURRENT\",\"message\":\"并发上限\"}");
        assertEquals("429_CONCURRENT", concurrency.code());
        assertEquals("CONCURRENCY", concurrency.reasonClass());
        assertEquals("QUOTA", client.providerDiagnostic("{\"error\":{\"message\":\"quota exceeded\"}}").reasonClass());
    }

    @Test void rejectsUntrustedProviderContentAndMalformedJson() {
        var result = client.providerDiagnostic("{\"error\":{\"code\":\"secret key: abc\",\"message\":\"prompt and resume content\"}}");
        assertEquals("未提供", result.code());
        assertEquals("UNKNOWN", result.reasonClass());
        assertFalse(result.toString().contains("secret"));
        assertFalse(result.toString().contains("resume"));
        assertEquals("UNKNOWN", client.providerDiagnostic("not-json").reasonClass());
    }
}
