package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.BizException;
import com.zhimian.config.UserContext;
import com.zhimian.dto.*;
import com.zhimian.entity.InterviewMessage;
import com.zhimian.entity.SysUser;
import com.zhimian.mapper.SysUserMapper;
import com.zhimian.service.ai.DeepSeekClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Actual MySQL and business services. LLM boundaries are test doubles; test rows roll back. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "digital-human.launcher.enabled=false", "interview.audio.dir=../artifacts/interview-flow-recovery/test-audio",
        "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"})
@Transactional
@EnabledIfEnvironmentVariable(named="INTERVIEW_STAGE_DB_TEST",matches="true")
class InterviewStageRecoveryDatabaseTest {
    @Autowired InterviewFlowService flow;
    @Autowired InterviewStageService stages;
    @Autowired AnswerAudioService audio;
    @Autowired ReportService reporting;
    @Autowired com.zhimian.mapper.InterviewSessionMapper sessions;
    @Autowired JdbcTemplate jdbc;
    @Autowired SysUserMapper users;
    @Autowired ObjectMapper json;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    @MockBean DeepSeekClient ai;
    @MockBean FollowUpService followup;
    @MockBean com.zhimian.service.followup.FollowupGate gate;
    @MockBean QuestionBootstrapper bootstrapper;
    @MockBean ReportJobService reports;
    @MockBean PracticeWorkflowService practiceWorker;
    @MockBean org.springframework.scheduling.TaskScheduler taskScheduler;
    final List<Long> created=new ArrayList<>();

