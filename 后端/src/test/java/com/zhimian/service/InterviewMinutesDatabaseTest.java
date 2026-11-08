package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.BizException;
import com.zhimian.config.UserContext;
import com.zhimian.entity.*;
import com.zhimian.mapper.*;
import com.zhimian.service.ai.DeepSeekClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import java.util.concurrent.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MySQL and production services. Only LLM/scheduler are replaced; all rows roll back. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"digital-human.launcher.enabled=false","mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"})
@Transactional
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="INTERVIEW_MINUTES_DB_TEST",matches="true")
class InterviewMinutesDatabaseTest {
    @Autowired InterviewMinutesService minutes;
    @Autowired InterviewFlowService flow;
    @Autowired MinutesSummaryJobService jobs;
    @Autowired JdbcTemplate db;
    @Autowired SysUserMapper users;
    @Autowired InterviewSessionMapper sessions;
    @Autowired InterviewMessageMapper messages;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @Autowired com.zhimian.config.JwtUtil jwt;
    @Autowired PlatformTransactionManager transactions;
    @MockBean DeepSeekClient ai;
    @MockBean QuestionBootstrapper bootstrapper;
    @MockBean org.springframework.scheduling.TaskScheduler scheduler;
    long sid,uid;

    @BeforeEach void setup() {
        var u=new SysUser();u.setUsername("minutes-test-"+UUID.randomUUID().toString().substring(0,8));u.setPassword("test-only");u.setNickname("纪要测试考生");u.setRole("STUDENT");u.setStatus(1);users.insert(u);uid=u.getId();UserContext.set(uid,"STUDENT");
        var s=new InterviewSession();s.setUserId(uid);s.setJobId(db.queryForObject("SELECT MIN(id) FROM job_position WHERE status=1",Long.class));s.setStatus("ONGOING");s.setStartTime(minutes.clock());s.setDurationSeconds(600);s.setDifficulty(2);sessions.insert(s);sid=s.getId();
    }
    @AfterEach void clear(){UserContext.clear();}
    long message(String role,String type,String text) {
        var m=new InterviewMessage();m.setSessionId(sid);m.setRoundNo(1);m.setQuestionId(0L);m.setRole(role);m.setMsgType(type);m.setContent(text);m.setReferenceAnswer("内部答案不能泄漏");minutes.stamp(m);messages.insert(m);return m.getId();
    }
    void prompt(String text){message("INTERVIEWER","MAIN",text);}
    void answer(String text){message("CANDIDATE","ANSWER",text);}
    Map<String,Object> control(boolean enabled){return minutes.control(sid,enabled,((Number)minutes.state(sid).get("controlVersion")).longValue(),UUID.randomUUID().toString());}
    void finish(){db.update("UPDATE interview_session SET status='FINISHED' WHERE id=?",sid);minutes.finish(sid);}
    List<String> texts(){return minutes.entries(sid,0,100).stream().map(e->String.valueOf(e.get("text"))).toList();}

