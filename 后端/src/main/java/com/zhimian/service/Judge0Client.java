package com.zhimian.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.BizException;
import com.zhimian.config.JudgeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/** 对自部署 Judge0 CE 的最小封装；不使用公共判题服务，也不上传任何密钥。 */
@Component
@RequiredArgsConstructor
public class Judge0Client {
    private final JudgeProperties properties;
    private final ObjectMapper json;

    public record Result(String token, int statusId, String status, String stdout, String stderr,
                         String compileOutput, String message, String time, Integer memory) {}

    public Result judge(String language, String sourceCode, String stdin) {
        if (!properties.isUsable()) throw new BizException("在线判题服务尚未启用；请部署 Judge0 CE 后配置 JUDGE_ENABLED 与 JUDGE_BASE_URL");
        Integer languageId = properties.getLanguageIds().get(language == null ? "java" : language.toLowerCase());
        if (languageId == null) throw new BizException("该语言尚未配置 Judge0 language_id：" + language);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("language_id", languageId);
        payload.put("source_code", sourceCode);
        payload.put("stdin", stdin == null ? "" : stdin);
        payload.put("cpu_time_limit", 3);
        payload.put("memory_limit", 128000);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties.getAuthToken() != null && !properties.getAuthToken().isBlank()) headers.set("X-Auth-Token", properties.getAuthToken());
        try {
            String base = properties.getBaseUrl().replaceAll("/+$", "");
            ResponseEntity<String> response = rest().exchange(base + "/submissions?base64_encoded=false&wait=true", HttpMethod.POST,
                    new HttpEntity<>(json.writeValueAsString(payload), headers), String.class);
            JsonNode body = json.readTree(response.getBody());
            JsonNode status = body.path("status");
            return new Result(text(body, "token"), status.path("id").asInt(0), text(status, "description"),
                    text(body, "stdout"), text(body, "stderr"), text(body, "compile_output"), text(body, "message"),
                    text(body, "time"), body.path("memory").isNumber() ? body.path("memory").asInt() : null);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("在线判题服务暂不可用，请稍后重试");
        }
    }

    private static String text(JsonNode node, String key) {
        return node.path(key).isMissingNode() || node.path(key).isNull() ? "" : node.path(key).asText();
    }

    private RestTemplate rest() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs());
        factory.setReadTimeout(properties.getTimeoutMs());
        return new RestTemplate(factory);
    }
}
