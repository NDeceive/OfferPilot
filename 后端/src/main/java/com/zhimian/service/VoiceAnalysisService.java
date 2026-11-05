package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhimian.config.VoiceProperties;
import com.zhimian.entity.InterviewAnswerAudio;
import com.zhimian.entity.InterviewVoiceAnalysis;
import com.zhimian.entity.InterviewVoiceSession;
import com.zhimian.mapper.InterviewVoiceAnalysisMapper;
import com.zhimian.mapper.InterviewVoiceSessionMapper;
import com.zhimian.service.voice.VoiceAnalysisClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报告生成前的批处理语音分析。
 * <p>
 * 任何异常都只影响语音这一块，不能拖垮报告。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceAnalysisService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final VoiceProperties props;
    private final VoiceAnalysisClient client;
    private final AnswerAudioService audioService;
    private final InterviewVoiceSessionMapper sessionMapper;
    private final InterviewVoiceAnalysisMapper analysisMapper;

    /** 开始面试时登记开关 */
    public void markEnabled(Long sessionId, boolean enabled) {
        InterviewVoiceSession row = sessionMapper.selectById(sessionId);
        if (row == null) {
            row = new InterviewVoiceSession();
            row.setSessionId(sessionId);
            row.setEnabled(enabled ? 1 : 0);
            row.setStatus(enabled ? "PENDING" : "DISABLED");
            sessionMapper.insert(row);
        } else {
            row.setEnabled(enabled ? 1 : 0);
            row.setStatus(enabled ? "PENDING" : "DISABLED");
            sessionMapper.updateById(row);
        }
    }

    public InterviewVoiceSession sessionState(Long sessionId) {
        return sessionMapper.selectById(sessionId);
    }

    public List<InterviewVoiceAnalysis> results(Long sessionId) {
        return analysisMapper.selectList(new LambdaQueryWrapper<InterviewVoiceAnalysis>()
                .eq(InterviewVoiceAnalysis::getSessionId, sessionId)
                .orderByAsc(InterviewVoiceAnalysis::getRoundNo)).stream()
                .filter(row -> {
                    try {
                        JsonNode metrics = JSON.readTree(row.getMetricsJson());
                        if (!validMetrics(metrics)) return false;
                        if (!number(metrics, "f0_std")) row.setConfidenceEst(null);
                        return true;
                    }
                    catch (Exception invalid) { return false; }
                }).toList();
    }

    private void setStatus(Long sessionId, String status, String reason) {
        InterviewVoiceSession row = sessionMapper.selectById(sessionId);
        if (row == null) {
            row = new InterviewVoiceSession();
            row.setSessionId(sessionId);
            row.setEnabled(0);
            row.setStatus(status);
            row.setFailureReason(reason);
            sessionMapper.insert(row);
        } else {
            row.setStatus(status);
            row.setFailureReason(reason);
            sessionMapper.updateById(row);
        }
    }

    /** 返回会话级状态，供报告与评分使用 */
    public String analyzeSession(Long sessionId) {
        InterviewVoiceSession state = sessionMapper.selectById(sessionId);
        if (state != null && state.getEnabled() != null && state.getEnabled() == 0) {
            setStatus(sessionId, "DISABLED", null);
            return "DISABLED";
        }
        if (!props.isEnabled()) {
            setStatus(sessionId, "FAILED", "service_disabled");
            return "FAILED";
        }

        List<InterviewAnswerAudio> audios = audioService.listBySession(sessionId);
        List<InterviewAnswerAudio> usable = new ArrayList<>();
        for (InterviewAnswerAudio a : audios) {
            if ("OK".equals(a.getStatus()) && a.getByteSize() != null && a.getByteSize() > 0) usable.add(a);
        }
        if (usable.isEmpty()) {
            setStatus(sessionId, "FAILED", audios.isEmpty() ? "mic_denied" : "empty_audio");
            return "FAILED";
        }

        // 同一题可能有多段作答（主回答 + 多次追问），按题聚合后再落一行分析。
        Map<Integer, List<JsonNode>> metricsByRound = new LinkedHashMap<>();
        for (InterviewAnswerAudio audio : usable) {
            try {
                Path path = audioService.pathOf(audio).toAbsolutePath();
                JsonNode m = client.analyze(path.toString());
                if (!validMetrics(m)) throw new IllegalArgumentException("empty_or_invalid_audio_metrics");
                metricsByRound.computeIfAbsent(audio.getRoundNo(), key -> new ArrayList<>()).add(m);
            } catch (Exception e) {
                log.warn("[语音分析] 单段失败 sessionId={}, round={}, seq={}, err={}",
                        sessionId, audio.getRoundNo(), audio.getAnswerSeq(), e.getMessage());
            }
        }

        int ok = 0;
        for (Map.Entry<Integer, List<JsonNode>> entry : metricsByRound.entrySet()) {
            try {
                JsonNode m = aggregate(entry.getValue());
                InterviewVoiceAnalysis row = new InterviewVoiceAnalysis();
                row.setSessionId(sessionId);
                row.setRoundNo(entry.getKey());
                row.setMetricsJson(m.toString());
                row.setFluency(bd(fluency(m)));
                row.setVolumeScore(bd(volume(m)));
                // Pitch is optional; an unavailable measurement must never become a default score.
                row.setConfidenceEst(number(m, "f0_std") ? bd(confidence(m)) : null);
                InterviewVoiceAnalysis exists = findOne(sessionId, entry.getKey());
                if (exists != null) {
                    row.setId(exists.getId());
                    analysisMapper.updateById(row);
                } else {
                    analysisMapper.insert(row);
                }
                ok++;
            } catch (Exception e) {
                log.warn("[语音分析] 单题聚合失败 sessionId={}, round={}, err={}",
                        sessionId, entry.getKey(), e.getMessage());
            }
        }

        if (ok == 0) {
            setStatus(sessionId, "FAILED", "service_error");
            return "FAILED";
        }
        setStatus(sessionId, "ANALYZED", null);
        return "ANALYZED";
    }

    private InterviewVoiceAnalysis findOne(Long sessionId, int roundNo) {
        List<InterviewVoiceAnalysis> rows = analysisMapper.selectList(new LambdaQueryWrapper<InterviewVoiceAnalysis>()
                .eq(InterviewVoiceAnalysis::getSessionId, sessionId)
                .eq(InterviewVoiceAnalysis::getRoundNo, roundNo)
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /* ---------------- 指标 -> 分数 ---------------- */

    static boolean validMetrics(JsonNode m) {
        if (m == null || !m.isObject() || !m.path("empty").isBoolean() || m.path("empty").asBoolean()) return false;
        for (String key : List.of("duration", "speech_ratio", "pause_count", "longest_pause",
                "rms_mean_db", "rms_std_db", "frame_count")) {
            if (!number(m, key)) return false;
        }
        double duration = m.path("duration").asDouble();
        return duration > 0.2 && m.path("speech_ratio").asDouble() > 0
                && m.path("speech_ratio").asDouble() <= 1
                && m.path("pause_count").asDouble() >= 0
                && m.path("pause_count").asDouble() == Math.floor(m.path("pause_count").asDouble())
                && m.path("longest_pause").asDouble() >= 0
                && m.path("longest_pause").asDouble() <= duration + 0.05
                && m.path("rms_std_db").asDouble() >= 0 && m.path("frame_count").asDouble() > 0
                && (!m.has("f0_std") || (number(m, "f0_std") && m.path("f0_std").asDouble() >= 0));
    }

    private static boolean number(JsonNode m, String key) {
        return m != null && m.path(key).isNumber() && Double.isFinite(m.path(key).doubleValue());
    }

    private static double metric(JsonNode m, String key) {
        if (m == null || m.path("empty").asBoolean() || !number(m, key))
            throw new IllegalArgumentException("Missing or invalid audio metric: " + key);
        return m.path(key).doubleValue();
    }

    /**
     * 把同一题的多段作答合并成一份指标：时长、顿数、帧数相加，
     * 比率/均值按时长加权，最长停顿取最大值；只有所有分段都有音高时才保留 f0_std。
     */
    static JsonNode aggregate(List<JsonNode> segments) {
        if (segments == null || segments.isEmpty())
            throw new IllegalArgumentException("no_audio_metrics");
        if (segments.size() == 1) return segments.get(0);
        double duration = 0;
        double speechRatio = 0;
        double pauses = 0;
        double longestPause = 0;
        double rmsMean = 0;
        double rmsStd = 0;
        double frames = 0;
        double f0 = 0;
        boolean hasPitch = true;
        for (JsonNode m : segments) {
            if (!validMetrics(m)) throw new IllegalArgumentException("invalid_audio_metrics");
            double d = metric(m, "duration");
            duration += d;
            speechRatio += metric(m, "speech_ratio") * d;
            pauses += metric(m, "pause_count");
            longestPause = Math.max(longestPause, metric(m, "longest_pause"));
            rmsMean += metric(m, "rms_mean_db") * d;
            rmsStd += metric(m, "rms_std_db") * d;
            frames += metric(m, "frame_count");
            if (number(m, "f0_std")) f0 += m.path("f0_std").asDouble() * d;
            else hasPitch = false;
        }
        ObjectNode out = JSON.createObjectNode();
        out.put("empty", false);
        out.put("duration", round3(duration));
        out.put("speech_ratio", round3(speechRatio / duration));
        out.put("pause_count", Math.round(pauses));
        out.put("longest_pause", round3(longestPause));
        out.put("rms_mean_db", round3(rmsMean / duration));
        out.put("rms_std_db", round3(rmsStd / duration));
        out.put("frame_count", Math.round(frames));
        if (hasPitch) out.put("f0_std", round3(f0 / duration));
        return out;
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    static double fluency(JsonNode m) {
        double pauses = metric(m, "pause_count");
        double longest = metric(m, "longest_pause");
        double speech = metric(m, "speech_ratio");
        double score = 100 - Math.min(40, pauses * 7) - Math.min(30, Math.max(0, longest - 0.8) * 18)
                - Math.max(0, 0.55 - speech) * 100;
        return clamp(score, 30, 100);
    }

    static double volume(JsonNode m) {
        double mean = metric(m, "rms_mean_db");
        double std = metric(m, "rms_std_db");
        double ideal = -20;                       // 口播比较舒服的位置
        double score = 100 - Math.abs(mean - ideal) * 3.2 - Math.max(0, std - 4) * 3;
        return clamp(score, 30, 100);
    }

    static double confidence(JsonNode m) {
        double f0std = metric(m, "f0_std");
        double volStd = metric(m, "rms_std_db");
        double pauses = metric(m, "pause_count");
        double score = 82 + (f0std >= 10 && f0std <= 40 ? 8 : -6) - Math.max(0, volStd - 5) * 3 - pauses * 3;
        return clamp(score, 30, 100);
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP);
    }

    /** 全会话一个 0..1 的语音分，供评分使用 */
    public double overallScore(Long sessionId) {
        List<InterviewVoiceAnalysis> rows = results(sessionId);
        if (rows.isEmpty()) return -1;
        double sum = 0;
        int n = 0;
        for (InterviewVoiceAnalysis r : rows) {
            if (r.getFluency() == null || r.getVolumeScore() == null || r.getConfidenceEst() == null) continue;
            sum += (r.getFluency().doubleValue() + r.getVolumeScore().doubleValue()
                    + r.getConfidenceEst().doubleValue()) / 3.0;
            n++;
        }
        return n == 0 ? -1 : sum / n / 100.0;
    }

}
