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
            throw new BizException("语音识别未配置：请运行“一键启动网页.bat”输入自己的智谱 API Key，然后重启后端；手动配置方法见 README。");
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
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("智谱 ASR 调用失败：{} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BizException(explainFailure(e));
        } catch (Exception e) {
            log.warn("智谱 ASR 调用失败：{}", e.getMessage());
            throw new BizException("语音识别暂时不可用，请稍后重试");
        }
    }

    /** 把智谱的错误码翻成人话，别一律回「稍后重试」。 */
    private String explainFailure(org.springframework.web.client.RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body.contains("1113") || body.contains("余额不足")) {
            return "智谱账户没有可用的语音识别额度（1113 余额不足），请到智谱后台充值或开通资源包后再试";
        }
        if (e.getStatusCode().value() == 401 || body.contains("1000") || body.contains("令牌")) {
            return "智谱 API Key 无效或已过期，请更换 key 后重启后端";
        }
        if (body.contains("1213") || body.contains("音频") || body.contains("file")) {
            return "这段音频智谱没能识别（格式或内容不合规），请重新录制 30 秒以内的清晰人声";
        }
        return "语音识别暂时不可用，请稍后重试";
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
