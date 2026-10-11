package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhimian.config.UserContext;
import com.zhimian.entity.InterviewFollowupRecord;
import com.zhimian.entity.InterviewReport;
import com.zhimian.entity.InterviewSession;
import com.zhimian.entity.SysUser;
import com.zhimian.mapper.InterviewFollowupRecordMapper;
import com.zhimian.mapper.InterviewReportMapper;
import com.zhimian.mapper.InterviewSessionMapper;
import com.zhimian.mapper.JobPositionMapper;
import com.zhimian.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TeacherDashboardIsolationTest {
    @BeforeAll static void initTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "dashboard-test");
        TableInfoHelper.initTableInfo(assistant, InterviewReport.class);
        TableInfoHelper.initTableInfo(assistant, InterviewFollowupRecord.class);
    }

    @AfterEach void clear() { UserContext.clear(); }

    @Test void emptyTeacherNeverQueriesFollowupsOrInventsWeaknesses() {
        UserContext.set(1L, "TEACHER");
        AtomicInteger followupQueries = new AtomicInteger();
        InterviewFollowupRecordMapper followups = mapper(InterviewFollowupRecordMapper.class, (name, args) -> {
            followupQueries.incrementAndGet();
            throw new AssertionError("No sessions must not trigger a followup table scan");
        });
        var result = service(new FakeTeaching(1L, List.of(), List.of(), List.of()),
                List.of(), List.of(), List.of(), followups).getOverview();
        assertEquals(0, followupQueries.get());
        assertTrue(result.getWeaknessDistribution().isEmpty());
        assertTrue(result.getCommonProblems().isEmpty());
        assertEquals(0, result.getSummary().getStudentTotal());
    }

    @Test void twoTeachersSeeOnlyTheirOwnSessionsAndValidScores() {
        SysUser alice = student(10L, "Alice");
        SysUser bob = student(20L, "Bob");
        InterviewSession first = session(100L, 10L);
        InterviewSession second = session(200L, 20L);
        InterviewReport valid = report(1000L, 100L, 10L, 80);
        InterviewReport invalid = report(1001L, 100L, 10L, 20);
        InterviewReport other = report(2000L, 200L, 20L, 95);
        InterviewFollowupRecord project = followup(100L, "项目表达不清", "AI");
        InterviewFollowupRecord technology = followup(200L, "技术原理不清", "RULE");
        InterviewFollowupRecordMapper followups = mapper(InterviewFollowupRecordMapper.class,
                (name, args) -> filter((LambdaQueryWrapper<?>) args[0], List.of(project, technology),
                        InterviewFollowupRecord::getSessionId));

        UserContext.set(1L, "TEACHER");
        var teacherOne = service(new FakeTeaching(1L, List.of(Map.of("id", 10L)),
                        List.of(Map.of("sessionId", 100L)), List.of(Map.of("reportId", 1000L))),
                List.of(alice, bob), List.of(first, second), List.of(valid, invalid, other), followups).getOverview();
        assertEquals(80d, teacherOne.getSummary().getAverageScore());
        assertEquals(1L, teacherOne.getSummary().getAiFollowupCount());
        assertEquals(0L, teacherOne.getSummary().getRuleFollowupCount());
        assertEquals("项目表达笼统", teacherOne.getWeaknessDistribution().get(0).getName());
        assertEquals(1d, teacherOne.getWeaknessDistribution().get(0).getPercent());
        assertEquals(80d, teacherOne.getStudentTrainingList().get(0).getAverageScore());
        assertEquals(1, teacherOne.getCommonProblems().size());

        UserContext.set(2L, "TEACHER");
        var teacherTwo = service(new FakeTeaching(2L, List.of(Map.of("id", 20L)),
                        List.of(Map.of("sessionId", 200L)), List.of(Map.of("reportId", 2000L))),
                List.of(alice, bob), List.of(first, second), List.of(valid, invalid, other), followups).getOverview();
        assertEquals(95d, teacherTwo.getSummary().getAverageScore());
        assertEquals(0L, teacherTwo.getSummary().getAiFollowupCount());
        assertEquals(1L, teacherTwo.getSummary().getRuleFollowupCount());
        assertEquals("技术细节不清", teacherTwo.getWeaknessDistribution().stream()
                .filter(w -> w.getCount() > 0).findFirst().orElseThrow().getName());
    }

    private TeacherDashboardService service(FakeTeaching teaching, List<SysUser> users,
                                           List<InterviewSession> sessions, List<InterviewReport> reports,
                                           InterviewFollowupRecordMapper followups) {
        SysUserMapper userMapper = mapper(SysUserMapper.class, (name, args) -> byId((Collection<?>) args[0], users, SysUser::getId));
        InterviewSessionMapper sessionMapper = mapper(InterviewSessionMapper.class,
                (name, args) -> byId((Collection<?>) args[0], sessions, InterviewSession::getId));
        InterviewReportMapper reportMapper = mapper(InterviewReportMapper.class,
                (name, args) -> filter((LambdaQueryWrapper<?>) args[0], reports, InterviewReport::getSessionId));
        JobPositionMapper jobs = mapper(JobPositionMapper.class, (name, args) -> List.of());
        return new TeacherDashboardService(userMapper, sessionMapper, reportMapper, jobs, followups, teaching);
    }

    private static <T> List<T> byId(Collection<?> ids, List<T> items, java.util.function.Function<T, Long> id) {
        return items.stream().filter(item -> ids.contains(id.apply(item))).toList();
    }

    private static <T> List<T> filter(LambdaQueryWrapper<?> wrapper, List<T> items,
                                      java.util.function.Function<T, Long> sessionId) {
        assertTrue(wrapper.getSqlSegment().toUpperCase().contains("SESSION_ID IN"),
                "Followups and reports must be scoped to teacher session IDs");
        Collection<Object> scopedIds = wrapper.getParamNameValuePairs().values();
        return items.stream().filter(item -> scopedIds.contains(sessionId.apply(item))).toList();
    }

    @FunctionalInterface private interface Answer { Object apply(String method, Object[] args); }
    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, Answer answer) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> answer.apply(method.getName(), args));
    }

    private static SysUser student(long id, String name) {
        SysUser user = new SysUser(); user.setId(id); user.setNickname(name); return user;
    }
    private static InterviewSession session(long id, long userId) {
        InterviewSession s = new InterviewSession(); s.setId(id); s.setUserId(userId);
        s.setStartTime(LocalDateTime.now()); s.setStatus("FINISHED"); return s;
    }
    private static InterviewReport report(long id, long sessionId, long userId, int score) {
        InterviewReport r = new InterviewReport(); r.setId(id); r.setSessionId(sessionId);
        r.setUserId(userId); r.setTotalScore(BigDecimal.valueOf(score)); return r;
    }
    private static InterviewFollowupRecord followup(long sessionId, String reason, String source) {
        InterviewFollowupRecord r = new InterviewFollowupRecord(); r.setSessionId(sessionId);
        r.setTriggerReason(reason); r.setSource(source); return r;
    }

    private static class FakeTeaching extends TeachingService {
        private final long teacherId;
        private final List<Map<String, Object>> people;
        private final List<Map<String, Object>> sessions;
        private final List<Map<String, Object>> reports;

        FakeTeaching(long teacherId, List<Map<String, Object>> people,
                     List<Map<String, Object>> sessions, List<Map<String, Object>> reports) {
            super(null, null, null);
            this.teacherId = teacherId; this.people = people; this.sessions = sessions; this.reports = reports;
        }
        @Override public long user() { return teacherId; }
        @Override public List<Map<String, Object>> people() { return people; }
        @Override public List<Map<String, Object>> rows(String sql, Object... args) {
            assertTrue(sql.contains("c.teacher_id=?"), "Dashboard queries must filter by teacher");
            assertEquals(teacherId, args[0]);
            if (sql.contains("SELECT DISTINCT p.session_id")) return sessions;
            if (sql.contains("SELECT DISTINCT p.report_id")) return reports;
            return List.of();
        }
    }
}
