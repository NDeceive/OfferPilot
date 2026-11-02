package com.zhimian.service;

import com.zhimian.dto.*;
import com.zhimian.entity.*;
import com.zhimian.mapper.InterviewMessageMapper;
import com.zhimian.service.followup.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class InterviewFeatureParityTest {
    @Mock JdbcTemplate jdbc;
    @Mock InterviewStageService stages;
    @Mock InterviewPlanService plans;
    @Mock FollowupGate gate;
    @Mock InterviewFollowupChainService chains;
    @Mock FollowupDirectionPolicy directions;
    @Mock FollowUpService generator;
    @Mock InterviewSnapshotService snapshots;
    @Mock ExperienceQuestionService experiences;
    @Mock InterviewMessageMapper messages;
    @Mock com.zhimian.mapper.JobPositionMapper jobs;
    @InjectMocks InterviewFlowService flow;

    private InterviewSession session() {
        var session = new InterviewSession();session.setId(1L);session.setJobId(2L);
        session.setDurationSeconds(600);session.setStartTime(LocalDateTime.now());return session;
    }

    @Test void secondAndThirdFollowupsUseUpstreamGateInsteadOfSingleLayerOverride() throws Exception {
        var session=session();var parent=new InterviewMessage();parent.setId(8L);
        var method=InterviewFlowService.class.getDeclaredMethod("planFollowup",InterviewSession.class,Long.class,
                InterviewMessage.class,SkillQuestion.class,String.class,String.class,String.class,String.class,int.class,int.class);
        method.setAccessible(true);
        when(gate.decide(eq(1L),eq(8L),anyInt(),eq(2),anyInt(),anyString(),eq(false)))
                .thenAnswer(call -> call.<Integer>getArgument(2)<=3 ? GateDecision.ask(.5,.5) : GateDecision.stop(GateDecision.STOP_DEPTH,0,0));
        when(directions.choose(anyInt(),anySet())).thenReturn(FollowupDimension.MECHANISM);
        var job=new JobPosition();job.setName("后端开发");when(jobs.selectById(2L)).thenReturn(job);
        when(generator.generate(any())).thenReturn(FollowUpResponse.of("这个方案如何处理边界条件？","AI","test"));
        for(int asked=0;asked<4;asked++) {
            var plan=method.invoke(flow,session,1L,parent,null,"技术","我使用事务和唯一约束保证数据一致性。","主问题","参考",2,asked);
            var field=plan.getClass().getDeclaredField("ask");field.setAccessible(true);
            assertEquals(asked<3,field.get(plan));
        }
        verify(generator,times(3)).generate(any());
        verify(gate).decide(eq(1L),eq(8L),eq(4),eq(2),anyInt(),anyString(),eq(false));
    }

    @Test void experienceQuestionCarriesStageAndUniquePromptIdForAnswerValidation() throws Exception {
        var session=session();var job=new JobPosition();job.setName("后端开发");
        when(snapshots.job(1L,2L)).thenReturn(job);
        when(stages.stage(1L)).thenReturn(InterviewStageService.PROFESSIONAL);
        when(experiences.generate(anyString(),isNull(),anyList(),anyList())).thenReturn(
                new ExperienceQuestionService.ExperienceQuestionResult("如何排查服务超时？","分析调用链","技术诊断"));
        doAnswer(call->{call.<InterviewMessage>getArgument(0).setId(501L);return 1;}).when(messages).insert(any(InterviewMessage.class));
        var method=InterviewFlowService.class.getDeclaredMethod("buildExperienceQuestionStep",InterviewSession.class,int.class);
        method.setAccessible(true);var step=(InterviewStep)method.invoke(flow,session,7);
        assertNotNull(step);assertEquals(0L,step.getQuestion().getId());
        assertEquals(501L,step.getQuestion().getQuestionInstanceId());
        assertEquals("EXPERIENCE",step.getQuestion().getQuestionType());
        assertEquals(InterviewStageService.PROFESSIONAL,step.getInterviewStage());
    }
}
