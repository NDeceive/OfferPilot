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
    private final TeachingService teaching;

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
        Map<Long,Map<String,Object>> teachingSessions=new HashMap<>();
        teaching.rows("SELECT p.session_id,p.state,a.id assignment_id,t.title task_title FROM teaching_attempt p JOIN teaching_assignment a ON a.id=p.assignment_id JOIN teaching_task t ON t.id=a.task_id WHERE a.student_id=?",userId).forEach(row->teachingSessions.put(TeachingService.id(row,"sessionId"),row));
        for (InterviewSession s : sessions) {
            InterviewRecord rec = new InterviewRecord();
            rec.setSessionId(s.getId());
            var origin=teachingSessions.get(s.getId());rec.setTrainingSource(origin==null?"SELF":"TEACHING");
            if(origin!=null){rec.setTaskTitle(String.valueOf(origin.get("taskTitle")));rec.setAssignmentId(TeachingService.id(origin,"assignmentId"));rec.setReportState(String.valueOf(origin.get("state")));}
            rec.setJobId(s.getJobId());
            rec.setDifficulty(s.getDifficulty());
            rec.setStatus(s.getStatus());
            rec.setStartTime(s.getStartTime());
            rec.setEndTime(s.getEndTime());
            rec.setDurationSeconds(s.getDurationSeconds());
            // 实际时长：已结束的用 endTime - startTime 计算
            if (s.getEndTime() != null && s.getStartTime() != null) {
                rec.setActualDurationSeconds(
                    java.time.Duration.between(s.getStartTime(), s.getEndTime()).toSeconds());
            }

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
