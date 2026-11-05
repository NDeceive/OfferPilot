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
        return save(sessionId, roundNo, 0, questionId, bytes, durationMs, userId);
    }

    /** answerSeq：同一题内的第几段作答，0 主回答，1 起为追问回答 */
    public InterviewAnswerAudio save(Long sessionId, int roundNo, int answerSeq, Long questionId, byte[] bytes, int durationMs, Long userId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) throw new BizException("会话不存在");
        if (userId != null && !userId.equals(session.getUserId())) throw new BizException("无权上传该会话的录音");

        String fileName = answerSeq <= 0 ? roundNo + ".wav" : roundNo + "-" + answerSeq + ".wav";
        String relative = sessionId + "/" + fileName;
        Path file = Paths.get(rootDir, String.valueOf(sessionId), fileName);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        } catch (IOException e) {
            log.warn("[作答录音] 落盘失败 sessionId={}, round={}, seq={}, err={}", sessionId, roundNo, answerSeq, e.getMessage());
            throw new BizException("录音保存失败");
        }

        InterviewAnswerAudio row = findByRound(sessionId, roundNo, answerSeq);
        if (row == null) {
            row = new InterviewAnswerAudio();
            row.setSessionId(sessionId);
            row.setRoundNo(roundNo);
            row.setAnswerSeq(answerSeq);
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
        return findByRound(sessionId, roundNo, 0);
    }

    public InterviewAnswerAudio findByRound(Long sessionId, int roundNo, int answerSeq) {
        List<InterviewAnswerAudio> rows = audioMapper.selectList(new LambdaQueryWrapper<InterviewAnswerAudio>()
                .eq(InterviewAnswerAudio::getSessionId, sessionId)
                .eq(InterviewAnswerAudio::getRoundNo, roundNo)
                .eq(InterviewAnswerAudio::getAnswerSeq, answerSeq)
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Validate the acknowledged recording before allowing the introduction to advance. */
    public void requireValidIntroduction(long sessionId,int roundNo,Long questionId,Long audioId) {
        var row=findByRound(sessionId,roundNo);
        if(audioId==null || row==null || !audioId.equals(row.getId()) || !java.util.Objects.equals(questionId,row.getQuestionId()) || !"OK".equals(row.getStatus()))
            throw new BizException("自我介绍需要录音保存成功后再提交，请重新录音");
        try(var input=javax.sound.sampled.AudioSystem.getAudioInputStream(pathOf(row).toFile())) {
            var format=input.getFormat();double duration=input.getFrameLength()/format.getFrameRate();
            if(!javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding()) || format.getSampleSizeInBits()!=16
                    || format.getChannels()!=1 || format.isBigEndian() || format.getSampleRate()<8000 || format.getSampleRate()>48000
                    || duration<0.3 || duration>600 || Files.size(pathOf(row))>20L*1024*1024)
                throw new BizException("自我介绍录音无效，请重新录音");
            byte[] pcm=input.readAllBytes();
            if(pcm.length!=input.getFrameLength()*format.getFrameSize())throw new BizException("录音文件不完整，请重新录音");
            int low=32767,high=-32768;
            for(int i=0;i+1<pcm.length;i+=2){int sample=(short)((pcm[i]&255)|(pcm[i+1]<<8));low=Math.min(low,sample);high=Math.max(high,sample);}
            if(high-low<4)throw new BizException("没有检测到有效录音，请检查麦克风");
        }catch(BizException e){throw e;}catch(Exception e){throw new BizException("录音文件不存在或格式无效，请重新录音");}
    }

    public List<InterviewAnswerAudio> listBySession(Long sessionId) {
        return audioMapper.selectList(new LambdaQueryWrapper<InterviewAnswerAudio>()
                .eq(InterviewAnswerAudio::getSessionId, sessionId)
                .orderByAsc(InterviewAnswerAudio::getRoundNo)
                .orderByAsc(InterviewAnswerAudio::getAnswerSeq));
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
