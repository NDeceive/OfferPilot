package com.zhimian.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.BizException;
import com.zhimian.entity.JobPosition;
import com.zhimian.service.ai.DeepSeekClient;
import com.zhimian.service.ai.ResumeAiGateway;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResumeAiComposerTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void rewritesOnlyReferencedConfirmedClaimIntoItsOwnSection() throws Exception {
        var client = client(true, "{\"items\":[{\"claimId\":7,\"text\":\"负责订单接口设计，使响应耗时降低20%\"}]}");
        var content = new ResumeAiComposer(new ResumeAiGateway(client, null, false), json).compose(job(), List.of(claim("MODULE")), input());
        assertEquals("负责订单接口设计，使响应耗时降低20%", content.projects());
        assertEquals("", content.skills());
        assertEquals("Java 后端开发", content.targetJob());
    }

    @Test void refusesUnknownEvidenceAndInventedMetric() throws Exception {
        assertThrows(BizException.class, () -> new ResumeAiComposer(
                new ResumeAiGateway(client(true, "{\"items\":[{\"claimId\":999,\"text\":\"负责订单接口\"}]}"), null, false), json)
                .compose(job(), List.of(claim("MODULE")), input()));
        assertThrows(BizException.class, () -> new ResumeAiComposer(
                new ResumeAiGateway(client(true, "{\"items\":[{\"claimId\":7,\"text\":\"负责订单接口设计，响应耗时降低50%\"}]}"), null, false), json)
                .compose(job(), List.of(claim("MODULE")), input()));
    }

    @Test void refusesPromotionOfParticipationAndUnavailableModel() throws Exception {
        assertThrows(BizException.class, () -> new ResumeAiComposer(
                new ResumeAiGateway(client(true, "{\"items\":[{\"claimId\":7,\"text\":\"主导订单接口设计\"}]}"), null, false), json)
                .compose(job(), List.of(claim("PARTICIPATED")), input()));
        assertThrows(BizException.class, () -> new ResumeAiComposer(new ResumeAiGateway(client(false, null), null, false), json)
                .compose(job(), List.of(claim("MODULE")), input()));
    }

    private DeepSeekClient client(boolean usable, String response) throws Exception {
        JsonNode reply = response == null ? null : json.readTree(response);
        return new DeepSeekClient(null, json) {
            @Override public boolean isUsable() { return usable; }
            @Override public JsonNode chatJson(String systemPrompt, String userPrompt) { return reply; }
        };
    }

    private ResumeWorkbenchService.ClaimView claim(String responsibility) {
        return new ResumeWorkbenchService.ClaimView(7L, "PROJECT", "订单模块",
                "设计订单接口，实现服务响应耗时降低20%", "参与订单接口设计，响应耗时降低20%",
                responsibility, "个人负责接口设计", "CONFIRMED", "", LocalDateTime.now());
    }

    private JobPosition job() {
        JobPosition result = new JobPosition();
        result.setName("Java 后端开发");
        result.setDescription("开发可靠的服务接口");
        return result;
    }

    private ResumeWorkbenchService.GenerateInput input() {
        return new ResumeWorkbenchService.GenerateInput("岗位简历", 1L, "张三", "", "");
    }
}
