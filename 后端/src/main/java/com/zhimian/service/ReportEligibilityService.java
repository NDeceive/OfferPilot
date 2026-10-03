package com.zhimian.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.HashSet;
import java.util.Set;

/** Shared completion definition used by student statistics and dashboard. */
@Service
@DependsOn("studentBackendSchema")
@RequiredArgsConstructor
public class ReportEligibilityService {
    private final JdbcTemplate jdbc;

    public Set<Long> readySessions(long userId,boolean validOnly) {
        String valid=validOnly ? " AND (p.session_id IS NULL OR (p.state='READY' AND p.valid=1)) " +
                "AND EXISTS (SELECT 1 FROM interview_message m WHERE m.session_id=s.id AND m.role='CANDIDATE' " +
                "AND m.msg_type='ANSWER' AND LENGTH(TRIM(m.content))>0)" : "";
        return new HashSet<>(jdbc.queryForList("SELECT s.id FROM interview_session s " +
                "JOIN interview_report r ON r.session_id=s.id LEFT JOIN student_report_job j ON j.session_id=s.id " +
                "LEFT JOIN teaching_attempt p ON p.session_id=s.id WHERE s.user_id=? AND r.user_id=? " +
                "AND s.status='FINISHED' AND r.overall_match_score IS NOT NULL " +
                "AND (j.session_id IS NULL OR j.state='READY') AND (p.session_id IS NULL OR p.state IN ('READY','INVALID'))" +valid,Long.class,userId,userId));
    }
}
