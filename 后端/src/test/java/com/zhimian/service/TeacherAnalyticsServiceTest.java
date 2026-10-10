package com.zhimian.service;

import com.zhimian.common.BizException;
import com.zhimian.config.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TeacherAnalyticsServiceTest {
    @AfterEach void clear() { UserContext.clear(); }

    @Test void scopedOverviewUsesOnlyValidRecordsAndFillsQuietDays() {
        UserContext.set(9L, "TEACHER");
        FakeTeaching teaching = new FakeTeaching();
        var service = new TeacherAnalyticsService(teaching);
        LocalDate to = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        var result = service.overview(new TeacherAnalyticsService.Filter(7L, "2026 秋", 3L,
                to.minusDays(2), to));
        assertEquals(12L, result.get("studentTotal"));
        assertEquals(5L, result.get("validAttemptTotal"));
        assertEquals(3L, result.get("trainedStudentCount"));
        assertEquals(1L, result.get("pendingReviewTotal"));
        assertEquals(3, ((List<?>) result.get("trend")).size());
        assertEquals(0L, ((Map<?, ?>) ((List<?>) result.get("trend")).get(0)).get("count"));
        assertTrue(teaching.calls.stream().filter(c -> c.sql.contains("teaching_attempt"))
                .allMatch(c -> c.sql.contains("c.teacher_id=?") && c.sql.contains("c.id=?") &&
                        c.sql.contains("c.semester=?") && c.sql.contains("t.job_id=?") &&
                        c.sql.contains("p.submitted_at>=?") && c.args[0].equals(9L)));
        assertTrue(teaching.calls.stream().anyMatch(c -> c.sql.contains("p.state='READY' AND p.valid=TRUE")));
        assertTrue(teaching.calls.stream().noneMatch(c -> c.sql.contains("2026 秋")),
                "User-supplied semester must stay a bound parameter");
    }

    @Test void recordsArePagedAndScopeIsRequired() {
        UserContext.set(9L, "TEACHER");
        FakeTeaching teaching = new FakeTeaching();
        var service = new TeacherAnalyticsService(teaching);
        var result = service.records(new TeacherAnalyticsService.Filter(null, null, null, null, null), 2, 20);
        assertEquals(2, result.get("page"));
        assertEquals(20, result.get("size"));
        assertEquals(27L, result.get("total"));
        Call page = teaching.calls.get(teaching.calls.size() - 1);
        assertTrue(page.sql.contains("LIMIT ? OFFSET ?"));
        assertFalse(page.sql.contains("interview_message"));
        assertFalse(page.sql.contains("user_answer"));
        assertEquals(20, page.args[page.args.length - 2]);
        assertEquals(20L, page.args[page.args.length - 1]);
        assertThrows(BizException.class, () -> service.records(
                new TeacherAnalyticsService.Filter(null, null, null, null, null), 1, 101));
        assertThrows(BizException.class, () -> service.overview(
                new TeacherAnalyticsService.Filter(null, null, null, LocalDate.now().minusDays(400), null)));

        UserContext.set(9L, "STUDENT");
        int before = teaching.calls.size();
        assertThrows(BizException.class, () -> service.overview(
                new TeacherAnalyticsService.Filter(null, null, null, null, null)));
        assertEquals(before, teaching.calls.size());
    }

    private record Call(String sql, Object[] args) {}
    private static class FakeTeaching extends TeachingService {
        final List<Call> calls = new ArrayList<>();
        FakeTeaching() { super(null, null, null); }
        @Override public List<Map<String, Object>> rows(String sql, Object... args) {
            assertTrue(sql.contains("c.teacher_id=?"));
            assertEquals(9L, args[0]);
            calls.add(new Call(sql, args));
            if (sql.contains("student_total")) return List.of(Map.of("studentTotal", 12L));
            if (sql.contains("valid_attempt_total")) return List.of(Map.of(
                    "validAttemptTotal", 5L, "trainedStudentCount", 3L, "averageScore", 81.5));
            if (sql.contains("pending_review_total")) return List.of(Map.of("pendingReviewTotal", 1L));
            if (sql.contains("COUNT(*) total")) return List.of(Map.of("total", 27L));
            return List.of();
        }
    }
}
