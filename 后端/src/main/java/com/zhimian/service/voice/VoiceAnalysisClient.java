package com.zhimian.service.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.config.VoiceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** 调用本地语音分析边车（Python + Praat）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceAnalysisClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final VoiceProperties props;

    public JsonNode analyze(String absolutePath) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(Math.max(3000, props.getTimeoutMs()));
        RestClient client = RestClient.builder()
                .baseUrl(props.getServiceUrl())
                .requestFactory(factory)
                .build();
        String payload;
        try {
            payload = JSON.writeValueAsString(Map.of("path", absolutePath));
        } catch (Exception e) {
            throw new IllegalStateException("分析请求序列化失败", e);
        }
        String body = client.post()
                .uri("/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
        try {
            return JSON.readTree(body == null ? "{}" : body);
        } catch (Exception e) {
            throw new IllegalStateException("分析结果不是合法 JSON", e);
        }
    }

    public boolean healthy() {
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(1000);
            factory.setReadTimeout(1500);
            RestClient.builder().baseUrl(props.getServiceUrl()).requestFactory(factory).build()
                    .get().uri("/health").retrieve().body(String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
