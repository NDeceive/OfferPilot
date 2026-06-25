package com.zhimian.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek 客户端：封装对 OpenAI 兼容 /chat/completions 接口的调用。
 * <p>
 * 任何异常（网络 / 超时 / 鉴权失败 / 解析失败 / 空内容）一律返回 null，
 * 由上层决定是否走规则兜底；客户端自身不抛业务异常，保证主流程稳定。
 */
@Slf4j
@Component
public class DeepSeekClient {

    private final AiProperties props;
    private final ObjectMapper objectMapper;

    public DeepSeekClient(AiProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用 DeepSeek 生成文本。
     *
     * @return 模型返回的纯文本内容；调用失败或内容为空时返回 null。
     */
    public String chat(String systemPrompt, String userPrompt) {
        try {
            // 组织 OpenAI 兼容的请求体
            Map<String, Object> body = Map.of(
                    "model", props.getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", 0.7,
                    "stream", false
            );

            // 用 Bearer Token 鉴权，API Key 来自配置/环境变量
            String raw = restClient().post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return extractContent(raw);
        } catch (Exception e) {
            // 失败只记日志并返回 null，触发上层规则兜底
            log.warn("DeepSeek 调用失败，将使用规则兜底：{}", e.getMessage());
            return null;
        }
    }

    /** 解析 choices[0].message.content，缺失或空白返回 null */
    private String extractContent(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                return null;
            }
            String text = content.asText().trim();
            return text.isEmpty() ? null : text;
        } catch (Exception e) {
            log.warn("DeepSeek 响应解析失败：{}", e.getMessage());
            return null;
        }
    }

    /** 按配置超时构建 RestClient（轻量，按需创建即可） */
    private RestClient restClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getTimeoutMs());
        factory.setReadTimeout(props.getTimeoutMs());
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
