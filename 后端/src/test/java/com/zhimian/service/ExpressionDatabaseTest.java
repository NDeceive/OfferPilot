package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.BizException;
import com.zhimian.config.UserContext;
import com.zhimian.dto.ExpressionBatchRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.DriverManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Optional real-MySQL check. All test interviews and samples are rolled back. */
@EnabledIfEnvironmentVariable(named = "EXPRESSIONS_DB_TEST", matches = "true")
class ExpressionDatabaseTest {
    @Test void mysqlPersistsDeduplicatesAndGroupsSamplesWithoutLeavingTestRecords() throws Exception {
        String url = System.getenv().getOrDefault("EXPRESSIONS_DB_URL", "jdbc:mysql://localhost:3306/zhimian?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=10000");
        try (var connection = DriverManager.getConnection(url, System.getenv().getOrDefault("DB_USERNAME", "root"),
                System.getenv().getOrDefault("DB_PASSWORD", "123456"))) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ClassPathResource("db/migration_expressions.sql")).execute(jdbc.getDataSource());
            connection.setAutoCommit(false);
            try {
                Long userId = jdbc.queryForObject("SELECT MIN(id) FROM sys_user", Long.class);
                Long jobId = jdbc.queryForObject("SELECT MIN(id) FROM job_position", Long.class);
                assertNotNull(userId); assertNotNull(jobId);
                jdbc.update("INSERT INTO interview_session(user_id,job_id,status,start_time,duration_seconds) VALUES (?,?,'ONGOING',NOW(),600)", userId, jobId);
                long sessionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                jdbc.update("INSERT INTO interview_message(session_id,question_id,round_no,role,msg_type,content) VALUES (?,0,1,'INTERVIEWER','MAIN','Expression test question 1')", sessionId);
                jdbc.update("INSERT INTO interview_message(session_id,question_id,round_no,role,msg_type,content) VALUES (?,-2,2,'INTERVIEWER','MAIN','Expression test question 2')", sessionId);
                long now = jdbc.queryForObject("SELECT UNIX_TIMESTAMP(NOW())*1000", Long.class);
                var probabilities = new LinkedHashMap<String, Double>();
                ExpressionService.KEYS.forEach(k -> probabilities.put(k, k.equals("happy") ? .7 : .05));
                var batch = new ExpressionBatchRequest(List.of(
                        new ExpressionBatchRequest.Sample(UUID.randomUUID().toString(), 0L, 1, now, true, probabilities),
                        new ExpressionBatchRequest.Sample(UUID.randomUUID().toString(), -2L, 2, now, false, null)));
                UserContext.set(userId, "USER");
                var flow = org.mockito.Mockito.mock(InterviewFlowService.class, org.mockito.Mockito.CALLS_REAL_METHODS);
                var mapper = org.mockito.Mockito.mock(com.zhimian.mapper.InterviewSessionMapper.class);
                var session = new com.zhimian.entity.InterviewSession();
                session.setId(sessionId); session.setUserId(userId); session.setStatus("ONGOING"); session.setDurationSeconds(600);
                session.setStartTime(java.time.LocalDateTime.now());
                org.mockito.Mockito.when(mapper.selectById(sessionId)).thenReturn(session);
                org.springframework.test.util.ReflectionTestUtils.setField(flow, "jdbc", jdbc);
                org.springframework.test.util.ReflectionTestUtils.setField(flow, "sessionMapper", mapper);
                org.springframework.test.util.ReflectionTestUtils.setField(flow, "teachingService", org.mockito.Mockito.mock(TeachingService.class));
                assertEquals(true, flow.pause(sessionId, true).get("paused"));
                jdbc.update("UPDATE interview_pause_state SET paused_at=DATE_SUB(NOW(),INTERVAL 20 SECOND) WHERE session_id=?", sessionId);
                session.setStartTime(java.time.LocalDateTime.now().minusSeconds(20));
                var resumed = flow.pause(sessionId, false);
                assertEquals(false, resumed.get("paused"));
                assertTrue((Integer)resumed.get("remainingSeconds") >= 599);
                assertEquals(20L, jdbc.queryForObject("SELECT paused_seconds FROM interview_pause_state WHERE session_id=?", Long.class, sessionId));
                var service = new ExpressionService(jdbc, new ObjectMapper());
                service.save(sessionId, batch);
                service.save(sessionId, batch);
                assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM interview_expression_sample WHERE session_id=?", Long.class, sessionId));
                var report = service.report(sessionId);
                assertEquals(2, ((List<?>)report.get("timeline")).size());
                assertEquals(1, ((Map<?, ?>)report.get("summary")).get("detectedCount"));
                var questions = (List<?>)report.get("questions");
                assertEquals(2, questions.size());
                assertEquals(0, ((Map<?, ?>)((Map<?, ?>)questions.get(1)).get("summary")).get("detectedCount"));
                var mixed = new ExpressionBatchRequest(List.of(new ExpressionBatchRequest.Sample(UUID.randomUUID().toString(), 0L, 2, now, true, probabilities),
                        new ExpressionBatchRequest.Sample(UUID.randomUUID().toString(), 0L, 1, now, true, probabilities)));
                var result=service.save(sessionId,mixed);
                assertEquals(1,((List<?>)result.get("rejected")).size());
                assertEquals(1,((List<?>)result.get("acceptedIds")).size());
                jdbc.update("UPDATE interview_session SET status='FINISHED',end_time=NOW() WHERE id=?", sessionId);
                service.save(sessionId, batch); // Retry after finish remains idempotent.
                connection.rollback();
                assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM interview_session WHERE id=?", Long.class, sessionId));
            } finally { UserContext.clear(); connection.rollback(); }
        }
    }
}
