package com.zhimian.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 大模型配置，绑定 application.yml 的 ai.* 节点（DeepSeek，OpenAI 兼容接口）。
 * <p>
 * API Key 通过 ${AI_API_KEY:...} 占位符从环境变量或配置读取，不在代码中硬编码。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** 提供方标识，目前为 deepseek */
    private String provider = "deepseek";

    /** 接口基地址（OpenAI 兼容） */
    private String baseUrl = "https://api.deepseek.com";

    /** API Key：来自环境变量 AI_API_KEY 或配置文件，禁止硬编码 */
    private String apiKey;

    /** 模型名称 */
    private String model = "deepseek-chat";

    /** 是否启用真实 AI 调用；false 时直接走规则化兜底（无 key 也能演示） */
    private boolean enabled = false;

    /** 调用超时（毫秒），同时作为连接与读取超时 */
    private int timeoutMs = 20000;

    /**
     * AI 是否真正可用：开关已打开，且 key 已正确配置（非空且不是占位符）。
     * 避免把 application.yml 中的默认占位符误当成真实 key 去调用。
     */
    public boolean isUsable() {
        if (!enabled) {
            return false;
        }
        if (apiKey == null || apiKey.isBlank()) {
            return false;
        }
        return !apiKey.contains("在这里填") && !apiKey.toLowerCase().contains("your");
    }
}
