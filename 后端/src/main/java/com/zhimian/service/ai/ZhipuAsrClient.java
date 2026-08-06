package com.zhimian.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.BizException;
import com.zhimian.config.AsrProperties;
import com.zhimian.dto.SpeechTranscriptResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 智谱 GLM-ASR 文件转写客户端。
 */
@Slf4j
@Component
public class ZhipuAsrClient {

    private final AsrProperties properties;
    private final ObjectMapper objectMapper;

    public ZhipuAsrClient(AsrProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public SpeechTranscriptResponse transcribe(byte[] audio, String filename, double duration) {
        if (!properties.isUsable()) {
            throw new BizException("语音识别尚未配置，请先设置 ZHIPU_API_KEY");
        }

        try {
            ByteArrayResource audioResource = new ByteArrayResource(audio) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("model", properties.getModel());
            form.add("stream", "false");
            form.add("file", audioResource);

            String raw = restClient().post()
                    .uri("/audio/transcriptions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(raw);
            String text = root.path("text").asText("").trim();
            if (text.isEmpty()) {
                throw new BizException("没有识别到有效语音，请重新录制");
            }
            return new SpeechTranscriptResponse(
                    text,
                    root.path("request_id").asText(null),
                    root.path("model").asText(properties.getModel()),
                    duration
            );
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("智谱 ASR 调用失败：{}", e.getMessage());
            throw new BizException("语音识别暂时不可用，请稍后重试");
        }
    }

    private RestClient restClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs());
        factory.setReadTimeout(properties.getTimeoutMs());
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
