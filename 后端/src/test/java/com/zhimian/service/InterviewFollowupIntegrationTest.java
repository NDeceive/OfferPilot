package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.config.JwtUtil;
import com.zhimian.entity.SysUser;
import com.zhimian.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual answer controller, authentication, database and DeepSeek; test rows roll back. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "digital-human.launcher.enabled=false",
        "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_DEEPSEEK_SMOKE", matches = "true")
class InterviewFollowupIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SysUserMapper users;
    @Autowired JwtUtil jwt;
    @Autowired ObjectMapper json;
    // Keep the test context from consuming real users' report queue.
    @MockBean ReportJobService reportJobs;

    /** Also checks the server used by the actual web page, when explicitly requested. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @EnabledIfEnvironmentVariable(named = "RUN_BACKEND_HTTP_SMOKE", matches = "true")
    void runningBackendAnswerApiUsesAi() throws Exception {
        var user = new SysUser();
        user.setUsername("fup-http-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPassword("not-a-login-password");
        user.setRole("STUDENT");
        user.setStatus(1);
        Long sid = null;
        try {
            users.insert(user);
            Long jobId = jdbc.queryForObject("SELECT MIN(id) FROM job_position", Long.class);
            jdbc.update("INSERT INTO interview_session(user_id,job_id,status,start_time,duration_seconds) VALUES (?,?,'ONGOING',NOW(),600)", user.getId(), jobId);
            sid = jdbc.queryForObject("SELECT id FROM interview_session WHERE user_id=?", Long.class, user.getId());
            jdbc.update("INSERT INTO interview_message(session_id,question_id,round_no,role,msg_type,content,ability_tag) VALUES (?,0,1,'INTERVIEWER','MAIN','请介绍 HashMap 的 put、get 流程以及哈希冲突处理。','专业知识')", sid);
            String token = jwt.generateToken(user.getId(), user.getUsername(), user.getRole());
            var response = RestClient.create("http://127.0.0.1:8080").post()
                    .uri("/api/interview/{sessionId}/answer", sid)
                    .header("Authorization", "Bearer " + token).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of("questionId", 0, "roundNo", 1, "requestId", UUID.randomUUID().toString(),
                            "answer", hashMapAnswer()))
                    .retrieve().body(String.class);
            var result = json.readTree(response);
            assertEquals(200, result.path("code").asInt());
            assertEquals("FOLLOWUP", result.path("data").path("nextAction").asText());
            assertFalse(result.path("data").path("followupQuestion").asText().isBlank());
            assertEquals("AI", jdbc.queryForObject("SELECT source FROM interview_followup_record WHERE session_id=?", String.class, sid));
            System.out.println("Verified HashMap follow-up: " + result.path("data").path("followupQuestion").asText());
        } finally {
            // Delete only this test's explicitly created rows; no real users' data is touched.
            if (sid != null) {
                jdbc.update("DELETE FROM student_answer_receipt WHERE session_id=?", sid);
                jdbc.update("DELETE FROM interview_followup_record WHERE session_id=?", sid);
                jdbc.update("DELETE FROM interview_message WHERE session_id=?", sid);
                jdbc.update("DELETE FROM interview_session WHERE id=? AND user_id=?", sid, user.getId());
            }
            if (user.getId() != null) jdbc.update("DELETE FROM sys_user WHERE id=? AND username=?", user.getId(), user.getUsername());
        }
    }

    @Test
    void submittingMainAnswerReturnsAndPersistsLiveAiFollowup() throws Exception {
        var user = new SysUser();
        user.setUsername("followup-test-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPassword("not-a-login-password");
        user.setRole("STUDENT");
        user.setStatus(1);
        users.insert(user);
        Long jobId = jdbc.queryForObject("SELECT MIN(id) FROM job_position", Long.class);
        assertNotNull(jobId);
        jdbc.update("INSERT INTO interview_session(user_id,job_id,status,start_time,duration_seconds) VALUES (?,?,'ONGOING',NOW(),600)", user.getId(), jobId);
        Long sid = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO interview_message(session_id,question_id,round_no,role,msg_type,content,ability_tag) VALUES (?,0,1,'INTERVIEWER','MAIN','请介绍 HashMap 的 put、get 流程以及哈希冲突处理。','专业知识')", sid);
        String token = jwt.generateToken(user.getId(), user.getUsername(), user.getRole());
        String body = json.writeValueAsString(Map.of("questionId", 0, "roundNo", 1,
                "answer", hashMapAnswer(),
                "requestId", UUID.randomUUID().toString()));
        String response = mvc.perform(post("/api/interview/{sessionId}/answer", sid)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nextAction").value("FOLLOWUP"))
                .andExpect(jsonPath("$.data.followupQuestion").isNotEmpty())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String question = json.readTree(response).path("data").path("followupQuestion").asText();
        assertEquals("AI", jdbc.queryForObject("SELECT source FROM interview_followup_record WHERE session_id=?", String.class, sid));
        assertEquals(question, jdbc.queryForObject("SELECT content FROM interview_message WHERE session_id=? AND msg_type='FOLLOWUP'", String.class, sid));
        // Retrying the same answer must replay the receipt, without another follow-up.
        mvc.perform(post("/api/interview/{sessionId}/answer", sid)
                        .header("Authorization", "Bearer " + token).contentType("application/json").content(body))
                .andExpect(jsonPath("$.data.followupQuestion").value(question));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM interview_message WHERE session_id=? AND msg_type='FOLLOWUP'", Integer.class, sid));
    }

    private String hashMapAnswer() {
        return "先算哈希定位到桶，为空直接插入，不为空就便利，key相同就覆盖，不同就尾插。链表长度超过8且数组长度超过64时转为红黑树。get同样先算哈希定位，然后便利比对key和hash和equals找到就返回。哈希冲突用的是拉链法，通过链表存储冲突元素，链表过长时，转红黑树优化查询。";
    }
}
