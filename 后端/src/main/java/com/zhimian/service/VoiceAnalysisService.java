package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.List;

/**
 * 报告生成前的批处理语音分析。
 * <p>
 * 任何异常都只影响语音这一块，不能拖垮报告。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceAnalysisService {

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
                .orderByAsc(InterviewVoiceAnalysis::getRoundNo));
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

        int ok = 0;
        for (InterviewAnswerAudio audio : usable) {
            try {
                Path path = audioService.pathOf(audio).toAbsolutePath();
                JsonNode m = client.analyze(path.toString());
                InterviewVoiceAnalysis row = new InterviewVoiceAnalysis();
                row.setSessionId(sessionId);
                row.setRoundNo(audio.getRoundNo());
                row.setMetricsJson(m.toString());
                row.setFluency(bd(fluency(m)));
                row.setVolumeScore(bd(volume(m)));
                row.setConfidenceEst(bd(confidence(m)));
                InterviewVoiceAnalysis exists = findOne(sessionId, audio.getRoundNo());
                if (exists != null) {
                    row.setId(exists.getId());
                    analysisMapper.updateById(row);
                } else {
                    analysisMapper.insert(row);
                }
                ok++;
            } catch (Exception e) {
                log.warn("[语音分析] 单题失败 sessionId={}, round={}, err={}",
                        sessionId, audio.getRoundNo(), e.getMessage());
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

    static double fluency(JsonNode m) {
        double pauses = m.path("pause_count").asDouble(0);
        double longest = m.path("longest_pause").asDouble(0);
        double speech = m.path("speech_ratio").asDouble(0.8);
        double score = 100 - Math.min(40, pauses * 7) - Math.min(30, Math.max(0, longest - 0.8) * 18)
                - Math.max(0, 0.55 - speech) * 100;
        return clamp(score, 30, 100);
    }

    static double volume(JsonNode m) {
        double mean = m.path("rms_mean_db").asDouble(-30);
        double std = m.path("rms_std_db").asDouble(3);
        double ideal = -20;                       // 口播比较舒服的位置
        double score = 100 - Math.abs(mean - ideal) * 3.2 - Math.max(0, std - 4) * 3;
        return clamp(score, 30, 100);
    }

    static double confidence(JsonNode m) {
        double f0std = m.path("f0_std").asDouble(20);
        double volStd = m.path("rms_std_db").asDouble(3);
        double pauses = m.path("pause_count").asDouble(0);
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
            if (r.getFluency() == null) continue;
            sum += (r.getFluency().doubleValue() + safe(r.getVolumeScore(), 70)
                    + safe(r.getConfidenceEst(), 70)) / 3.0;
            n++;
        }
        return n == 0 ? -1 : sum / n / 100.0;
    }

    private static double safe(BigDecimal v, double fallback) {
        return v == null ? fallback : v.doubleValue();
    }
}
