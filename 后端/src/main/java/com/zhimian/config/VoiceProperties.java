package com.zhimian.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 语音分析配置，绑定 interview.voice.* */
@Data
@Component
@ConfigurationProperties(prefix = "interview.voice")
public class VoiceProperties {
    private boolean enabled = true;
    private String serviceUrl = "http://127.0.0.1:8020";
    private int timeoutMs = 20000;
}
