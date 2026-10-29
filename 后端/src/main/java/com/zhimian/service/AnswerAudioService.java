package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhimian.common.BizException;
import com.zhimian.entity.InterviewAnswerAudio;
import com.zhimian.entity.InterviewSession;
import com.zhimian.mapper.InterviewAnswerAudioMapper;
import com.zhimian.mapper.InterviewSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 作答录音的落盘与读取。失败只影响录音本身，不阻断答题主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerAudioService {

    private final InterviewAnswerAudioMapper audioMapper;
    private final InterviewSessionMapper sessionMapper;

    @Value("${interview.audio.dir:data/answer-audio}")
    private String rootDir;

    public InterviewAnswerAudio save(Long sessionId, int roundNo, Long questionId, byte[] bytes, int durationMs, Long userId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) throw new BizException("会话不存在");
        if (userId != null && !userId.equals(session.getUserId())) throw new BizException("无权上传该会话的录音");

        String relative = sessionId + "/" + roundNo + ".wav";
        Path file = Paths.get(rootDir, String.valueOf(sessionId), roundNo + ".wav");
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        } catch (IOException e) {
            log.warn("[作答录音] 落盘失败 sessionId={}, round={}, err={}", sessionId, roundNo, e.getMessage());
            throw new BizException("录音保存失败");
        }

        InterviewAnswerAudio row = findByRound(sessionId, roundNo);
        if (row == null) {
            row = new InterviewAnswerAudio();
            row.setSessionId(sessionId);
            row.setRoundNo(roundNo);
            row.setQuestionId(questionId);
            row.setFilePath(relative);
            row.setDurationMs(durationMs);
            row.setByteSize((long) bytes.length);
            row.setStatus("OK");
            audioMapper.insert(row);
        } else {
            row.setFilePath(relative);
            row.setDurationMs(durationMs);
            row.setByteSize((long) bytes.length);
            row.setStatus("OK");
            row.setFailureReason(null);
            audioMapper.updateById(row);
        }
        return row;
    }

    public InterviewAnswerAudio findByRound(Long sessionId, int roundNo) {
        List<InterviewAnswerAudio> rows = audioMapper.selectList(new LambdaQueryWrapper<InterviewAnswerAudio>()
                .eq(InterviewAnswerAudio::getSessionId, sessionId)
                .eq(InterviewAnswerAudio::getRoundNo, roundNo)
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<InterviewAnswerAudio> listBySession(Long sessionId) {
        return audioMapper.selectList(new LambdaQueryWrapper<InterviewAnswerAudio>()
                .eq(InterviewAnswerAudio::getSessionId, sessionId)
                .orderByAsc(InterviewAnswerAudio::getRoundNo));
    }

    /** 读取时校验归属，别人的录音不给听 */
    public InterviewAnswerAudio requireOwned(Long audioId, Long userId) {
        InterviewAnswerAudio row = audioMapper.selectById(audioId);
        if (row == null) throw new BizException("录音不存在");
        InterviewSession session = sessionMapper.selectById(row.getSessionId());
        if (session == null || !userId.equals(session.getUserId())) throw new BizException("无权访问该录音");
        return row;
    }

    public Path pathOf(InterviewAnswerAudio row) {
        return Paths.get(rootDir, row.getFilePath());
    }

    public void deleteBySession(Long sessionId) {
        List<InterviewAnswerAudio> rows = listBySession(sessionId);
        for (InterviewAnswerAudio row : rows) {
            try { Files.deleteIfExists(pathOf(row)); } catch (IOException ignored) { }
        }
        try { Files.deleteIfExists(Paths.get(rootDir, String.valueOf(sessionId))); } catch (IOException ignored) { }
        audioMapper.delete(new LambdaQueryWrapper<InterviewAnswerAudio>()
                .eq(InterviewAnswerAudio::getSessionId, sessionId));
    }
}
