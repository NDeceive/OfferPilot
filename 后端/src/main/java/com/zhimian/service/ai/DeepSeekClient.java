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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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

    /**
     * 供上层判断是否值得发起调用，避免上层直接依赖 AiProperties。
     * 与 {@link #chat} 配合使用：false 时不必往返一次注定失败的请求。
     */
    public boolean isUsable() {
        return props.isUsable();
    }

    /**
     * 流式调用 DeepSeek，每收到一段增量就回调 onDelta。
     * <p>
     * 与 {@link #chat} 保持同一契约：异常一律不外抛，失败返回 false 由上层降级。
     * 但有一个例外——客户端主动断开导致的中断，用 {@code shouldStop} 返回 true 表达，
     * 此时同样返回 false，上层据此静默收尾（不要当成 AI 故障去打印告警或重试）。
     *
     * @param shouldStop 每读一行前询问一次；返回 true 表示调用方已不需要结果，立即停止读取。
     *                   用来在浏览器断开 SseEmitter 后尽快停止消耗 DeepSeek 配额。
     * @return true 表示流正常结束且至少产出了一段文本
     */
    public boolean chatStream(String systemPrompt, String userPrompt,
                              BooleanSupplier shouldStop, Consumer<String> onDelta) {
        if (!props.isUsable()) {
            return false;
        }
        Map<String, Object> body = Map.of(
                "model", props.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.8,
                // 话术本来就要求很短，这里再兜一道，防止模型絮叨拖长首字延迟
                "max_tokens", 200,
                "stream", true
        );

        try {
            Boolean ok = streamRestClient().post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .body(body)
                    // 必须用 exchange：retrieve() 会把整个响应体缓冲下来，流式就没了。
                    // 且响应必须在 lambda 内读完——exchange 返回后 body 会被关闭。
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            log.warn("DeepSeek 流式返回 {}，将降级", response.getStatusCode());
                            return Boolean.FALSE;
                        }
                        boolean produced = false;
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (shouldStop != null && shouldStop.getAsBoolean()) {
                                    return Boolean.FALSE;
                                }
                                // 只认 data: 行，天然跳过空行分隔符、": keep-alive" 注释和 event: 行
                                if (!line.startsWith("data:")) {
                                    continue;
                                }
                                String payload = line.substring(5).trim();
                                if (payload.isEmpty() || "[DONE]".equals(payload)) {
                                    continue;
                                }
                                String delta = extractDelta(payload);
                                if (delta != null && !delta.isEmpty()) {
                                    produced = true;
                                    onDelta.accept(delta);
                                }
                            }
                        }
                        return produced;
                    });
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.warn("DeepSeek 流式调用失败，将降级：{}", e.getMessage());
            return false;
        }
    }

    /** 解析流式分片里的 choices[0].delta.content；首帧 role 帧与结束帧没有该字段，返回 null */
    private String extractDelta(String json) {
        try {
            JsonNode content = objectMapper.readTree(json)
                    .path("choices").path(0).path("delta").path("content");
            return content.isMissingNode() || content.isNull() ? null : content.asText();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 调用 DeepSeek 并期望返回 JSON 对象。
     * 解析 choices[0].message.content 中的 JSON，失败返回 null。
     *
     * @return 解析后的 JsonNode；调用失败或返回非 JSON 时返回 null。
     */
    public JsonNode chatJson(String systemPrompt, String userPrompt) {
        String raw = chat(systemPrompt, userPrompt);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            // 模型可能在 JSON 外层包裹 markdown 代码块，先尝试剥离
            String json = raw.trim();
            if (json.startsWith("```")) {
                int start = json.indexOf('\n');
                int end = json.lastIndexOf("```");
                if (start >= 0 && end > start) {
                    json = json.substring(start, end).trim();
                }
            }
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("DeepSeek 返回内容无法解析为 JSON：{}", raw);
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
        return restClient(props.getTimeoutMs());
    }

    /**
     * 流式专用：读取超时放宽到 streamTimeoutMs。
     * 这里的 readTimeout 是「两次 read 的间隔」而非整段时长，所以一次回头很长的回复
     * 只要中间没有长时间静默就不会被截断。
     */
    private RestClient streamRestClient() {
        return restClient(props.getStreamTimeoutMs());
    }

    private RestClient restClient(int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getTimeoutMs());
        factory.setReadTimeout(readTimeoutMs);
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
