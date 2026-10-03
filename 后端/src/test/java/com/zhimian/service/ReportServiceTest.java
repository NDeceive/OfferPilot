package com.zhimian.service;

import com.zhimian.config.UserContext;
import com.zhimian.dto.ReportDetailResponse;
import com.zhimian.entity.InterviewReport;
import com.zhimian.entity.InterviewSession;
import com.zhimian.entity.InterviewMessage;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    @Test
    void historicalUnansweredReportDoesNotClaimCompletedAnswersOrWeakAbilities() {
        var sessions = mock(InterviewSessionMapper.class);
        var messages = mock(InterviewMessageMapper.class);
        var reports = mock(InterviewReportMapper.class);
        var dimensions = mock(ReportDimensionMapper.class);
        var modules = mock(InterviewModuleScoreMapper.class);
        var report = new InterviewReport();
        report.setId(68L);report.setSessionId(8L);report.setUserId(21L);
        report.setSummary("专业知识突出，专业知识最薄弱");
        report.setStrengths("[\"完成全部主问题\"]");
        report.setWeaknesses("[\"专业知识薄弱\"]");report.setSuggestions("[]");
        var skipped = new InterviewMessage();
        skipped.setRole("CANDIDATE");skipped.setMsgType("SKIPPED");skipped.setContent("跳过");
        when(reports.selectById(68L)).thenReturn(report);
        when(messages.selectList(any())).thenReturn(List.of(skipped));
        when(dimensions.selectList(any())).thenReturn(List.of());
        when(modules.selectList(any())).thenReturn(List.of());
        UserContext.set(21L, "USER");
        var service = new ReportService(mock(TeachingService.class),mock(InterviewSnapshotService.class),sessions,
                messages,reports,dimensions,mock(SkillQuestionMapper.class),mock(JobPositionMapper.class),modules,mock(ScoreModuleMapper.class));
        var detail = service.getDetail(68L);
        assertTrue(detail.getSummary().contains("未形成可评价回答"));
        assertEquals(List.of(),detail.getStrengths());
        assertEquals(List.of(),detail.getWeaknesses());
        assertEquals("专业知识突出，专业知识最薄弱",report.getSummary(),"Stored raw report is preserved");
        var answer = new InterviewMessage();answer.setRole("CANDIDATE");answer.setMsgType("ANSWER");answer.setContent("我负责实现接口。");
        when(messages.selectList(any())).thenReturn(List.of(answer));
        assertEquals(report.getSummary(),service.getDetail(68L).getSummary(),"Real low-score answers retain their evidence");
    }

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
                mock(TeachingService.class),
                mock(InterviewSnapshotService.class),
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
