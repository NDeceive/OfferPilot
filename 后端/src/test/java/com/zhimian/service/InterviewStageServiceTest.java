package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.entity.*;
import com.zhimian.service.ai.DeepSeekClient;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class InterviewStageServiceTest {
    @Test void fabricatedProjectEvidenceIsRejectedAndQuotaCannotOverflow() throws Exception {
        var jdbc=mock(JdbcTemplate.class);var snapshots=mock(InterviewSnapshotService.class);var ai=mock(DeepSeekClient.class);
        var service=new InterviewStageService(jdbc,snapshots,ai);var session=new InterviewSession();session.setId(1L);session.setJobId(2L);
        var job=new JobPosition();job.setName("Java工程师");when(snapshots.job(1L,2L)).thenReturn(job);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(1L))).thenReturn(0);
        when(ai.isUsable()).thenReturn(true);
        when(ai.chatJson(anyString(),anyString())).thenReturn(new ObjectMapper().readTree("{\"hasProject\":true,\"evidence\":\"虚构商城项目\",\"question\":\"怎样设计订单？\"}"));
        assertNull(service.projectQuestion(session,List.of()));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(1L))).thenReturn(2);
        assertNull(service.projectQuestion(session,List.of()));
        verify(ai,times(1)).chatJson(anyString(),anyString());
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}
