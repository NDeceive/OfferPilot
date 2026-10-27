package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.BizException;
import com.zhimian.config.UserContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@EnabledIfEnvironmentVariable(named="RUN_PRACTICE_DB_TEST",matches="true")
class ObjectivePracticeDatabaseTest {
    Connection connection; JdbcTemplate db; PracticeBankService bank; PracticeService practice;
    @BeforeEach void open() throws Exception {
        connection=DriverManager.getConnection(System.getenv().getOrDefault("PRACTICE_DB_URL","jdbc:mysql://127.0.0.1:3306/zhimian?serverTimezone=Asia/Shanghai"),System.getenv().getOrDefault("DB_USER","root"),System.getenv().getOrDefault("DB_PASSWORD","123456"));
        connection.setAutoCommit(false);db=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
        var json=new ObjectMapper(); bank=new PracticeBankService(db,json); practice=new PracticeService(db,json,mock(Judge0Client.class),bank);UserContext.set(21L,"STUDENT");
    }
    @AfterEach void close() throws Exception {UserContext.clear();if(connection!=null){connection.rollback();connection.close();}}
    long id(Map<String,Object> value){return ((Number)value.get("id")).longValue();}
    long job(){return db.queryForObject("SELECT id FROM job_position WHERE code='BE-JAVA'",Long.class);}
    Map<String,Object> start(String type,int count){return practice.create(new PracticeService.StartInput(job(),"不限公司","综合高频","normal",type,count,"basic"));}
    List<Map<String,Object>> questions(Map<String,Object> s){return (List<Map<String,Object>>)s.get("questionList");}
    String correct(long qid) throws Exception {return new ObjectMapper().readTree(db.queryForObject("SELECT detail_json FROM practice_question_detail WHERE session_question_id=?",String.class,qid)).path("correctAnswer").asText();}

    @Test void objectiveSnapshotsHideAnswersAndPersistCorrectAndWrongSubmissions() throws Exception {
        for (String type:List.of("choice","multiple_choice","true_false")) {
            var s=start(type,10); long sid=id(s);var q=questions(s).get(0);long qid=id(q);
            assertEquals(10,questions(s).size());
            assertTrue(questions(s).stream().allMatch(item -> type.equals(item.get("type")) && "OBJECTIVE".equals(item.get("gradingMode")) && item.get("options") instanceof List && !item.containsKey("correctAnswer") && !item.containsKey("referenceAnswer")));
            assertThrows(BizException.class,()->practice.answer(sid,qid,new PracticeService.AnswerInput("not-an-option")));
            String expected=correct(qid);
            if(type.equals("multiple_choice")) expected=String.join(",",Arrays.stream(expected.split(",")).sorted(Comparator.reverseOrder()).toList());
            var right=practice.answer(sid,qid,new PracticeService.AnswerInput(expected));
            assertEquals("OBJECTIVE_CORRECT",right.get("status"));assertEquals(true,right.get("passed"));assertTrue(right.containsKey("correctAnswer"));
            var restored=questions(practice.session(sid)).get(0);
            assertFalse(restored.containsKey("correctAnswer"));assertNotNull(restored.get("referenceAnswer"));
            var saved=(Map<String,Object>)((Map<String,Object>)restored.get("latestSubmission")).get("result");
            assertEquals(right.get("status"),saved.get("status"));assertEquals(right.get("correctAnswer"),saved.get("correctAnswer"));assertEquals(right.get("referenceAnswer"),saved.get("referenceAnswer"));
            assertEquals(((Number)right.get("submissionId")).longValue(),((Number)saved.get("submissionId")).longValue());
            String wrong=type.equals("true_false") ? expected.equals("true") ? "false" : "true" : type.equals("multiple_choice") ? expected.split(",")[0] : correct(qid).equals("A") ? "B" : "A";
            assertEquals("OBJECTIVE_INCORRECT",practice.answer(sid,qid,new PracticeService.AnswerInput(wrong)).get("status"));
            var report=PracticeWorkflowService.buildReport(practice.session(sid),List.of());var stats=(Map<String,Object>)report.get("statistics");
            assertEquals(1,stats.get("objectiveIncorrect"));assertEquals(0,stats.get("objectiveCorrect"));
            var mistakes=practice.personalQuestions("mistakes",1,50);
            assertTrue(((List<Map<String,Object>>)mistakes.get("items")).stream().anyMatch(item -> id(item)==qid));
            practice.answer(sid,qid,new PracticeService.AnswerInput(correct(qid)));
            assertFalse(((List<Map<String,Object>>)practice.personalQuestions("mistakes",1,50).get("items")).stream().anyMatch(item -> id(item)==qid));
        }
    }

    @Test void mixedSelectionKeepsCodingQuotaAndDistinctObjectiveConcepts() {
        var s=start("all",20);var qs=questions(s);
        long coding=qs.stream().filter(q -> "coding".equals(q.get("type"))).count();assertTrue(coding>=4&&coding<=6);
        for(String type:List.of("choice","multiple_choice","true_false"))assertTrue(qs.stream().anyMatch(q -> type.equals(q.get("type"))));
        var concepts=new HashSet<Object>();for(var q:qs) if("OBJECTIVE".equals(q.get("gradingMode")))assertTrue(concepts.add(((Map)q.get("source")).get("conceptId")));
        for(String code:List.of("FE-WEB","PM-C","QA-FUNC","ALG-ML","BE-GO","DA-BIZ","FE-IOS")) {
            var selected=bank.select(Map.of("code",code,"abilities","[]","keywords","[]"),"不限公司","综合高频",1,10,0,10,"TRUE_FALSE");
            assertEquals(10,selected.knowledge().size());assertTrue(selected.knowledge().stream().allMatch(q -> "TRUE_FALSE".equals(q.get("subtype"))));
        }
    }

    @Test void failedObjectiveRetryPreservesSnapshotAndResolvesOriginalAfterPassing() throws Exception {
        var s=start("true_false",1);long sid=id(s),qid=id(questions(s).get(0));String expected=correct(qid);
        practice.answer(sid,qid,new PracticeService.AnswerInput(expected.equals("true")?"false":"true"));
        db.update("UPDATE practice_session SET status='FINISHED' WHERE id=?",sid);
        // All service SQL uses the surrounding real connection transaction, rolled back by teardown.
        var tx=mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(call -> ((TransactionCallback<?>)call.getArgument(0)).doInTransaction(new SimpleTransactionStatus()));
        var workflow=new PracticeWorkflowService(db,new ObjectMapper().findAndRegisterModules(),practice,mock(Judge0Client.class),tx);
        long retryId=id(workflow.retry(sid,true));var clone=questions(practice.session(retryId)).get(0);
        assertEquals("true_false",clone.get("type"));assertEquals(questions(s).get(0).get("options"),clone.get("options"));assertFalse(clone.containsKey("correctAnswer"));
        assertEquals(expected,correct(id(clone)));
        assertEquals("OBJECTIVE_CORRECT",practice.answer(retryId,id(clone),new PracticeService.AnswerInput(expected)).get("status"));
        assertEquals("RESOLVED",db.queryForObject("SELECT state FROM practice_review WHERE user_id=21 AND session_question_id=?",String.class,qid));
        assertFalse(((List<Map<String,Object>>)practice.personalQuestions("mistakes",1,50).get("items")).stream().anyMatch(q -> id(q)==qid || id(q)==id(clone)));
    }
}
