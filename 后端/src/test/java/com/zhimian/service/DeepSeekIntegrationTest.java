package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.config.AiProperties;
import com.zhimian.dto.FollowUpRequest;
import com.zhimian.service.ai.DeepSeekClient;
import com.zhimian.service.ai.FollowUpPromptBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in live check using backend configuration; never prints credentials. */
@EnabledIfEnvironmentVariable(named = "RUN_DEEPSEEK_SMOKE", matches = "true")
class DeepSeekIntegrationTest {
    @Test
    void configuredBackendGeneratesAnAiFollowup() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> {
                    AiProperties props = Binder.get(context.getEnvironment()).bind("ai", AiProperties.class).get();
                    assertTrue(props.isUsable(), "Backend AI credentials must be configured");
                    assertEquals("deepseek", props.getProvider());
                    assertEquals("https://api.deepseek.com", props.getBaseUrl());
                    var service = new FollowUpService(props, new DeepSeekClient(props, new ObjectMapper()),
                            new FollowUpPromptBuilder(), new RuleBasedFollowUpGenerator());
                    var request = new FollowUpRequest();
                    request.setPosition("Java后端工程师");
                    request.setQuestion("如何防止缓存击穿？");
                    request.setReferenceAnswer("互斥锁控制热点键回源，合理设置锁超时与降级策略。");
                    request.setAnswer("我们给热点键加互斥锁，只允许一个线程查数据库，其他线程等待缓存重建，但是没有处理锁超时。");
                    var response = service.generate(request);
                    assertEquals("AI", response.getSource(), "Must use the live model rather than RULE fallback");
                    assertNotNull(response.getFollowUpQuestion());
                    assertFalse(response.getFollowUpQuestion().isBlank());
                });
    }
}
