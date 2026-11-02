package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.entity.InterviewMessage;
import com.zhimian.entity.InterviewSession;
import com.zhimian.mapper.InterviewMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Session mutations run under InterviewFlowService's existing session lock. */
@Service
@RequiredArgsConstructor
public class InterviewPlanService {
    private static final ObjectMapper JSON = new ObjectMapper();
    public static final int PROFESSIONAL_TARGET = 3;
    public static final int ANSWER_GRACE_SECONDS = 120;
    private final JdbcTemplate jdbc;
    private final InterviewMessageMapper messages;

    public record Plan(List<Long> questionIds, int projectTarget, int estimatedSeconds,
                       int completed, int total, int hardLimitSeconds, String endReason) {}
    public record Counts(int completed, int skipped, int professional, int projects) {}

    public int elapsedSeconds(InterviewSession session) {
        var pauses=jdbc.queryForList("SELECT paused_seconds + IF(paused_at IS NULL,0,TIMESTAMPDIFF(SECOND,paused_at,NOW())) FROM interview_pause_state WHERE session_id=?",
                Long.class,session.getId());
        long paused=pauses.isEmpty()?0:pauses.get(0);
        var until=session.getEndTime()!=null?session.getEndTime():java.time.LocalDateTime.now();
        return (int)Math.max(0,java.time.Duration.between(session.getStartTime(),until).getSeconds()-paused);
    }

    public void start(InterviewSession session, List<Long> candidates) {
        var ids = candidates.stream().distinct().limit(PROFESSIONAL_TARGET).toList();
        try {
            jdbc.update("INSERT INTO interview_session_plan(session_id,professional_question_ids,estimated_duration_seconds,total_core,hard_limit_seconds) VALUES (?,?,?,?,?)",
                    session.getId(), JSON.writeValueAsString(ids), session.getDurationSeconds(), 3 + ids.size(),
                    Math.max(1800, session.getDurationSeconds() * 2));
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) { throw new IllegalStateException(failure); }
    }

    public Plan find(long sessionId) {
        return jdbc.query("SELECT * FROM interview_session_plan WHERE session_id=?", (rs, n) -> {
            try {
                return new Plan(JSON.readValue(rs.getString("professional_question_ids"), new TypeReference<List<Long>>() {}),
                        rs.getInt("project_target"), rs.getInt("estimated_duration_seconds"), rs.getInt("completed_core"),
                        rs.getInt("total_core"), rs.getInt("hard_limit_seconds"), rs.getString("end_reason"));
            } catch (com.fasterxml.jackson.core.JsonProcessingException failure) { throw new IllegalStateException(failure); }
        }, sessionId).stream().findFirst().orElse(null);
    }

    public Counts counts(long sessionId) {
        var history = messages.selectList(new LambdaQueryWrapper<InterviewMessage>()
                .eq(InterviewMessage::getSessionId, sessionId).orderByAsc(InterviewMessage::getId));
        return count(history);
    }

    static Counts count(List<InterviewMessage> history) {
        var mains = new LinkedHashMap<Integer, InterviewMessage>();
        var tails = new HashMap<Integer, InterviewMessage>();
        for (var message : history) {
            if ("SUMMARY".equals(message.getMsgType())) continue;
            if ("MAIN".equals(message.getMsgType()) && "INTERVIEWER".equals(message.getRole()))
                mains.put(message.getRoundNo(), message);
            tails.put(message.getRoundNo(), message);
        }
        int completed = 0, skipped = 0, professional = 0, projects = 0;
        for (var entry : mains.entrySet()) {
            var main = entry.getValue(); var tail = tails.get(entry.getKey());
            if (InterviewStageService.PROJECT.equals(main.getQuestionType())) projects++;
            if (InterviewStageService.PROFESSIONAL.equals(main.getQuestionType()) || "EXPERIENCE".equals(main.getQuestionType())) professional++;
            if (tail != null && "CANDIDATE".equals(tail.getRole())
                    && Set.of("ANSWER", "SKIPPED").contains(tail.getMsgType())) {
                completed++;
                if ("SKIPPED".equals(tail.getMsgType())) skipped++;
            }
        }
        return new Counts(completed, skipped, professional, projects);
    }

    public void resolveProjects(long sessionId) {
        var plan = find(sessionId);
        if (plan == null) return;
        int actual = counts(sessionId).projects();
        jdbc.update("UPDATE interview_session_plan SET project_target=? WHERE session_id=?", actual, sessionId);
    }

    /** Estimates change only at saved progress boundaries; reading/resuming never finishes a session. */
    public void updateEstimate(InterviewSession session, int elapsedSeconds) {
        var plan = find(session.getId());
        if (plan == null) return;
        var counts = counts(session.getId());
        int total = 1 + plan.projectTarget() + plan.questionIds().size();
        if (counts.completed() == plan.completed() && total == plan.total() && elapsedSeconds < plan.estimatedSeconds()) return;
        int estimated = estimate(session.getDurationSeconds(), elapsedSeconds, counts.completed(), total, plan.hardLimitSeconds());
        jdbc.update("UPDATE interview_session_plan SET estimated_duration_seconds=?,completed_core=?,total_core=? WHERE session_id=?",
                estimated, counts.completed(), total, session.getId());
    }

    static int estimate(int initialSeconds, int elapsedSeconds, int completed, int total, int hardLimit) {
        if (completed >= total) return elapsedSeconds;
        double prior = initialSeconds / (double)Math.max(1, total);
        double perQuestion = Math.max(45, (elapsedSeconds + 2 * prior) / (completed + 2));
        return Math.min(hardLimit, (int)Math.ceil(elapsedSeconds + (total - completed) * perQuestion));
    }

    public Map<String,Object> view(InterviewSession session, int elapsedSeconds) {
        var plan = find(session.getId());
        if (plan == null) return null;
        var counts = counts(session.getId());
        int total = 1 + plan.projectTarget() + plan.questionIds().size();
        var view = new LinkedHashMap<String,Object>();
        view.put("mode", "CORE_QUESTIONS");
        view.put("completedCoreQuestions", counts.completed());
        view.put("totalCoreQuestions", total);
        view.put("skippedCoreQuestions", counts.skipped());
        view.put("professionalTarget", plan.questionIds().size());
        view.put("projectTarget", plan.projectTarget());
        view.put("initialDurationSeconds", session.getDurationSeconds());
        view.put("estimatedDurationSeconds", plan.estimatedSeconds());
        view.put("elapsedSeconds", elapsedSeconds);
        view.put("softLimitReached", elapsedSeconds >= session.getDurationSeconds());
        view.put("hardLimitSeconds", plan.hardLimitSeconds());
        view.put("answerGraceSeconds", ANSWER_GRACE_SECONDS);
        view.put("hardLimitReached", elapsedSeconds >= plan.hardLimitSeconds());
        view.put("remainingSeconds", Math.max(0, plan.hardLimitSeconds() + ANSWER_GRACE_SECONDS - elapsedSeconds));
        view.put("canFinish", counts.completed() >= total);
        view.put("endReason", plan.endReason());
        return view;
    }

    public void end(long sessionId, String reason) {
        jdbc.update("UPDATE interview_session_plan SET end_reason=COALESCE(end_reason,?) WHERE session_id=?", reason, sessionId);
    }
}
