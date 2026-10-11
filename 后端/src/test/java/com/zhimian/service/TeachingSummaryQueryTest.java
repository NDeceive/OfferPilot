package com.zhimian.service;

import com.zhimian.config.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TeachingSummaryQueryTest {
    @AfterEach void clear() { UserContext.clear(); }

    @Test void summaryIsScopedAndDoesNotLoadEveryAssignmentOrReport() {
        UserContext.set(8L, "TEACHER");
        FakeTeaching teaching = new FakeTeaching();
        Map<String, Object> result = teaching.summary();
        assertEquals(2L, result.get("studentTotal"));
        assertEquals(1L, result.get("completedTotal"));
        assertEquals(4, teaching.sql.size());
        assertTrue(teaching.sql.stream().allMatch(query -> query.contains("c.teacher_id=?")));
        assertTrue(teaching.sql.stream().noneMatch(query -> query.contains("SELECT *")));
        assertTrue(teaching.sql.stream().anyMatch(query -> query.contains("p.state='READY' AND p.valid=TRUE")));
        assertTrue(teaching.sql.stream().anyMatch(query -> query.contains("NOT EXISTS")));
    }

    private static class FakeTeaching extends TeachingService {
        final List<String> sql = new ArrayList<>();
        FakeTeaching() { super(null, null, null); }
        @Override public List<Map<String, Object>> rows(String query, Object... args) {
            assertEquals(8L, args[0]);
            sql.add(query);
            if (query.contains("student_total")) return List.of(Map.of("studentTotal", 2L));
            if (query.contains("task_total")) return List.of(Map.of("taskTotal", 3L));
            if (query.contains("assignment_total")) return List.of(Map.of(
                    "assignmentTotal", 2L, "completedTotal", 1L, "validAttemptTotal", 4L));
            return List.of(Map.of("pendingTotal", 1L));
        }
    }
}
