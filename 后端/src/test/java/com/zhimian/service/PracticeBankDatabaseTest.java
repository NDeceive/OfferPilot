package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.config.UserContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real local MySQL; every write is rolled back. Never enabled in normal test runs. */
@EnabledIfEnvironmentVariable(named="RUN_PRACTICE_DB_TEST",matches="true")
class PracticeBankDatabaseTest {
    @Test void personalLibraryQueriesRespectLatestOutcomeAndOwnership() {
        var s = practice.create(new PracticeService.StartInput(job("BE-JAVA"),"不限公司","Java核心","normal","knowledge",3,"basic"));
        long sessionId = ((Number)s.get("id")).longValue();
        long questionId = ((Number)((List<Map<String,Object>>)s.get("questionList")).get(0).get("id")).longValue();
        practice.toggleFavorite(questionId);
        assertTrue(((List<Map<String,Object>>)practice.personalQuestions("favorites",1,50).get("items")).stream().anyMatch(q -> ((Number)q.get("id")).longValue()==questionId));
        db.update("INSERT INTO practice_submission(session_id,session_question_id,user_id,submission_type,status,is_correct) VALUES(?,?,21,'KNOWLEDGE','NEEDS_REVIEW',0)",sessionId,questionId);
        assertTrue(((List<Map<String,Object>>)practice.personalQuestions("mistakes",1,50).get("items")).stream().anyMatch(q -> ((Number)q.get("id")).longValue()==questionId));
        db.update("INSERT INTO practice_submission(session_id,session_question_id,user_id,submission_type,status,is_correct) VALUES(?,?,21,'KNOWLEDGE','REVIEWED',1)",sessionId,questionId);
        assertTrue(((List<Map<String,Object>>)practice.personalQuestions("mistakes",1,50).get("items")).stream().noneMatch(q -> ((Number)q.get("id")).longValue()==questionId));
        assertEquals(sessionId,((Number)((Map<String,Object>)practice.overview().get("recentSession")).get("id")).longValue());
        practice.finish(sessionId);
        assertTrue(((List<Map<String,Object>>)practice.sessions("FINISHED",1,50).get("items")).stream().anyMatch(q -> ((Number)q.get("id")).longValue()==sessionId));
        UserContext.set(22L,"STUDENT");
        assertTrue(((List<Map<String,Object>>)practice.personalQuestions("favorites",1,50).get("items")).stream().noneMatch(q -> ((Number)q.get("id")).longValue()==questionId));
        assertThrows(RuntimeException.class,() -> practice.removeFavorite(questionId));
        UserContext.set(21L,"STUDENT");practice.removeFavorite(questionId);practice.removeFavorite(questionId);
    }
    Connection connection;JdbcTemplate db;PracticeService practice;PracticeBankService bank;
    @BeforeEach void open() throws Exception {
        String url=System.getenv().getOrDefault("PRACTICE_DB_URL","jdbc:mysql://127.0.0.1:3306/zhimian?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai");
        connection=DriverManager.getConnection(url,System.getenv().getOrDefault("DB_USER","root"),System.getenv().getOrDefault("DB_PASSWORD","123456"));
        connection.setAutoCommit(false);db=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
        var json=new ObjectMapper();bank=new PracticeBankService(db,json);practice=new PracticeService(db,json,mock(Judge0Client.class),bank);UserContext.set(21L,"STUDENT");
    }
    @AfterEach void close() throws Exception {UserContext.clear();if(connection!=null){connection.rollback();connection.close();}}
    long job(String code){return db.queryForObject("SELECT id FROM job_position WHERE code=?",Long.class,code);}
    @Test void realSelectionSnapshotSubmissionAndHistoricalProvenance() {
        assertEquals(40,bank.catalog().get("knowledgeCount"));assertEquals(20,bank.catalog().get("codingCount"));assertEquals(18,bank.catalog().get("cultureCount"));
        var session=practice.create(new PracticeService.StartInput(job("BE-JAVA"),"腾讯","综合高频","normal","all",20,"basic"));
        var questions=(List<Map<String,Object>>)session.get("questionList");long id=((Number)session.get("id")).longValue();
        assertEquals(20,questions.size());
        long coding=questions.stream().filter(q->"coding".equals(q.get("type"))).count();assertTrue(coding>=4&&coding<=6);
        assertTrue(questions.stream().noneMatch(q->q.containsKey("testsJson")||q.containsKey("answerKeywords")));
        assertTrue(questions.stream().allMatch(q->!q.containsKey("referenceAnswer")));
        var culture=questions.stream().filter(q->"CULTURE".equals(q.get("subtype"))).findFirst().orElseThrow();
        var source=culture.get("source");long qid=((Number)culture.get("id")).longValue();
        var result=practice.answer(id,qid,new PracticeService.AnswerInput("先确认用户影响，再协调团队行动并验证结果"));
        assertEquals("SELF_REVIEW",result.get("status"));assertFalse(result.containsKey("score"));assertFalse(result.containsKey("passed"));
        db.update("UPDATE practice_company SET evidence_json='{}' WHERE code='tencent'");
        var restored=(List<Map<String,Object>>)practice.session(id).get("questionList");
        var answered=restored.stream().filter(q->((Number)q.get("id")).longValue()==qid).findFirst().orElseThrow();
        assertEquals(source,answered.get("source"));assertNotNull(answered.get("referenceAnswer"));
        assertEquals(1,practice.session(id).get("completed"));
    }
    @Test void pureCodingCompanyFallbackAndScopedShortage() {
        var code=practice.create(new PracticeService.StartInput(job("FE-WEB"),"腾讯","编程与算法","normal","coding",20,"basic"));
        assertEquals(20,((List<?>)code.get("questionList")).size());
        var normal=practice.create(new PracticeService.StartInput(job("FE-WEB"),"美团","综合高频","normal","all",10,"basic"));
        assertTrue(String.valueOf(((Map<?,?>)normal.get("matching")).get("notice")).contains("暂无"));
        assertThrows(RuntimeException.class,()->practice.create(new PracticeService.StartInput(job("FE-WEB"),"腾讯","JVM","normal","knowledge",10,"basic")));
        var culture=practice.create(new PracticeService.StartInput(job("FE-WEB"),"阿里巴巴","企业情境","normal","knowledge",3,"basic"));
        assertTrue(((List<Map<String,Object>>)culture.get("questionList")).stream().allMatch(q->"CULTURE".equals(q.get("subtype"))));
    }
    @Test void candidateRequiresExplicitReviewAndCannotBeApprovedTwice() throws Exception {
        var json=new ObjectMapper();var company=bank.company("腾讯");
        var payload=Map.of("difficulty",1,"evidence",bank.object(company.get("evidence_json")),"question",Map.of("evidenceIds",List.of("user")),"model","test-fixture");
        db.update("INSERT INTO practice_company_candidate(company_id,job_code,topic,payload_json,created_by) VALUES(?,?,?,?,?)",company.get("id"),"BE-JAVA","项目场景",json.writeValueAsString(payload),21L);
        long id=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        assertEquals(18,bank.catalog().get("cultureCount"));
        UserContext.set(22L,"TEACHER");
        var editor=new CompanyQuestionService(bank,mock(com.zhimian.service.ai.DeepSeekClient.class),json,db);
        var review=new CompanyQuestionService.ReviewInput(true,"审核后的协作题","请说明行动步骤和决策依据","自评要点：证据、协作、取舍");
        assertThrows(RuntimeException.class,()->editor.review(id,review));
        UserContext.set(21L,"TEACHER");assertEquals("APPROVED",editor.review(id,review).get("status"));
        assertEquals(19,bank.catalog().get("cultureCount"));
        assertThrows(RuntimeException.class,()->editor.review(id,review));
    }
}
