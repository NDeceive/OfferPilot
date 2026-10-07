package com.zhimian.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.BizException;
import com.zhimian.config.UserContext;
import com.zhimian.dto.ExpressionBatchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ExpressionService {
    public static final List<String> KEYS = List.of("neutral", "happy", "sad", "angry", "fearful", "disgusted", "surprised");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @Transactional
    public Map<String,Object> save(long sessionId, ExpressionBatchRequest request) {
        var sessions = jdbc.queryForList("SELECT user_id, UNIX_TIMESTAMP(start_time)*1000 AS start_ms, "
                + "UNIX_TIMESTAMP(end_time)*1000 AS end_ms FROM interview_session WHERE id=? FOR UPDATE", sessionId);
        if (sessions.isEmpty() || ((Number)sessions.get(0).get("user_id")).longValue() != UserContext.getUserId())
            throw new BizException("无权操作该会话");
        var session = sessions.get(0);
        long start = ((Number)session.get("start_ms")).longValue();
        long end = session.get("end_ms") == null ? System.currentTimeMillis() : ((Number)session.get("end_ms")).longValue();
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM interview_expression_sample WHERE session_id=?", Long.class, sessionId);
        var accepted = new ArrayList<String>();
        var rejected = new ArrayList<Map<String,String>>();
        for (var sample : request.samples()) {
          try {
            if (sample.capturedAt() < start - 5000 || sample.capturedAt() > end + 5000)
                throw new BizException("表情采样时间不属于该面试");
            if (jdbc.queryForObject("SELECT COUNT(*) FROM interview_message WHERE session_id=? AND question_id=? "
                    + "AND round_no=? AND role='INTERVIEWER'", Long.class, sessionId, sample.questionId(), sample.roundNo()) == 0)
                throw new BizException("表情采样题目不属于该面试轮次");
            validateProbabilities(sample.faceDetected(), sample.probabilities());
            boolean existing = jdbc.queryForObject("SELECT COUNT(*) FROM interview_expression_sample WHERE session_id=? AND sample_id=?",
                    Long.class, sessionId, sample.sampleId()) > 0;
            if (!existing && count >= 10000) throw new BizException("表情采样数量超出上限");
            try {
                String probabilities = sample.faceDetected() ? json.writeValueAsString(sample.probabilities()) : null;
                jdbc.update("INSERT INTO interview_expression_sample "
                        + "(session_id,sample_id,question_id,round_no,captured_at,face_detected,probabilities) VALUES (?,?,?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE sample_id=VALUES(sample_id)", sessionId, sample.sampleId(), sample.questionId(),
                        sample.roundNo(), sample.capturedAt(), sample.faceDetected(), probabilities);
                if (!existing) count++;
                accepted.add(sample.sampleId());
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new BizException("表情数据序列化失败");
            }
          } catch (BizException invalid) {
            rejected.add(Map.of("sampleId",sample.sampleId(),"reason",invalid.getMessage()));
          }
        }
        return Map.of("acceptedIds",accepted,"rejected",rejected);
    }

    public Map<String,Object> context(long sessionId) {
        var rows = jdbc.queryForList("SELECT user_id, UNIX_TIMESTAMP(start_time)*1000 AS started_at, status "
                + "FROM interview_session WHERE id=?",sessionId);
        if (rows.isEmpty() || ((Number)rows.get(0).get("user_id")).longValue() != UserContext.getUserId())
            throw new BizException("无权操作该会话");
        return Map.of("serverTime",System.currentTimeMillis(),"startedAt",rows.get(0).get("started_at"),"status",rows.get(0).get("status"));
    }

    static void validateProbabilities(boolean detected, Map<String, Double> probabilities) {
        if (!detected) {
            if (probabilities != null && !probabilities.isEmpty()) throw new BizException("未检测到人脸时不能提交概率");
            return;
        }
        if (probabilities == null || !probabilities.keySet().equals(new HashSet<>(KEYS)))
            throw new BizException("需要完整的七类表情概率");
        double sum = 0;
        for (Double value : probabilities.values()) {
            if (value == null || !Double.isFinite(value) || value < 0 || value > 1)
                throw new BizException("表情概率必须在 0 到 1 之间");
            sum += value;
        }
        if (Math.abs(sum - 1) > 0.02) throw new BizException("七类表情概率之和必须为 1");
    }

    // Called only after ReportService has checked owner/teacher report access.
    public Map<String, Object> report(long sessionId) {
        var timeline = jdbc.query("SELECT * FROM interview_expression_sample WHERE session_id=? ORDER BY captured_at,id",
                (rs, row) -> {
                    Map<String, Object> sample = new LinkedHashMap<>();
                    sample.put("sampleId", rs.getString("sample_id"));
                    sample.put("questionId", rs.getLong("question_id"));
                    sample.put("roundNo", rs.getInt("round_no"));
                    sample.put("capturedAt", rs.getLong("captured_at"));
                    sample.put("faceDetected", rs.getBoolean("face_detected"));
                    try {
                        sample.put("probabilities", rs.getBoolean("face_detected")
                                ? json.readValue(rs.getString("probabilities"), new TypeReference<Map<String, Double>>() {}) : null);
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        throw new IllegalStateException("Invalid stored expression probabilities", e);
                    }
                    return sample;
                }, sessionId);
        var questions = jdbc.queryForList("SELECT question_id AS questionId, round_no AS roundNo, content AS question, UNIX_TIMESTAMP(create_time)*1000 AS startedAt "
                + "FROM interview_message WHERE session_id=? AND role='INTERVIEWER' AND msg_type='MAIN' ORDER BY round_no,id", sessionId);
        Long endedAt = jdbc.queryForObject("SELECT UNIX_TIMESTAMP(COALESCE(end_time,NOW()))*1000 FROM interview_session WHERE id=?", Long.class, sessionId);
        for (int index=0; index<questions.size(); index++) {
            var question=questions.get(index);
            question.put("endedAt",index+1<questions.size()?questions.get(index+1).get("startedAt"):endedAt);
            int round = ((Number)question.get("roundNo")).intValue();
            question.put("summary", summarize(timeline.stream().filter(s -> ((Number)s.get("roundNo")).intValue() == round).toList()));
        }
        Long startedAt = jdbc.queryForObject("SELECT UNIX_TIMESTAMP(start_time)*1000 FROM interview_session WHERE id=?", Long.class, sessionId);
        return Map.of("timeline", timeline, "summary", summarize(timeline), "questions", questions,
                "sampleIntervalMs", 2000, "startedAt", startedAt == null ? 0 : startedAt,"endedAt",endedAt==null?0:endedAt);
    }

    static Map<String, Object> summarize(List<Map<String, Object>> samples) {
        Map<String, Double> averages = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        KEYS.forEach(k -> { averages.put(k, 0.0); counts.put(k, 0); });
        int detected = 0, uncertain = 0;
        for (var sample : samples) {
            if (!Boolean.TRUE.equals(sample.get("faceDetected"))) continue;
            @SuppressWarnings("unchecked") var probabilities = (Map<String, Double>)sample.get("probabilities");
            String dominant = KEYS.get(0);
            for (String key : KEYS) {
                averages.put(key, averages.get(key) + probabilities.get(key));
                if (probabilities.get(key) > probabilities.get(dominant)) dominant = key;
            }
            double runnerUp = 0;
            for (String key : KEYS) if (!key.equals(dominant)) runnerUp = Math.max(runnerUp, probabilities.get(key));
            if (probabilities.get(dominant) < .45 || probabilities.get(dominant) - runnerUp < .12) uncertain++;
            else counts.put(dominant, counts.get(dominant) + 1);
            detected++;
        }
        final int valid = detected;
        if (valid > 0) averages.replaceAll((key, value) -> value / valid);
        return Map.of("sampleCount", samples.size(), "detectedCount", detected, "missingCount", samples.size() - detected,
                "averageProbabilities", averages, "dominantCounts", counts,"uncertainCount",uncertain);
    }
}
