package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.config.AiProperties;
import com.zhimian.service.ai.DeepSeekClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit live call using synthetic dialogue only. Never prints or saves the API key. */
@EnabledIfEnvironmentVariable(named="RUN_MINUTES_DEEPSEEK_SMOKE",matches="true")
class MinutesDeepSeekLiveTest {
    @Test void configuredDeepSeekReturnsOneRealSummaryParagraph() {
        new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer()).run(context->{
            AiProperties props=Binder.get(context.getEnvironment()).bind("ai",AiProperties.class).get();
            assertTrue(props.isUsable(),"DeepSeek credentials must be configured in the backend");
            var client=new DeepSeekClient(props,new ObjectMapper());
            String dialogue="""
                [{"role":"INTERVIEWER","text":"如何排查慢SQL？"},
                {"role":"CANDIDATE","text":"先看慢查询日志，再用EXPLAIN检查索引和扫描行数，优化后通过压测对比。"},
                {"role":"INTERVIEWER","text":"是否有具体的优化指标？"},
                {"role":"CANDIDATE","text":"目前没有记录具体指标，需要补充压测数据。"}]
                """;
            var result=client.chatTextDetailed(MinutesSummaryJobService.PROMPT,dialogue);
            assertEquals(DeepSeekClient.JsonFailure.NONE,result.failure(),"Live DeepSeek call must succeed; no template fallback");
            String paragraph=MinutesSummaryJobService.normalizeParagraph(result.value());
            assertTrue(paragraph.length()>30);assertFalse(paragraph.contains("\n"));
            Path out=Path.of("../artifacts/interview-report-simplify");Files.createDirectories(out);
            Files.writeString(out.resolve("deepseek-live-summary.txt"),paragraph);
            Files.writeString(out.resolve("deepseek-live-result.json"),"{\"live\":true,\"provider\":\"deepseek\",\"model\":\"deepseek-flash\",\"input\":\"synthetic dialogue only\",\"characters\":"+paragraph.length()+"}");
        });
    }
}
