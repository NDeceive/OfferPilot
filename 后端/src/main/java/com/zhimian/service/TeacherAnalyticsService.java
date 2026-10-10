package com.zhimian.service;

import com.zhimian.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Current teacher's real teaching activity. No candidate answer text is returned. */
@Service
@RequiredArgsConstructor
public class TeacherAnalyticsService {
    private final TeachingService teaching;

    public record Filter(Long classId, String semester, Long jobId, LocalDate from, LocalDate to) {}

    private record Scope(String clause, List<Object> args, LocalDate from, LocalDate to) {}

    private Scope scope(Filter filter) {
        teaching.teacher();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        LocalDate to = filter.to() == null ? today : filter.to();
        LocalDate from = filter.from() == null ? to.minusDays(29) : filter.from();
        if (from.isAfter(to) || to.isAfter(today) || from.isBefore(to.minusDays(365))) {
            throw new BizException("统计日期范围须在过去366天内，且开始日期不晚于结束日期");
        }
        if (filter.classId() != null && filter.classId() <= 0 ||
                filter.jobId() != null && filter.jobId() <= 0) {
            throw new BizException("班级或岗位编号无效");
        }
        String semester = filter.semester() == null ? "" : filter.semester().trim();
        if (semester.length() > 80) throw new BizException("学期名称过长");
        StringBuilder clause = new StringBuilder("c.teacher_id=?");
        List<Object> args = new ArrayList<>();
        args.add(teaching.user());
        if (filter.classId() != null) { clause.append(" AND c.id=?"); args.add(filter.classId()); }
        if (!semester.isEmpty()) { clause.append(" AND c.semester=?"); args.add(semester); }
        if (filter.jobId() != null) { clause.append(" AND t.job_id=?"); args.add(filter.jobId()); }
        clause.append(" AND p.submitted_at>=? AND p.submitted_at<?");
        args.add(from.atStartOfDay());
        args.add(to.plusDays(1).atStartOfDay());
        return new Scope(clause.toString(), args, from, to);
    }

    private static final String ATTEMPTS = " FROM teaching_attempt p " +
            "JOIN teaching_assignment a ON a.id=p.assignment_id " +
            "JOIN teaching_task t ON t.id=a.task_id " +
            "JOIN teaching_class c ON c.id=t.class_id ";
    private static final String VALID = " AND p.state='READY' AND p.valid=TRUE AND p.report_id IS NOT NULL";

    public Map<String, Object> overview(Filter filter) {
        Scope scope = scope(filter);
        // Denominator is the current joined roster, independent of job and date filters.
        List<Object> rosterArgs = new ArrayList<>();
        rosterArgs.add(teaching.user());
        StringBuilder rosterWhere = new StringBuilder("c.teacher_id=? AND m.state='JOINED'");
        if (filter.classId() != null) { rosterWhere.append(" AND c.id=?"); rosterArgs.add(filter.classId()); }
        if (filter.semester() != null && !filter.semester().isBlank()) {
            rosterWhere.append(" AND c.semester=?"); rosterArgs.add(filter.semester().trim());
        }
        var roster = teaching.rows("SELECT COUNT(DISTINCT m.student_id) student_total FROM teaching_member m " +
                "JOIN teaching_class c ON c.id=m.class_id WHERE " + rosterWhere, rosterArgs.toArray()).get(0);
        long studentTotal = number(roster, "studentTotal");

        var valid = teaching.rows("SELECT COUNT(*) valid_attempt_total, " +
                "COUNT(DISTINCT CASE WHEN m.state='JOINED' THEN a.student_id END) trained_student_count, " +
                "AVG(r.total_score) average_score" + ATTEMPTS +
                "LEFT JOIN teaching_member m ON m.class_id=c.id AND m.student_id=a.student_id " +
                "LEFT JOIN interview_report r ON r.id=p.report_id WHERE " + scope.clause + VALID,
                scope.args.toArray()).get(0);
        long validTotal = number(valid, "validAttemptTotal");
        long trained = number(valid, "trainedStudentCount");

        var pending = teaching.rows("SELECT COUNT(DISTINCT p.report_id) pending_review_total" + ATTEMPTS +
                "WHERE " + scope.clause + " AND p.report_id IS NOT NULL " +
                "AND NOT EXISTS (SELECT 1 FROM teaching_review rev WHERE rev.report_id=p.report_id)",
                scope.args.toArray()).get(0);

        Map<String, Map<String, Object>> trend = new LinkedHashMap<>();
        for (LocalDate date = scope.from; !date.isAfter(scope.to); date = date.plusDays(1)) {
            trend.put(date.toString(), Map.of("date", date.toString(), "count", 0L, "active", 0L));
        }
        for (var row : teaching.rows("SELECT DATE_FORMAT(p.submitted_at,'%Y-%m-%d') date, " +
                "COUNT(*) valid_count, COUNT(DISTINCT a.student_id) active" + ATTEMPTS +
                "WHERE " + scope.clause + VALID +
                " GROUP BY DATE_FORMAT(p.submitted_at,'%Y-%m-%d') ORDER BY date",
                scope.args.toArray())) {
            String date = String.valueOf(row.get("date"));
            if (trend.containsKey(date)) trend.put(date, Map.of("date", date,
                    "count", number(row, "validCount"), "active", number(row, "active")));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", scope.from.toString());
        result.put("to", scope.to.toString());
        result.put("studentTotal", studentTotal);
        result.put("validAttemptTotal", validTotal);
        result.put("trainedStudentCount", trained);
        result.put("participationRate", studentTotal == 0 ? null : Math.round(trained * 10000.0 / studentTotal) / 10000.0);
        result.put("averageScore", valid.get("averageScore"));
        result.put("pendingReviewTotal", number(pending, "pendingReviewTotal"));
        result.put("trend", List.copyOf(trend.values()));
        return result;
    }

    public Map<String, Object> records(Filter filter, int page, int size) {
        Scope scope = scope(filter);
        if (page < 1 || size < 1 || size > 100 || (long) (page - 1) * size > Integer.MAX_VALUE) {
            throw new BizException("分页参数无效：每页最多100条");
        }
        String where = " WHERE " + scope.clause + " AND p.report_id IS NOT NULL";
        long total = number(teaching.rows("SELECT COUNT(*) total" + ATTEMPTS + where,
                scope.args.toArray()).get(0), "total");
        List<Object> args = new ArrayList<>(scope.args);
        args.add(size);
        args.add((long) (page - 1) * size);
        var items = teaching.rows("SELECT p.id,p.session_id,p.report_id,p.state,p.valid,p.submitted_at," +
                "a.student_id,t.id task_id,t.title task_title,c.id class_id,c.name class_name," +
                "t.job_id,j.name job_name,COALESCE(NULLIF(u.nickname,''),u.username) student_name" +
                ATTEMPTS + "JOIN sys_user u ON u.id=a.student_id JOIN job_position j ON j.id=t.job_id" + where +
                " ORDER BY p.submitted_at DESC,p.id DESC LIMIT ? OFFSET ?", args.toArray());
        return Map.of("page", page, "size", size, "total", total, "items", items);
    }

    private static long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
