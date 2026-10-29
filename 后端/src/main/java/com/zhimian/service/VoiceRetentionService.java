package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhimian.entity.InterviewVoiceAnalysis;
import com.zhimian.entity.InterviewVoiceRetention;
import com.zhimian.entity.InterviewVoiceSession;
import com.zhimian.mapper.InterviewVoiceAnalysisMapper;
import com.zhimian.mapper.InterviewVoiceRetentionMapper;
import com.zhimian.mapper.InterviewVoiceSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 作答录音保留期限：默认 30 天，面试结束后可手动改，到点清理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceRetentionService {

    public static final int DEFAULT_DAYS = 30;
    /** 可选的挡位（天），0 表示永久 */
    public static final int[] OPTIONS = {1, 7, 30, 365, 0};

    private final InterviewVoiceRetentionMapper mapper;
    private final InterviewVoiceSessionMapper voiceSessionMapper;
    private final InterviewVoiceAnalysisMapper analysisMapper;
    private final AnswerAudioService audioService;

    public InterviewVoiceRetention ensureDefault(Long sessionId) {
        InterviewVoiceRetention row = mapper.selectById(sessionId);
        if (row == null || row.getRetainDays() == null) {
            return set(sessionId, DEFAULT_DAYS);
        }
        return row;
    }

    public InterviewVoiceRetention set(Long sessionId, int days) {
        InterviewVoiceRetention row = new InterviewVoiceRetention();
        row.setSessionId(sessionId);
        row.setRetainDays(days);
        row.setExpireAt(days <= 0 ? null : LocalDateTime.now().plusDays(days));
        InterviewVoiceRetention exists = mapper.selectById(sessionId);
        if (exists == null) {
            mapper.insert(row);
        } else {
            // updateById 默认忽略 null，永久保存时必须显式把 expire_at 置空
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<InterviewVoiceRetention> w =
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
            w.eq(InterviewVoiceRetention::getSessionId, sessionId)
                    .set(InterviewVoiceRetention::getRetainDays, days)
                    .set(InterviewVoiceRetention::getExpireAt, row.getExpireAt());
            mapper.update(null, w);
        }
        return row;
    }

    public Map<String, Object> info(Long sessionId) {
        InterviewVoiceRetention row = mapper.selectById(sessionId);
        Map<String, Object> out = new LinkedHashMap<>();
        int days = row == null || row.getRetainDays() == null ? DEFAULT_DAYS : row.getRetainDays();
        out.put("retainDays", days);
        out.put("expireAt", row == null || row.getExpireAt() == null ? null : row.getExpireAt().toString());
        out.put("options", OPTIONS);
        return out;
    }

    /** 只删语音：音频文件、逐题分析、保留记录；面试与报告本身不动 */
    public void purgeVoice(Long sessionId) {
        audioService.deleteBySession(sessionId);
        analysisMapper.delete(new LambdaQueryWrapper<InterviewVoiceAnalysis>()
                .eq(InterviewVoiceAnalysis::getSessionId, sessionId));
        InterviewVoiceSession state = voiceSessionMapper.selectById(sessionId);
        if (state != null) {
            state.setStatus("DELETED");
            state.setFailureReason(null);
            voiceSessionMapper.updateById(state);
        }
        mapper.deleteById(sessionId);
        log.info("[语音清理] sessionId={} 手动删除语音", sessionId);
    }

    /** 每小时扫一次到期录音；删文件、删分析、标记会话已过期 */
    @Scheduled(fixedDelayString = "${interview.voice.cleanup-ms:3600000}", initialDelayString = "${interview.voice.cleanup-initial-ms:60000}")
    public void cleanupExpired() {
        List<InterviewVoiceRetention> expired = mapper.selectList(new LambdaQueryWrapper<InterviewVoiceRetention>()
                .isNotNull(InterviewVoiceRetention::getExpireAt)
                .lt(InterviewVoiceRetention::getExpireAt, LocalDateTime.now())
                .last("LIMIT 50"));
        for (InterviewVoiceRetention row : expired) {
            Long sid = row.getSessionId();
            try {
                audioService.deleteBySession(sid);
                analysisMapper.delete(new LambdaQueryWrapper<InterviewVoiceAnalysis>()
                        .eq(InterviewVoiceAnalysis::getSessionId, sid));
                InterviewVoiceSession state = voiceSessionMapper.selectById(sid);
                if (state != null) {
                    state.setStatus("EXPIRED");
                    voiceSessionMapper.updateById(state);
                }
                mapper.deleteById(sid);
                log.info("[语音清理] sessionId={} 到期删除", sid);
            } catch (Exception e) {
                log.warn("[语音清理] sessionId={} 失败: {}", sid, e.getMessage());
            }
        }
    }
}
