package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.config.UserContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="RUN_PRACTICE_DB_TEST",matches="true")
class PracticeWorkflowDatabaseTest {
    Connection connection; JdbcTemplate db; PracticeService practice; PracticeWorkflowService workflow; TransactionTemplate transaction; Judge0Client judge;
    @BeforeEach void open() throws Exception {
        connection=DriverManager.getConnection(System.getenv().getOrDefault("PRACTICE_DB_URL","jdbc:mysql://127.0.0.1:3306/zhimian?serverTimezone=Asia/Shanghai"),System.getenv().getOrDefault("DB_USER","root"),System.getenv().getOrDefault("DB_PASSWORD","123456"));
        var source=new SingleConnectionDataSource(connection,true);db=new JdbcTemplate(source);transaction=new TransactionTemplate(new DataSourceTransactionManager(source));
        var json=new ObjectMapper().findAndRegisterModules();judge=mock(Judge0Client.class);var bank=new PracticeBankService(db,json);
        practice=new PracticeService(db,json,judge,bank);workflow=new PracticeWorkflowService(db,json,practice,judge,transaction);UserContext.set(21L,"STUDENT");
        when(judge.capability()).thenReturn(Map.of("ready",true,"languages",Map.of("java","Java (OpenJDK 17)")));
    }
    @AfterEach void close() throws Exception {UserContext.clear();if(connection!=null)connection.close();}
    long job(){return db.queryForObject("SELECT id FROM job_position WHERE code='BE-JAVA'",Long.class);}
    @Test void taskIdempotencyFailureRecoveryAndSettlementAreAtomic() {
        transaction.executeWithoutResult(tx -> {
            try {
                var session=practice.create(new PracticeService.StartInput(job(),"不限公司","编程与算法","normal","coding",1,"basic"));long sid=((Number)session.get("id")).longValue();
                long qid=((Number)((List<Map<String,Object>>)session.get("questionList")).get(0).get("id")).longValue();
                var input=new PracticeWorkflowService.TaskInput(UUID.randomUUID().toString(),"submit","public class Main {}","java");
                var task=workflow.createTask(sid,qid,input);long tid=((Number)task.get("id")).longValue();
                assertEquals(tid,((Number)workflow.createTask(sid,qid,input).get("id")).longValue());
                assertThrows(RuntimeException.class,()->workflow.createTask(sid,qid,new PracticeWorkflowService.TaskInput(input.requestKey(),"submit","changed","java")));
                assertEquals("PROCESSING",workflow.finish(sid).get("settlementStatus"));
                when(judge.judge(anyString(),anyString(),anyString())).thenReturn(new Judge0Client.Result("test",6,"Compilation Error","","","Main.java:4: error: expected","","",null));
                workflow.work();UserContext.set(21L,"STUDENT");
                assertEquals("COMPILE_ERROR",workflow.task(tid).get("status"));
                var report=workflow.report(sid);assertEquals("READY",report.get("settlementStatus"));assertEquals(1,((Map)report.get("statistics")).get("codingFailed"));
                assertEquals(report.get("statistics"),workflow.finish(sid).get("statistics"));assertThrows(RuntimeException.class,()->practice.submitCode(sid,qid,new PracticeService.CodeInput("code","java")));
                assertEquals(1,workflow.submissions(sid,qid,1).get("total"));
                var retry=workflow.retry(sid,true);assertNotEquals(sid,((Number)retry.get("id")).longValue());
                UserContext.set(22L,"STUDENT");assertThrows(RuntimeException.class,()->workflow.task(tid));assertThrows(RuntimeException.class,()->workflow.report(sid));assertThrows(RuntimeException.class,()->workflow.review(qid,"PENDING"));
            } finally { tx.setRollbackOnly(); }
        });
    }
    @Test void unavailableServiceAndSelfReviewDoNotInventCorrectness() {
        transaction.executeWithoutResult(tx -> {
            try {
                var s=practice.create(new PracticeService.StartInput(job(),"不限公司","Java核心","normal","knowledge",1,"basic"));long sid=((Number)s.get("id")).longValue();long qid=((Number)((List<Map<String,Object>>)s.get("questionList")).get(0).get("id")).longValue();
                db.update("UPDATE practice_question_detail SET detail_json=JSON_SET(detail_json,'$.gradingMode','SELF_REVIEW') WHERE session_question_id=?",qid);
                practice.answer(sid,qid,new PracticeService.AnswerInput("我的自评回答"));workflow.review(qid,"PENDING");
                assertTrue(((List<Map<String,Object>>)practice.personalQuestions("mistakes",1,50).get("items")).stream().anyMatch(q->((Number)q.get("id")).longValue()==qid));
                var report=workflow.finish(sid);assertEquals(1,((Map)report.get("statistics")).get("selfReview"));assertEquals(0,((Map)report.get("statistics")).get("objectiveCorrect"));
                var code=practice.create(new PracticeService.StartInput(job(),"不限公司","编程与算法","normal","coding",1,"basic"));long cid=((Number)code.get("id")).longValue();long cqid=((Number)((List<Map<String,Object>>)code.get("questionList")).get(0).get("id")).longValue();
                when(judge.capability()).thenReturn(Map.of("ready",false,"notice","unavailable"));
                var task=workflow.createTask(cid,cqid,new PracticeWorkflowService.TaskInput(UUID.randomUUID().toString(),"submit","code","java"));assertEquals("SERVICE_UNAVAILABLE",task.get("status"));
                when(judge.capability()).thenReturn(Map.of("ready",true,"languages",Map.of("java","Java (OpenJDK 17)")));
                when(judge.judge(anyString(),anyString(),anyString())).thenThrow(new Judge0Client.UnavailableException("unavailable"));
                var outage=workflow.createTask(cid,cqid,new PracticeWorkflowService.TaskInput(UUID.randomUUID().toString(),"submit","code","java"));
                workflow.work();UserContext.set(21L,"STUDENT");assertEquals("SERVICE_UNAVAILABLE",workflow.task(((Number)outage.get("id")).longValue()).get("status"));
                var failed=workflow.finish(cid);assertEquals(0,((Map)failed.get("statistics")).get("submitted"));assertEquals(1,failed.get("systemFailureCount"));
            } finally { tx.setRollbackOnly(); }
        });
    }
    @Test void concurrentTasksStayPendingAndSuccessfulRetryResolvesOriginalReview() {
        transaction.executeWithoutResult(tx -> {
            try {
                var s=practice.create(new PracticeService.StartInput(job(),"不限公司","编程与算法","normal","coding",1,"basic"));long sid=((Number)s.get("id")).longValue();long qid=((Number)((List<Map<String,Object>>)s.get("questionList")).get(0).get("id")).longValue();
                var a=workflow.createTask(sid,qid,new PracticeWorkflowService.TaskInput(UUID.randomUUID().toString(),"submit","bad","java"));
                workflow.createTask(sid,qid,new PracticeWorkflowService.TaskInput(UUID.randomUUID().toString(),"run","bad","java"));
                when(judge.judge(anyString(),anyString(),anyString())).thenReturn(new Judge0Client.Result("test",6,"Compilation Error","","","error","","",null));
                workflow.finish(sid);workflow.work();UserContext.set(21L,"STUDENT");assertEquals("PROCESSING",workflow.report(sid).get("settlementStatus"));
                workflow.work();UserContext.set(21L,"STUDENT");assertEquals("READY",workflow.report(sid).get("settlementStatus"));
                long retryId=((Number)workflow.retry(sid,true).get("id")).longValue();long retryQuestion=db.queryForObject("SELECT id FROM practice_session_question WHERE session_id=?",Long.class,retryId);
                var tests=new ObjectMapper().readValue(db.queryForObject("SELECT tests_json FROM practice_session_question WHERE id=?",String.class,retryQuestion),List.class);
                when(judge.judge(anyString(),anyString(),anyString())).thenAnswer(call -> {String input=call.getArgument(2);String expected=(String)((Map)tests.stream().filter(t->input.equals(((Map)t).get("input"))).findFirst().orElseThrow()).get("expected");return new Judge0Client.Result("test",3,"Accepted",expected,"","","","0.01",100);});
                var passed=workflow.createTask(retryId,retryQuestion,new PracticeWorkflowService.TaskInput(UUID.randomUUID().toString(),"submit","correct","java"));workflow.work();UserContext.set(21L,"STUDENT");
                assertEquals("ACCEPTED",workflow.task(((Number)passed.get("id")).longValue()).get("status"));
                assertEquals("RESOLVED",db.queryForObject("SELECT state FROM practice_review WHERE user_id=21 AND session_question_id=?",String.class,qid));
            } catch(Exception e) {throw new RuntimeException(e);} finally {tx.setRollbackOnly();}
        });
    }
    @Test void staleTasksAndObjectiveQuestionsHaveHonestOutcomes() {
        transaction.executeWithoutResult(tx -> {
            try {
                var s=practice.create(new PracticeService.StartInput(job(),"不限公司","编程与算法","normal","coding",1,"basic"));long sid=((Number)s.get("id")).longValue();long qid=((Number)((List<Map<String,Object>>)s.get("questionList")).get(0).get("id")).longValue();
                var t=workflow.createTask(sid,qid,new PracticeWorkflowService.TaskInput(UUID.randomUUID().toString(),"submit","code","java"));
                db.update("UPDATE practice_judge_task SET status='RUNNING',started_at=DATE_SUB(NOW(),INTERVAL 16 MINUTE) WHERE id=?",t.get("id"));
                workflow.work();UserContext.set(21L,"STUDENT");var report=workflow.finish(sid);assertEquals(0,((Map)report.get("statistics")).get("submitted"));assertEquals(1,report.get("systemFailureCount"));
                var knowledge=practice.create(new PracticeService.StartInput(job(),"不限公司","Java核心","normal","knowledge",1,"basic"));long kid=((Number)knowledge.get("id")).longValue();long kqid=((Number)((List<Map<String,Object>>)knowledge.get("questionList")).get(0).get("id")).longValue();
                db.update("UPDATE practice_question_detail SET detail_json=JSON_SET(detail_json,'$.gradingMode','OBJECTIVE','$.correctAnswer','A') WHERE session_question_id=?",kqid);
                assertFalse(((List<Map<String,Object>>)practice.session(kid).get("questionList")).get(0).containsKey("correctAnswer"));
                assertEquals(false,practice.answer(kid,kqid,new PracticeService.AnswerInput("B")).get("passed"));assertEquals(true,practice.answer(kid,kqid,new PracticeService.AnswerInput("A")).get("passed"));
                assertEquals(1,((Map)workflow.finish(kid).get("statistics")).get("objectiveCorrect"));
            } finally {tx.setRollbackOnly();}
        });
    }
}
