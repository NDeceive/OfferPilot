package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhimian.config.UserContext;
import com.zhimian.dto.InterviewRecord;
import com.zhimian.entity.InterviewReport;
import com.zhimian.entity.InterviewSession;
import com.zhimian.entity.JobPosition;
import com.zhimian.mapper.InterviewReportMapper;
import com.zhimian.mapper.InterviewSessionMapper;
import com.zhimian.mapper.JobPositionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 面试记录服务：组合会话、报告、岗位名，返回真实记录列表。
 * 当前用户没有面试时返回空列表（不造假数据）。
 */
@Service
@RequiredArgsConstructor
public class InterviewRecordService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewReportMapper reportMapper;
    private final JobPositionMapper jobMapper;

    public List<InterviewRecord> myRecords() {
        Long userId = UserContext.getUserId();

        List<InterviewSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, userId)
                        .orderByDesc(InterviewSession::getStartTime));
        if (sessions.isEmpty()) {
            return new ArrayList<>();
        }

        // 预取报告（sessionId -> report）
        List<InterviewReport> reports = reportMapper.selectList(
                new LambdaQueryWrapper<InterviewReport>().eq(InterviewReport::getUserId, userId));
        Map<Long, InterviewReport> reportBySession = new HashMap<>();
        for (InterviewReport r : reports) {
            reportBySession.put(r.getSessionId(), r);
        }

        List<InterviewRecord> records = new ArrayList<>();
        for (InterviewSession s : sessions) {
            InterviewRecord rec = new InterviewRecord();
            rec.setSessionId(s.getId());
            rec.setJobId(s.getJobId());
            rec.setDifficulty(s.getDifficulty());
            rec.setStatus(s.getStatus());
            rec.setStartTime(s.getStartTime());
            rec.setEndTime(s.getEndTime());

            JobPosition job = jobMapper.selectById(s.getJobId());
            rec.setJobName(job != null ? job.getName() : "未知岗位");

            InterviewReport report = reportBySession.get(s.getId());
            if (report != null) {
                rec.setReportId(report.getId());
                rec.setTotalScore(report.getTotalScore() != null ? report.getTotalScore().doubleValue() : null);
            }
            records.add(rec);
        }
        return records;
    }
}
