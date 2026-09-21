package com.zhimian.service;

import com.zhimian.config.UserContext;
import com.zhimian.dto.ReportDetailResponse;
import com.zhimian.entity.InterviewReport;
import com.zhimian.entity.InterviewSession;
import com.zhimian.mapper.InterviewMessageMapper;
import com.zhimian.mapper.InterviewModuleScoreMapper;
import com.zhimian.mapper.InterviewReportMapper;
import com.zhimian.mapper.InterviewSessionMapper;
import com.zhimian.mapper.JobPositionMapper;
import com.zhimian.mapper.ReportDimensionMapper;
import com.zhimian.mapper.ScoreModuleMapper;
import com.zhimian.mapper.SkillQuestionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void reportDetailReturnsExactJobIdFromSession() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        ReportDimensionMapper dimensionMapper = mock(ReportDimensionMapper.class);
        InterviewModuleScoreMapper moduleScoreMapper = mock(InterviewModuleScoreMapper.class);

        InterviewReport report = new InterviewReport();
        report.setId(3L);
        report.setSessionId(8L);
        report.setUserId(21L);
        report.setStrengths("[]");
        report.setWeaknesses("[]");
        report.setSuggestions("[]");

        InterviewSession session = new InterviewSession();
        session.setId(8L);
        session.setJobId(42L);

        when(reportMapper.selectById(3L)).thenReturn(report);
        when(sessionMapper.selectById(8L)).thenReturn(session);
        when(dimensionMapper.selectList(any())).thenReturn(List.of());
        when(moduleScoreMapper.selectList(any())).thenReturn(List.of());
        UserContext.set(21L, "USER");

        ReportService service = new ReportService(
                sessionMapper,
                mock(InterviewMessageMapper.class),
                reportMapper,
                dimensionMapper,
                mock(SkillQuestionMapper.class),
                mock(JobPositionMapper.class),
                moduleScoreMapper,
                mock(ScoreModuleMapper.class));

        ReportDetailResponse detail = service.getDetail(3L);

        assertEquals(42L, detail.getJobId());
    }
}
