package com.zhimian.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Judge0 CE 的可选连接配置。默认禁用，避免未部署 Judge 时误发外部请求。 */
@Data
@Component
@ConfigurationProperties(prefix = "judge")
public class JudgeProperties {
    private boolean enabled = false;
    private String baseUrl = "";
    private String authToken = "";
    private int timeoutMs = 15000;
    /** Judge0 的 language_id 由部署版本决定，安装后可通过 /languages 校验。 */
    private Map<String, Integer> languageIds = new LinkedHashMap<>(Map.of("java", 62));

    public boolean isUsable() {
        return enabled && baseUrl != null && !baseUrl.isBlank();
    }
}
