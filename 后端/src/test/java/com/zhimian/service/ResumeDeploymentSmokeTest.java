package com.zhimian.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.config.JwtUtil;
import com.zhimian.entity.SysUser;
import com.zhimian.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="digital-human.launcher.enabled=false")
@EnabledIfEnvironmentVariable(named="RUN_RESUME_DEPLOY_SMOKE",matches="true")
class ResumeDeploymentSmokeTest {
 @Autowired SysUserMapper users; @Autowired JwtUtil jwt; @Autowired ObjectMapper json;
 @Test void deployedResumeAiUsesConfiguredDeepSeek() throws Exception {
  var u=new SysUser();u.setUsername("resume-deploy-"+UUID.randomUUID());u.setPassword("not-a-login-password");u.setRole("STUDENT");u.setStatus(1);
  try {users.insert(u);var client=RestClient.builder().baseUrl("http://127.0.0.1:8080").defaultHeader("Authorization","Bearer "+jwt.generateToken(u.getId(),u.getUsername(),u.getRole())).build();
   var status=json.readTree(client.get().uri("/api/resume-workbench/ai-status").retrieve().body(String.class));assertEquals(200,status.path("code").asInt());assertTrue(status.path("data").path("configured").asBoolean());
   var result=json.readTree(client.post().uri("/api/resume-workbench/claim-wording/ai").contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(Map.of("category","PROJECT","title","缓存模块","factText","参与缓存模块开发，使用Redis缓存热点数据，编写单元测试。","responsibility","PARTICIPATED","personalBoundary","负责缓存读取与测试，未负责整个项目。")).retrieve().body(String.class));
   assertEquals(200,result.path("code").asInt(),result.path("message").asText());assertFalse(result.path("data").path("text").asText().isBlank());System.out.println("PASS deployed resume DeepSeek status and live wording API");
  } finally {if(u.getId()!=null)users.deleteById(u.getId());}
 }
}