    @BeforeEach void user() {
        var user=new SysUser();user.setUsername("stage-check-"+UUID.randomUUID().toString().substring(0,8));
        user.setPassword("test-only");user.setRole("STUDENT");user.setStatus(1);users.insert(user);
        UserContext.set(user.getId(),"STUDENT");
    }
    @AfterEach void cleanup(){for(long id:created)audio.deleteBySession(id);UserContext.clear();}
    InterviewStartResponse start(){
        var req=new StartInterviewRequest();req.setJobId(jdbc.queryForObject("SELECT MIN(id) FROM job_position WHERE status=1",Long.class));
        req.setSkipResume(true);req.setDurationSeconds(600);req.setRequestId(UUID.randomUUID().toString());
        var response=flow.start(req);created.add(response.getSessionId());return response;
    }
    AnswerRequest request(QuestionView prompt,String text){
        var req=new AnswerRequest();req.setQuestionId(prompt.getId());req.setQuestionInstanceId(prompt.getQuestionInstanceId());
        req.setRoundNo(prompt.getRoundNo());req.setRequestId(UUID.randomUUID().toString());req.setAnswer(text);return req;
    }
    AnswerRequest introduction(InterviewStartResponse start,String text){
        var req=request(start.getQuestion(),text);var bytes=wav(false);
        req.setAudioId(audio.save(start.getSessionId(),1,start.getQuestion().getId(),bytes,1000,UserContext.getUserId()).getId());return req;
    }
    QuestionView current(long sid){
        var prompt=(InterviewMessage)flow.resume(sid).get("currentQuestion");var view=new QuestionView();
        view.setId(prompt.getQuestionId());view.setQuestionInstanceId(prompt.getId());view.setRoundNo(prompt.getRoundNo());return view;
    }
    @Test void introductionMustBeSavedAndSubmittedOnceBeforeProfessionalQuestions(){
        var start=start();long sid=start.getSessionId();var prompt=start.getQuestion();
        assertEquals("SELF_INTRODUCTION",prompt.getInterviewStage());assertTrue(prompt.getContent().contains("介绍一下自己"));
        assertEquals(prompt.getQuestionInstanceId(),flow.next(sid).getQuestion().getQuestionInstanceId());
        var invalid=request(prompt,"");assertThrows(BizException.class,()->flow.answer(sid,invalid));
        invalid.setSkipped(true);assertThrows(BizException.class,()->flow.answer(sid,invalid));
        invalid.setSkipped(false);invalid.setAnswer("没有项目经历");assertThrows(BizException.class,()->flow.answer(sid,invalid));
        assertEquals("SELF_INTRODUCTION",stages.stage(sid));
        var req=introduction(start,"我是软件专业学生，目前没有项目经历，正在学习专业基础。");
        var result=flow.answer(sid,req);assertNotNull(result.getAnswerId());assertEquals("PROJECT_DISCUSSION",result.getInterviewStage());
        assertEquals(result,flow.answer(sid,req));
        assertEquals("PROJECT_DISCUSSION",flow.resume(sid).get("interviewStage"));
        var next=flow.next(sid);assertEquals("PROFESSIONAL_QUESTIONS",next.getInterviewStage());
        assertEquals(next.getQuestion().getQuestionInstanceId(),flow.next(sid).getQuestion().getQuestionInstanceId());
        assertThrows(BizException.class,()->flow.answer(sid,req));
        flow.finish(sid);flow.finish(sid);verify(reports,times(1)).enqueue(sid);
        assertEquals("COMPLETED",stages.stage(sid));assertEquals("FINISHED",flow.resume(sid).get("status"));
        assertEquals("USER_ENDED",jdbc.queryForObject("SELECT end_reason FROM interview_session_plan WHERE session_id=?",String.class,sid));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM interview_message WHERE session_id=? AND msg_type='SUMMARY'",Integer.class,sid));
        assertThrows(BizException.class,()->flow.answer(sid,request(next.getQuestion(),"迟到的回答")));
    }
    @Test void projectEvidenceMultipleFollowupsAndProfessionalTransitionArePreserved() throws Exception {
        when(ai.isUsable()).thenReturn(true);
        when(ai.chatJson(anyString(),anyString())).thenReturn(
                json.readTree("{\"hasProject\":true,\"evidence\":\"校园选课项目\",\"question\":\"你负责的选课模块如何处理并发？\"}"),
                json.readTree("{\"hasProject\":true,\"evidence\":\"校园选课项目\",\"question\":\"你如何验证方案的性能和稳定性？\"}"));
        when(followup.generate(any())).thenReturn(FollowUpResponse.of("为什么选择这个方案？","AI","test boundary"));
        when(gate.decide(anyLong(),anyLong(),anyInt(),anyInt(),anyInt(),anyString(),anyBoolean()))
                .thenAnswer(call -> call.<Integer>getArgument(2)<=3
                        ? com.zhimian.service.followup.GateDecision.ask(1,0)
                        : com.zhimian.service.followup.GateDecision.stop("STOP_DEPTH",0,0));
        var start=start();long sid=start.getSessionId();flow.answer(sid,introduction(start,"我参与开发了校园选课项目，负责后端模块。"));
        for(int index=0;index<2;index++){
            var project=flow.next(sid);assertEquals("PROJECT_DISCUSSION",project.getInterviewStage());
            var first=flow.answer(sid,request(project.getQuestion(),"我用事务和数据库唯一约束防止重复选课，并为异常请求提供回滚。"));
            assertEquals("FOLLOWUP",first.getNextAction());
            assertEquals(first.getQuestionInstanceId(),current(sid).getQuestionInstanceId());
            for(int layer=1;layer<=3;layer++) {
                var next=flow.answer(sid,request(current(sid),"约束能够保证并发情况下的数据一致性，后续再结合实际负载进行压力测试。"));
                assertEquals(layer<3?"FOLLOWUP":"NEXT",next.getNextAction());
            }
        }
        assertEquals("PROFESSIONAL_QUESTIONS",flow.next(sid).getInterviewStage());
        assertEquals(6,jdbc.queryForObject("SELECT COUNT(*) FROM interview_message WHERE session_id=? AND msg_type='FOLLOWUP'",Integer.class,sid));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM interview_message WHERE session_id=? AND msg_type='MAIN' AND question_type='SELF_INTRODUCTION'",Integer.class,sid));
    }
    @Test void silentAndForeignRecordingsCannotCompleteIntroduction(){
        var start=start();long sid=start.getSessionId();var req=request(start.getQuestion(),"我是软件专业学生。");
        var other=start();var foreign=introduction(other,"测试数据");req.setAudioId(foreign.getAudioId());
        assertThrows(BizException.class,()->flow.answer(sid,req));
        var silent=audio.save(sid,1,start.getQuestion().getId(),wav(true),1000,UserContext.getUserId());req.setAudioId(silent.getId());
        assertThrows(BizException.class,()->flow.answer(sid,req));assertEquals("SELF_INTRODUCTION",stages.stage(sid));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM interview_message WHERE session_id=? AND role='CANDIDATE'",Integer.class,sid));
    }
    @Test void referenceDurationDoesNotEndOrRejectAnIntroductionAndResumeIsReadOnly(){
        var start=start();long sid=start.getSessionId();
        jdbc.update("UPDATE interview_session SET start_time=DATE_SUB(NOW(),INTERVAL 590 SECOND) WHERE id=?",sid);
        sqlSession.clearCache();
        assertEquals("ONGOING",flow.resume(sid).get("status"));assertEquals("NEXT",flow.next(sid).getNextAction());
        jdbc.update("UPDATE interview_session SET start_time=DATE_SUB(NOW(),INTERVAL 601 SECOND) WHERE id=?",sid);
        sqlSession.clearCache();
        assertEquals("ONGOING",flow.resume(sid).get("status"));
        var accepted=flow.answer(sid,introduction(start,"我是软件专业学生，负责过接口开发。"));
        assertEquals("NEXT",accepted.getNextAction());
        assertEquals(true,accepted.getInterviewPlan().get("softLimitReached"));
        verify(reports,never()).enqueue(sid);
    }

