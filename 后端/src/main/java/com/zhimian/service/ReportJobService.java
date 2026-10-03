package com.zhimian.service;

import com.zhimian.common.BizException;
import com.zhimian.mapper.InterviewSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;

/** Database queue: commit submission before processing; one worker per application. */
@Service
@EnableScheduling
@DependsOn("studentBackendSchema")
@RequiredArgsConstructor
@Slf4j
public class ReportJobService {
    private final JdbcTemplate jdbc;
    private final InterviewSessionMapper sessions;
    private final ReportService reports;
    private final ModuleScoreService scores;
    private final TeachingService teaching;
    private final PlatformTransactionManager transactions;

    public void enqueue(long sessionId) {
        jdbc.update("INSERT IGNORE INTO student_report_job(session_id) VALUES (?)", sessionId);
    }

    public void retry(long sessionId) {
        // Reports submitted before the durable queue was introduced can also be retried.
        jdbc.update("INSERT IGNORE INTO student_report_job(session_id,state) VALUES (?,'FAILED')",sessionId);
        if (jdbc.update("UPDATE student_report_job SET state='QUEUED',report_id=NULL,updated_at=NOW() " +
                "WHERE session_id=? AND state='FAILED'", sessionId) != 1)
            throw new BizException("报告尚未失败或已在重试中，请刷新状态");
    }

    public Map<String,Object> state(long sessionId) {
        var states = jdbc.query("SELECT state,attempts,report_id FROM student_report_job WHERE session_id=?",
                (rs,n) -> Map.<String,Object>of("state", rs.getString(1), "attempts", rs.getInt(2),
                        "reportId", rs.getLong(3)), sessionId);
        if (states.isEmpty()) {
            var report = reports.getReportBySession(sessionId);
            if (report != null && report.getOverallMatchScore() != null)
                return Map.of("state", "READY", "reportId", report.getId());
            var session = sessions.selectById(sessionId);
            return Map.of("state", session != null && "ONGOING".equals(session.getStatus()) ? "ONGOING" : "FAILED",
                    "failureReason", "报告未完成，请重试");
        }
        var state = new java.util.LinkedHashMap<>(states.get(0));
        if (java.util.Set.of("QUEUED","RUNNING").contains(state.get("state"))) state.put("state","GENERATING");
        if ("FAILED".equals(state.get("state"))) state.put("failureReason","报告生成失败，请重试");
        return state;
    }

    /** Called while holding the session lock, prevents deletion racing a worker. */
    public void delete(long sessionId) {
        var states=jdbc.queryForList("SELECT state FROM student_report_job WHERE session_id=? FOR UPDATE",String.class,sessionId);
        if (states.contains("RUNNING")) throw new BizException("报告正在生成，请稍后删除");
        jdbc.update("DELETE FROM student_report_job WHERE session_id=?", sessionId);
    }

    @Scheduled(fixedDelayString="${student.report-worker-delay-ms:1000}")
    public void processNext() {
        // A crashed worker leaves RUNNING behind. Active generation holds the session lock;
        // its final update wins before this recovery can acquire that lock.
        var stale=jdbc.queryForList("SELECT session_id FROM student_report_job WHERE state='RUNNING' " +
                "AND updated_at<DATE_SUB(NOW(),INTERVAL 15 MINUTE) LIMIT 10",Long.class);
        for (long id:stale) new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            jdbc.queryForList("SELECT id FROM interview_session WHERE id=? FOR UPDATE",id);
            jdbc.update("UPDATE student_report_job SET state='QUEUED',updated_at=NOW() WHERE session_id=? " +
                    "AND state='RUNNING' AND updated_at<DATE_SUB(NOW(),INTERVAL 15 MINUTE)",id);
        });
        var pending=jdbc.queryForList("SELECT session_id FROM student_report_job WHERE state='QUEUED' ORDER BY updated_at LIMIT 1",Long.class);
        if(pending.isEmpty())return;
        long id=pending.get(0);
        if(jdbc.update("UPDATE student_report_job SET state='RUNNING',attempts=attempts+1,updated_at=NOW() " +
                "WHERE session_id=? AND state='QUEUED'",id)!=1)return;
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                var rows=jdbc.queryForList("SELECT id FROM interview_session WHERE id=? FOR UPDATE",id);
                if(rows.isEmpty())throw new BizException("训练会话已删除");
                var states=jdbc.queryForList("SELECT state FROM student_report_job WHERE session_id=? FOR UPDATE",String.class,id);
                if(!states.contains("RUNNING"))return;
                long reportId=reports.generateForSession(sessions.selectById(id));
                scores.scoreAndUpdateReport(id,reportId);
                teaching.ready(id,reportId);
                jdbc.update("UPDATE student_report_job SET state='READY',report_id=?,updated_at=NOW() WHERE session_id=?",reportId,id);
            });
        } catch(Exception failure) {
            jdbc.update("UPDATE student_report_job SET state='FAILED',updated_at=NOW() WHERE session_id=? AND state='RUNNING'",id);
            teaching.failed(id);
            log.error("Report generation failed sessionId={}",id,failure);
        }
    }
}
