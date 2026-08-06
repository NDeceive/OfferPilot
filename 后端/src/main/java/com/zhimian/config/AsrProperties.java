package com.zhimian.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 智谱语音识别配置。API Key 只允许通过环境变量注入。
 */
@Data
@Component
@ConfigurationProperties(prefix = "asr")
public class AsrProperties {

    private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";
    private String apiKey;
    private String model = "glm-asr-2512";
    private int timeoutMs = 60000;
    private long maxFileSize = 10 * 1024 * 1024;
    private double maxDurationSeconds = 30;

    public boolean isUsable() {
        return apiKey != null
                && !apiKey.isBlank()
                && !apiKey.toLowerCase().contains("your")
                && !apiKey.contains("在这里填");
    }
}
