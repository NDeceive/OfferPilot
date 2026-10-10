package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.ForbiddenException;
import com.zhimian.config.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TeachingResourceAccessTest {
    @AfterEach void clear() { UserContext.clear(); }

    @Test void anotherTeacherCannotReadOrEditClassAndTask() {
        UserContext.set(2L, "TEACHER");
        ScopedDb db = new ScopedDb();
        TeachingService service = new TeachingService(db, new ObjectMapper(), null);
        assertThrows(ForbiddenException.class, () -> service.members(10L));
        assertThrows(ForbiddenException.class, () -> service.editClass(10L,
                new TeachingService.ClassInput("新名称", "2026 秋")));
        assertThrows(ForbiddenException.class, () -> service.task(20L));
        assertEquals(0, db.writes.get());
        assertEquals(3, db.tenantReads.get());
    }

    private static class ScopedDb extends JdbcTemplate {
        final AtomicInteger writes = new AtomicInteger();
        final AtomicInteger tenantReads = new AtomicInteger();

        @Override public List<Map<String, Object>> queryForList(String sql, Object... args) {
            assertTrue(sql.contains("teacher_id=?"), "Resource lookup must include owner filter");
            assertEquals(2L, args[args.length - 1]);
            tenantReads.incrementAndGet();
            return List.of(); // Class 10 and task 20 belong to another teacher.
        }

        @Override public int update(String sql, Object... args) {
            writes.incrementAndGet();
            throw new AssertionError("Denied access must not write");
        }
    }
}