    void stopFollowups() {
        when(gate.decide(anyLong(),anyLong(),anyInt(),anyInt(),anyInt(),anyString(),anyBoolean()))
                .thenReturn(com.zhimian.service.followup.GateDecision.stop("STOP_NO_NEED",0,0));
    }

    @Test void completingCoreQuestionsEndsEarlyAndThePlanSurvivesResume() {
        stopFollowups();var start=start();long sid=start.getSessionId();
        String frozen=jdbc.queryForObject("SELECT professional_question_ids FROM interview_session_plan WHERE session_id=?",String.class,sid);
        flow.answer(sid,introduction(start,"我是软件专业学生，主要学习后端开发。"));
        var step=flow.next(sid);
        assertEquals(0,step.getInterviewPlan().get("projectTarget"));
        assertEquals(4,step.getInterviewPlan().get("totalCoreQuestions"));
        for(int index=0;index<3;index++) {
            assertNotNull(step.getQuestion());
            var answered=flow.answer(sid,request(step.getQuestion(),"我会先检查需求和约束，再结合实际负载验证设计，并记录测试结果。"));
            assertEquals(index==2?"FINISHABLE":"NEXT",answered.getNextAction());
            if(index<2)step=flow.next(sid);
        }
        assertEquals("FINISHABLE",flow.next(sid).getNextAction());
        var resumed=flow.resume(sid);assertEquals("ONGOING",resumed.get("status"));
        var plan=(Map<?,?>)resumed.get("interviewPlan");
        assertEquals(4,plan.get("completedCoreQuestions"));assertEquals(true,plan.get("canFinish"));
        assertTrue((Integer)plan.get("elapsedSeconds")<600);
        assertEquals(frozen,jdbc.queryForObject("SELECT professional_question_ids FROM interview_session_plan WHERE session_id=?",String.class,sid));
        flow.finish(sid);flow.finish(sid);
        verify(reports,times(1)).enqueue(sid);
        assertEquals("PLAN_COMPLETED",jdbc.queryForObject("SELECT end_reason FROM interview_session_plan WHERE session_id=?",String.class,sid));
    }

    @Test void referenceTimeExtendsAndOnlyOptionalDeeperFollowupsAreTrimmed() {
        when(gate.decide(anyLong(),anyLong(),anyInt(),anyInt(),anyInt(),anyString(),anyBoolean()))
                .thenReturn(com.zhimian.service.followup.GateDecision.ask(1,0));
        when(followup.generate(any())).thenReturn(FollowUpResponse.of("请解释这个方案的边界。","AI","test"));
        var start=start();long sid=start.getSessionId();
        flow.answer(sid,introduction(start,"我是软件专业学生，主要学习后端开发。"));
        var question=flow.next(sid).getQuestion();
        jdbc.update("UPDATE interview_session SET start_time=DATE_SUB(NOW(),INTERVAL 650 SECOND) WHERE id=?",sid);sqlSession.clearCache();
        assertEquals("ONGOING",flow.resume(sid).get("status"));
        var first=flow.answer(sid,request(question,"我先收集指标，再分析调用链，最后通过压测验证方案是否有效。"));
        assertEquals("FOLLOWUP",first.getNextAction());assertEquals(1,first.getInterviewPlan().get("completedCoreQuestions"));
        assertTrue((Integer)first.getInterviewPlan().get("estimatedDurationSeconds")>650);
        var last=flow.answer(sid,request(current(sid),"出现超时时采用限流和降级，并监控错误率和耗时。"));
        assertEquals("NEXT",last.getNextAction());assertEquals(2,last.getInterviewPlan().get("completedCoreQuestions"));
        verify(followup,times(1)).generate(any());verify(reports,never()).enqueue(sid);
    }

