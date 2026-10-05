package com.zhimian.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.BizException;
import com.zhimian.config.AsrProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZhipuAsrClientTest {

    @Test
    void missingKeyExplainsHowToConfigureWithoutCallingProvider() {
        AsrProperties properties = new AsrProperties();
        ZhipuAsrClient client = new ZhipuAsrClient(properties, new ObjectMapper());

        BizException error = assertThrows(BizException.class,
                () -> client.transcribe(new byte[] {1}, "answer.wav", 1.0));

        assertTrue(error.getMessage().contains("一键启动网页.bat"));
        assertTrue(error.getMessage().contains("README"));
    }
}