    @Test void disabledSessionNeverCreatesSummaryAndCannotBackfill() {
        minutes.initialize(sid,false);prompt("关闭期间的问题");answer("关闭期间的回答");finish();
        assertTrue(texts().isEmpty());assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM minutes_summary_job WHERE session_id=?",Integer.class,sid));
        assertThrows(BizException.class,()->control(true));verifyNoInteractions(ai);
    }
    @Test void realStartCapturesFirstPromptAndRetriedStartKeepsTheSameDocument() {
        var request=new com.zhimian.dto.StartInterviewRequest();
        request.setJobId(db.queryForObject("SELECT MIN(id) FROM job_position WHERE status=1",Long.class));
        request.setSkipResume(true);request.setDurationSeconds(600);request.setMinutesEnabled(true);request.setRequestId(UUID.randomUUID().toString());
        var started=flow.start(request);long source=started.getSessionId();
        assertEquals(true,minutes.state(source).get("enabled"));
        assertTrue(minutes.entries(source,0,100).stream().anyMatch(e->"MAIN".equals(e.get("entryType"))));
        assertEquals(source,flow.start(request).getSessionId());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM minutes_window WHERE session_id=?",Integer.class,source));
    }
    @Test void windowsExcludeCrossBoundaryAnswersAndRetainOnlyOptedInContent() {
        minutes.initialize(sid,false);prompt("之前的问题");control(true);answer("不能补录的旧回答");
        prompt("已记录问题");answer("已记录回答");control(false);
        prompt("关闭后的问题");control(true);answer("跨区间回答");
        prompt("重开后的问题");answer("重开后的回答");
        var text=String.join("\n",texts());assertTrue(text.contains("已记录回答"));assertTrue(text.contains("重开后的回答"));
        assertFalse(text.contains("不能补录"));assertFalse(text.contains("跨区间回答"));assertFalse(text.contains("关闭后的问题"));assertFalse(text.contains("内部答案"));
        int size=texts().size();minutes.state(sid);assertEquals(size,texts().size(),"projection must be idempotent");
    }
    @Test void pauseKeepsIntentButExcludesTheCurrentAnswer() {
        minutes.initialize(sid,true);prompt("暂停前的问题");minutes.paused(sid,true);assertEquals(true,minutes.state(sid).get("enabled"));
        minutes.paused(sid,false);answer("跨暂停回答");prompt("恢复的新问题");answer("恢复的新回答");
        assertFalse(texts().contains("跨暂停回答"));assertTrue(texts().contains("恢复的新回答"));
    }
    @Test void mixedWindowAudioIsNeverLinkedAndExplicitSkipsRemainVisible() {
        minutes.initialize(sid,true);prompt("第一问");answer("第一答");control(false);control(true);
        prompt("下一问");message("CANDIDATE","SKIPPED","[已跳过]");finish();
        db.update("INSERT INTO interview_round_audio(session_id,round_no,question_id,file_path,duration_ms,byte_size,status) VALUES (?,1,0,'test-only.wav',1000,100,'OK')",sid);
        var records=minutes.entries(sid,0,100);
        assertTrue(records.stream().anyMatch(e->"SKIPPED".equals(e.get("entryType"))));
        assertTrue(records.stream().noneMatch(e->e.containsKey("audioId")));
    }
    @Test void controlReceiptsAndVersionsPreventDuplicateOrStaleMutations() {
        minutes.initialize(sid,false);String request=UUID.randomUUID().toString();minutes.control(sid,true,0,request);minutes.control(sid,true,0,request);
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM minutes_window WHERE session_id=?",Integer.class,sid));
        assertThrows(BizException.class,()->minutes.control(sid,false,0,UUID.randomUUID().toString()));
        assertThrows(BizException.class,()->minutes.control(sid,false,0,request));
    }
    @Test void scopeAndOwnershipApplyToExportSummaryAndDeletion() {
        minutes.initialize(sid,true);prompt("可见问题");answer("可见回答");control(false);prompt("秘密问题");answer("秘密回答");finish();
        String export=minutes.export(sid,"record","txt");assertTrue(export.contains("可见回答"));assertTrue(export.contains("纪要测试考生"));assertFalse(export.contains("提交于"));assertFalse(export.contains("纪要已开启"));assertFalse(export.contains("秘密回答"));assertFalse(export.contains("内部答案"));
        assertTrue(minutes.export(sid,"record","md").startsWith("# 面试记录"));
        UserContext.set(uid+100000,"STUDENT");assertThrows(BizException.class,()->minutes.state(sid));assertThrows(BizException.class,()->minutes.export(sid,"record","txt"));assertThrows(BizException.class,()->minutes.summary(sid));assertThrows(BizException.class,()->minutes.delete(sid));
    }
    void successModel() {
        when(ai.chatTextDetailed(anyString(),anyString())).thenAnswer(invocation->{
            String input=invocation.getArgument(1);assertFalse(input.contains("内部答案"));
            return new DeepSeekClient.TextCallResult("本次面试围绕已记录的问题展开，考生说明了相应处理思路。",DeepSeekClient.JsonFailure.NONE);
        });
    }
    @Test void summaryPublishesIndependentlyAndFailedRegenerationKeepsPreviousVersion() {
        minutes.initialize(sid,true);prompt("问题");answer("回答");finish();successModel();jobs.processNext();
        assertEquals("READY",minutes.state(sid).get("summaryState"));assertFalse(minutes.summary(sid).isEmpty());
        String exported=minutes.export(sid,"summary","txt");assertTrue(exported.contains("考生说明"));assertFalse(exported.contains("来源："));assertFalse(exported.contains("本场概览"));
        String old=String.valueOf(minutes.summary(sid).get("outputJson"));
        minutes.regenerate(sid,"retry-test");doReturn(new DeepSeekClient.TextCallResult(null,DeepSeekClient.JsonFailure.NOT_CONFIGURED)).when(ai).chatTextDetailed(anyString(),anyString());jobs.processNext();
        assertEquals("FAILED",minutes.state(sid).get("summaryState"));assertEquals(old,minutes.summary(sid).get("outputJson"));
    }
    @Test void deletionDuringInferenceCannotResurrectDocument() {
        minutes.initialize(sid,true);prompt("问题");answer("回答");finish();
        when(ai.chatTextDetailed(anyString(),anyString())).thenAnswer(i->{minutes.delete(sid);return new DeepSeekClient.TextCallResult(null,DeepSeekClient.JsonFailure.NOT_CONFIGURED);});
        jobs.processNext();assertEquals("DELETED",minutes.state(sid).get("lifecycle"));assertTrue(texts().isEmpty());assertTrue(minutes.summary(sid).isEmpty());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM minutes_summary_job WHERE session_id=?",Integer.class,sid));
    }
    @Test void longRecordsKeepCompletePairsAndIncludeTheFinalAnswer() throws Exception {
        minutes.initialize(sid,true);
        for(int i=0;i<6;i++){prompt("长记录问题"+i);answer("内容".repeat(2000)+(i==5?"末尾记录验证":"中间回答"));}
        finish();var sawLast=new java.util.concurrent.atomic.AtomicBoolean();var calls=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation->{
            var input=json.readTree((String)invocation.getArgument(1));calls.incrementAndGet();
            for(int i=0;i<input.size();i++) {
                var entry=input.get(i);
                if(entry.isTextual())continue; // final coherent merge receives partial paragraphs
                if("CANDIDATE".equals(entry.get("role").asText()))assertEquals("INTERVIEWER",input.get(i-1).get("role").asText());
                if(entry.get("text").asText().contains("末尾记录验证"))sawLast.set(true);
            }
            return new DeepSeekClient.TextCallResult("本次面试包含多个连续话题，考生逐一介绍了相关内容。",DeepSeekClient.JsonFailure.NONE);
        }).when(ai).chatTextDetailed(anyString(),anyString());
        jobs.processNext();assertTrue(calls.get()>1);assertTrue(sawLast.get());assertEquals("READY",minutes.state(sid).get("summaryState"));
    }
    @Test void authenticatedHttpEndpointsRespectOwnershipAndExportScope() throws Exception {
        minutes.initialize(sid,true);prompt("HTTP可见问题");answer("HTTP可见回答");
        String token=jwt.generateToken(uid,users.selectById(uid).getUsername(),"STUDENT");
        mvc.perform(get("/api/interview/"+sid+"/minutes").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(true));
        mvc.perform(get("/api/interview/"+sid+"/minutes/export").param("kind","record").param("format","txt").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("HTTP可见回答")));
        mvc.perform(put("/api/interview/"+sid+"/minutes/control").header("Authorization","Bearer "+token).contentType("application/json").content("{\"enabled\":false,\"expectedVersion\":0,\"requestId\":\"http-control\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false));
        mvc.perform(get("/api/interview/"+(sid+1000000)+"/minutes").header("Authorization","Bearer "+token))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(200)));
        UserContext.set(uid,"STUDENT");
    }
    @Test
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void controlDoesNotWaitForTheLongInterviewTransaction() throws Exception {
        minutes.initialize(sid,true);
        var pool=Executors.newFixedThreadPool(2);var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try {
            var interview=pool.submit(()->new TransactionTemplate(transactions).executeWithoutResult(tx->{
                db.queryForList("SELECT id FROM interview_session WHERE id=? FOR UPDATE",sid);locked.countDown();
                try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            }));
            assertTrue(locked.await(3,TimeUnit.SECONDS));
            var control=pool.submit(()->{UserContext.set(uid,"STUDENT");try{return minutes.control(sid,false,0,"concurrent-disable");}finally{UserContext.clear();}});
            assertEquals(false,control.get(3,TimeUnit.SECONDS).get("enabled"));
            release.countDown();interview.get(3,TimeUnit.SECONDS);
        } finally {
            release.countDown();pool.shutdownNow();pool.awaitTermination(5,TimeUnit.SECONDS);
            UserContext.set(uid,"STUDENT");minutes.delete(sid);
            db.update("DELETE FROM minutes_document WHERE session_id=?",sid);db.update("DELETE FROM interview_session WHERE id=?",sid);db.update("DELETE FROM sys_user WHERE id=?",uid);
        }
    }
}