    @Test void protectionAllowsCurrentAnswerGraceButNeverStartsAnotherQuestion() {
        var start=start();long sid=start.getSessionId();
        flow.answer(sid,introduction(start,"我是软件专业学生，主要学习后端开发。"));
        var question=flow.next(sid).getQuestion();
        jdbc.update("UPDATE interview_session SET start_time=DATE_SUB(NOW(),INTERVAL 1810 SECOND) WHERE id=?",sid);sqlSession.clearCache();
        var resumed=flow.resume(sid);assertEquals("ONGOING",resumed.get("status"));
        assertEquals(true,((Map<?,?>)resumed.get("interviewPlan")).get("hardLimitReached"));
        var accepted=flow.answer(sid,request(question,"这是保护上限后完成的当前题回答，应允许在宽限期内保存。"));
        assertEquals("FINISHABLE",accepted.getNextAction());
        assertEquals("FINISHABLE",flow.next(sid).getNextAction());
        flow.finish(sid);
        assertEquals("TIME_PROTECTION",jdbc.queryForObject("SELECT end_reason FROM interview_session_plan WHERE session_id=?",String.class,sid));
    }

    @Test void skippingIsRecordedAndPausedTimeDoesNotConsumeTheReferenceBudget() {
        stopFollowups();var start=start();long sid=start.getSessionId();
        flow.answer(sid,introduction(start,"我是软件专业学生，主要学习后端开发。"));
        var question=flow.next(sid).getQuestion();
        var skipped=request(question,"");skipped.setSkipped(true);
        var next=flow.answer(sid,skipped);assertEquals(1,next.getInterviewPlan().get("skippedCoreQuestions"));
        assertEquals(false,next.getInterviewPlan().get("canFinish"));
        jdbc.update("UPDATE interview_session SET start_time=DATE_SUB(NOW(),INTERVAL 650 SECOND) WHERE id=?",sid);
        jdbc.update("INSERT INTO interview_pause_state(session_id,paused_seconds) VALUES (?,600) ON DUPLICATE KEY UPDATE paused_seconds=600",sid);sqlSession.clearCache();
        var resumed=flow.resume(sid);var plan=(Map<?,?>)resumed.get("interviewPlan");
        assertEquals(false,plan.get("softLimitReached"));assertTrue((Integer)plan.get("elapsedSeconds")<60);
        verify(reports,never()).enqueue(sid);
        flow.finish(sid);
        long reportId=reporting.generateForSession(sessions.selectById(sid));
        var detail=reporting.getDetail(reportId);
        assertTrue(detail.getActualDurationSeconds()<60, "Report and interview clock must both exclude paused time");
        assertEquals("USER_ENDED",detail.getInterviewPlan().get("endReason"));
    }

    @Test void expiredProtectionGraceRejectsLateAnswersWithoutFinishingDuringResume() {
        var start=start();long sid=start.getSessionId();
        var request=introduction(start,"这是已经超过最终保护窗口的回答。");
        jdbc.update("UPDATE interview_session SET start_time=DATE_SUB(NOW(),INTERVAL 1930 SECOND) WHERE id=?",sid);sqlSession.clearCache();
        assertEquals("ONGOING",flow.resume(sid).get("status"));
        assertThrows(BizException.class,()->flow.answer(sid,request));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM interview_message WHERE session_id=? AND role='CANDIDATE'",Integer.class,sid));
        assertEquals("FINISHABLE",flow.next(sid).getNextAction());
        verify(reports,never()).enqueue(sid);
    }
    // Synthetic PCM verifies recording transport/validation only, not ASR or real voice analysis.
    static byte[] wav(boolean silent){
        int samples=16000;var b=ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36+samples*2).put("WAVEfmt ".getBytes()).putInt(16).putShort((short)1).putShort((short)1);
        b.putInt(16000).putInt(32000).putShort((short)2).putShort((short)16).put("data".getBytes()).putInt(samples*2);
        for(int i=0;i<samples;i++)b.putShort((short)(silent?0:10000*Math.sin(2*Math.PI*180*i/16000)));return b.array();
    }
}
