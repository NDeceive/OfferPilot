package com.zhimian.service;

import com.zhimian.config.UserContext;
import com.zhimian.entity.InterviewMessage;
import com.zhimian.entity.InterviewSession;
import com.zhimian.mapper.InterviewMessageMapper;
import com.zhimian.mapper.InterviewSessionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterviewCompletionTest {
    @Mock InterviewSessionMapper sessions;
    @Mock InterviewMessageMapper messages;
    @Mock JdbcTemplate jdbc;
    @Mock TeachingService teaching;
    @Mock ReportJobService reports;
    @Mock InterviewStageService stages;
    @Mock InterviewPlanService plans;
    @InjectMocks InterviewFlowService flow;

    private InterviewSession session() {
        UserContext.set(2L, "STUDENT");
        var session = new InterviewSession();
        session.setId(1L); session.setUserId(2L); session.setStatus("ONGOING");
        session.setDurationSeconds(900); session.setStartTime(LocalDateTime.now().minusSeconds(100));
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(1L))).thenReturn(List.of(2L));
        return session;
    }

    @AfterEach void clear() { UserContext.clear(); }

    @Test void legacySessionsRetainTheirLastThirtySecondsWithoutAQuestionQuota() throws Exception {
        var current = session();
        current.setStartTime(LocalDateTime.now().minusSeconds(880));
        var method = InterviewFlowService.class.getDeclaredMethod("hasTimeLeft", InterviewSession.class);
        method.setAccessible(true);
        assertEquals(true, method.invoke(flow, current));
        verifyNoInteractions(messages);
    }

    @Test void finishPersistsExactlyOneClosingAndEnqueuesOneReport() {
        var session = session();
        when(sessions.selectById(1L)).thenReturn(session);
        when(messages.selectList(any())).thenReturn(List.of());
        flow.finish(1L);
        flow.finish(1L);
        assertEquals("FINISHED", session.getStatus());
        verify(messages).insert(argThat((InterviewMessage m) -> "SUMMARY".equals(m.getMsgType())
                && InterviewPacing.CLOSING_TEXT.equals(m.getContent())));
        verify(reports).enqueue(1L);
    }
}
