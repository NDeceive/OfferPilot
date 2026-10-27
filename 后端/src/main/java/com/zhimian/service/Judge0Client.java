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
import java.util.Base64;
import java.nio.charset.StandardCharsets;

/** 对自部署 Judge0 CE 的最小封装；不使用公共判题服务，也不上传任何密钥。 */
@Component
@RequiredArgsConstructor
public class Judge0Client {
    private final JudgeProperties properties;
    private final ObjectMapper json;
    private volatile Map<String,Object> verifiedCapability;
    private volatile long verifiedAt;

    public static class UnavailableException extends BizException { public UnavailableException(String message) { super(message); } }

    public record Result(String token, int statusId, String status, String stdout, String stderr,
                         String compileOutput, String message, String time, Integer memory) {}

    public synchronized Map<String,Object> capability() {
        if (!properties.isUsable()) return Map.of("ready",false,"notice","判题服务未启用，代码仍可保存。", "languages",Map.of());
        if(verifiedCapability!=null && System.currentTimeMillis()-verifiedAt<60000) return verifiedCapability;
        try {
            Integer id = properties.getLanguageIds().get("java");
            if (id == null || id <= 0) throw new IllegalStateException();
            HttpHeaders headers = headers();
            String raw = rest().exchange(properties.getBaseUrl().replaceAll("/+$","")+"/languages/"+id,HttpMethod.GET,new HttpEntity<>(headers),String.class).getBody();
            String name = json.readTree(raw).path("name").asText();
            if (!name.matches("(?i).*\\b(?:OpenJDK|JDK)\\s+"+java.util.regex.Pattern.quote(properties.getJavaVersion())+"(?:[.\\s)].*|$)")) throw new IllegalStateException();
            Result probe=judge("java","public class Main { public static void main(String[] args) { System.out.print(System.getProperty(\"java.version\")); } }","");
            if(probe.statusId()!=3 || !probe.stdout().strip().startsWith(properties.getJavaVersion()+".")) throw new IllegalStateException();
            verifiedCapability=Map.of("ready",true,"languages",Map.of("java",name),"runtimeVersion",probe.stdout().strip(),"notice","");verifiedAt=System.currentTimeMillis();
            return verifiedCapability;
        } catch (Exception e) { return Map.of("ready",false,"notice","判题服务或 Java 版本尚未验证，请稍后重试。","languages",Map.of()); }
    }

    public Result judge(String language, String sourceCode, String stdin) {
        if (!properties.isUsable()) throw new UnavailableException("在线判题服务尚未启用；请部署 Judge0 CE 后配置 JUDGE_ENABLED 与 JUDGE_BASE_URL");
        Integer languageId = properties.getLanguageIds().get(language == null ? "java" : language.toLowerCase());
        if (languageId == null) throw new BizException("该语言尚未配置 Judge0 language_id：" + language);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("language_id", languageId);
        payload.put("source_code", encode(sourceCode));
        payload.put("stdin", encode(stdin == null ? "" : stdin));
        payload.put("cpu_time_limit", 3);
        payload.put("memory_limit", 128000);
        payload.put("wall_time_limit", 6);
        payload.put("max_file_size", 64);
        payload.put("enable_network", false);
        HttpHeaders headers = headers();
        try {
            String base = properties.getBaseUrl().replaceAll("/+$", "");
            ResponseEntity<String> response = rest().exchange(base + "/submissions?base64_encoded=true&wait=true", HttpMethod.POST,
                    new HttpEntity<>(json.writeValueAsString(payload), headers), String.class);
            JsonNode body = json.readTree(response.getBody());
            JsonNode status = body.path("status");
            return new Result(text(body, "token"), status.path("id").asInt(0), text(status, "description"),
                    decoded(body, "stdout"), decoded(body, "stderr"), decoded(body, "compile_output"), decoded(body, "message"),
                    text(body, "time"), body.path("memory").isNumber() ? body.path("memory").asInt() : null);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new UnavailableException("在线判题服务暂不可用，请稍后重试");
        }
    }

    private HttpHeaders headers() { var h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); if(properties.getAuthToken()!=null && !properties.getAuthToken().isBlank()) h.set("X-Auth-Token",properties.getAuthToken()); return h; }
    private static String encode(String s) { return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
    private static String decoded(JsonNode n,String key) { String s=text(n,key); return s.isEmpty()?"":new String(Base64.getDecoder().decode(s),StandardCharsets.UTF_8); }

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
