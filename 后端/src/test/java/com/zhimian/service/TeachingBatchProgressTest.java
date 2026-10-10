package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.config.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TeachingBatchProgressTest {
    @AfterEach void clear() { UserContext.clear(); }

    @Test void taskAssignmentsUseBatchQueriesInsteadOfPerStudentQueries() {
        UserContext.set(8L, "TEACHER");
        FakeTeaching service = new FakeTeaching();
        var assignments = service.assignmentsForTask(5L);
        assertEquals(2, assignments.size());
        assertEquals("COMPLETED", assignments.get(0).get("completionStatus"));
        assertEquals("NOT_STARTED", assignments.get(1).get("completionStatus"));
        assertEquals(1L, assignments.get(0).get("validCount"));
        assertEquals(5, service.queries.size(), "owner lookup + four batched reads");
        assertTrue(service.queries.stream().noneMatch(q -> q.contains("WHERE assignment_id=?")));
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }

    private static class FakeTeaching extends TeachingService {
        final List<String> queries = new ArrayList<>();
        FakeTeaching() { super(null, new ObjectMapper(), null); }
        @Override public List<Map<String, Object>> rows(String sql, Object... args) {
            queries.add(sql);
            if (sql.contains("WHERE t.id=? AND c.teacher_id=?")) {
                assertEquals(8L, args[1]);
                return List.of(row("id", 5L, "classId", 6L, "minAttempts", 1L,
                        "maxAttempts", 3L, "startTime", LocalDateTime.now().minusDays(1),
                        "deadline", LocalDateTime.now().plusDays(1), "archived", false,
                        "allowLate", false, "endedAt", null));
            }
            if (sql.contains("FROM teaching_assignment a JOIN teaching_task t") && sql.contains("JOIN sys_user u")) {
                assertEquals(8L, args[0]);
                return List.of(row("id", 10L, "taskId", 5L, "studentId", 1L),
                        row("id", 11L, "taskId", 5L, "studentId", 2L));
            }
            if (sql.contains("FROM teaching_attempt WHERE assignment_id IN"))
                return List.of(row("id", 99L, "assignmentId", 10L, "state", "READY", "valid", true));
            if (sql.contains("FROM teaching_assignment_override")) return List.of();
            if (sql.contains("LEFT JOIN teaching_member"))
                return List.of(row("assignmentId", 10L, "state", "JOINED"),
                        row("assignmentId", 11L, "state", "JOINED"));
            throw new AssertionError("Unexpected query: " + sql);
        }
    }
}
