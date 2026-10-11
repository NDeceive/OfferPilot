package com.zhimian.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.BizException;
import com.zhimian.service.ai.DeepSeekClient;
import com.zhimian.service.ai.ResumeAiGateway;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResumeClaimAiServiceTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void generatesAnExampleWithoutSavingAClaim() throws Exception {
        var example = service(true, "{\"title\":\"订单模块\",\"scenario\":\"假设你参与了订单模块，个人行动填写【请填写真实情况】\","
                + "\"resumeLine\":\"参与订单模块开发，完成【请填写真实情况】\"}")
                .generateExample(new ResumeClaimAiService.ExampleInput("PROJECT", "Java 后端开发", ""));
        assertEquals("订单模块", example.title());
        assertTrue(example.scenario().contains("假设"));
        assertEquals("AI", example.source());
    }

    @Test void unavailableAiBadExampleAndInventedMetricUseSafeRuleTemplate() throws Exception {
        var input = new ResumeClaimAiService.ExampleInput("PROJECT", "Java 后端开发", "参与订单项目");
        assertEquals("RULE", service(false, null).generateExample(input).source());
        assertEquals("RULE", service(true, "{}").generateExample(input).source());
        var invented = service(true,
                "{\"title\":\"订单\",\"scenario\":\"响应提速50%\",\"resumeLine\":\"订单优化\"}").generateExample(input);
        assertEquals("RULE", invented.source());
        assertFalse(invented.scenario().contains("50%"));
        assertThrows(BizException.class, () -> service(true,
                "{\"title\":\"订单\",\"scenario\":\"示例\",\"resumeLine\":\"示例\"}")
                .generateExample(new ResumeClaimAiService.ExampleInput("INVALID", "", "")));
    }

    @Test void providerRateLimitFallsBackLikeFollowUpsWithoutClaimingAiSuccess() throws Exception {
        var service = service(true, null, DeepSeekClient.JsonFailure.RATE_LIMITED);
        var example = service.generateExample(new ResumeClaimAiService.ExampleInput("PROJECT", "Java 后端开发", ""));
        assertEquals("RULE", example.source());
        assertTrue(example.resumeLine().contains("请填写"));
    }

    @Test void wordingRejectsEmptyFactInventedNumbersAndPromotedResponsibility() throws Exception {
        var input = new ResumeClaimAiService.WordingInput("PROJECT", "订单", "参与订单接口设计，耗时减少20%", "PARTICIPATED", "");
        assertThrows(BizException.class, () -> service(true, "{\"text\":\"改写\"}")
                .suggestWording(new ResumeClaimAiService.WordingInput("PROJECT", "订单", "", "PARTICIPATED", "")));
        assertThrows(BizException.class, () -> service(true, "{\"text\":\"耗时减少50%\"}").suggestWording(input));
        assertThrows(BizException.class, () -> service(true, "{\"text\":\"主导订单接口设计\"}").suggestWording(input));
        assertThrows(BizException.class, () -> service(false, null).suggestWording(input));
    }

    @Test void wordingReturnsDraftOnly() throws Exception {
        var result = service(true, "{\"text\":\"参与订单接口设计，耗时减少20%\"}")
                .suggestWording(new ResumeClaimAiService.WordingInput("PROJECT", "订单", "参与订单接口设计，耗时减少20%", "PARTICIPATED", ""));
        assertEquals("参与订单接口设计，耗时减少20%", result.text());
    }

    private ResumeClaimAiService service(boolean usable, String response) throws Exception {
        return service(usable, response, DeepSeekClient.JsonFailure.NONE);
    }

    private ResumeClaimAiService service(boolean usable, String response, DeepSeekClient.JsonFailure failure) throws Exception {
        JsonNode reply = response == null ? null : json.readTree(response);
        DeepSeekClient client = new DeepSeekClient(null, json) {
            @Override public boolean isUsable() { return usable; }
            @Override public JsonCallResult chatJsonDetailed(String systemPrompt, String userPrompt) {
                return new JsonCallResult(reply, failure);
            }
        };
        return new ResumeClaimAiService(new ResumeAiGateway(client, null, false), json);
    }
}
