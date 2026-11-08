package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.service.ai.ResumeDeepSeekClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reads the local bank and sends synthetic job/tags only; no student data or DB writes. */
@EnabledIfEnvironmentVariable(named="RUN_RESUME_SKILL_LIVE",matches="true")
class ResumeSkillPredictionLiveTest {
    @Test void realBankCandidatesAreSelectedByConfiguredDeepSeek() {
        new ApplicationContextRunner()
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,JdbcTemplateAutoConfiguration.class))
                .withUserConfiguration(ResumeDeepSeekClient.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var json=new ObjectMapper();
                    var ai=context.getBean(ResumeDeepSeekClient.class);
                    assertTrue(ai.isUsable(),"Resume DeepSeek must be configured for this explicit live test");
                    var service=new ResumeSkillPredictionService(context.getBean(JdbcTemplate.class),json,ai);
                    var tags=List.of("Java","MySQL","Redis");
                    var candidates=service.candidates(tags);
                    assertFalse(candidates.isEmpty(),"Local bank must contain questions matching the synthetic tags");
                    var snapshot=new ResumeAnalysisService.Snapshot(1L,"Java 后端开发","Java 后端开发，关注数据库与缓存基础",null,
                            "SYNTHETIC",0L,"联调材料",Map.of("skillTags",String.join("\n",tags)),"live-test");
                    var result=service.predict(snapshot,Set.of("technical_base"));
                    assertTrue(result.notice().isBlank(),result.notice());
                    assertEquals(3,result.items().size());
                    assertTrue(result.items().stream().allMatch(item->candidates.stream().anyMatch(c->c.question().equals(item.question()))));
                    Path out=Path.of("../output/interview-predictions-qa");Files.createDirectories(out);
                    Files.writeString(out.resolve("deepseek-bank-live.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(
                            Map.of("live",true,"provider","deepseek","model",ai.modelName(),"tags",tags,
                                    "candidateCount",candidates.size(),"selected",result.items())));
                });
    }
}
