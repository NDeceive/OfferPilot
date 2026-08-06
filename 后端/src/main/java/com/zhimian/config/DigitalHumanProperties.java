package com.zhimian.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数字人服务及本地自动启动配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "digital-human")
public class DigitalHumanProperties {

    private boolean enabled = true;
    private String baseUrl = "http://127.0.0.1:8010";
    private int timeoutMs = 20_000;
    private Launcher launcher = new Launcher();

    @Data
    public static class Launcher {
        private boolean enabled = true;
        private String workdir = "数字人";
        private String script = "start_offerpilot_digital_human.bat";
        private int startupWaitMs = 120_000;
        private boolean stopOnShutdown = true;
    }
}
