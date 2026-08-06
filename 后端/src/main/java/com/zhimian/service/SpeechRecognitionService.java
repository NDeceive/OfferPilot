package com.zhimian.service;

import com.zhimian.common.BizException;
import com.zhimian.config.AsrProperties;
import com.zhimian.config.UserContext;
import com.zhimian.dto.SpeechTranscriptResponse;
import com.zhimian.entity.InterviewSession;
import com.zhimian.mapper.InterviewSessionMapper;
import com.zhimian.service.ai.ZhipuAsrClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SpeechRecognitionService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "audio/wav",
            "audio/x-wav",
            "audio/wave",
            "audio/vnd.wave",
            "application/octet-stream"
    );

    private final InterviewSessionMapper interviewSessionMapper;
    private final ZhipuAsrClient asrClient;
    private final AsrProperties properties;

    public SpeechTranscriptResponse transcribe(Long sessionId, MultipartFile file) {
        validateSession(sessionId);
        byte[] bytes = validateAndReadWav(file);
        double duration = readWavDuration(bytes);
        if (duration <= 0 || duration > properties.getMaxDurationSeconds()) {
            throw new BizException("单段语音时长必须在30秒以内");
        }
        return asrClient.transcribe(bytes, "interview-" + sessionId + ".wav", duration);
    }

    private void validateSession(Long sessionId) {
        if (sessionId == null) {
            throw new BizException("面试会话不能为空");
        }
        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        Long currentUserId = UserContext.getUserId();
        if (session == null || currentUserId == null || !currentUserId.equals(session.getUserId())) {
            throw new BizException("面试会话不存在或无权访问");
        }
        if (!"ONGOING".equals(session.getStatus())) {
            throw new BizException("当前面试已经结束");
        }
    }

    private byte[] validateAndReadWav(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("语音文件不能为空");
        }
        if (file.getSize() > properties.getMaxFileSize()) {
            throw new BizException("语音文件不能超过10MB");
        }
        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new BizException("仅支持 WAV 语音文件");
        }
        String filename = file.getOriginalFilename();
        if (filename != null && !filename.toLowerCase().endsWith(".wav")) {
            throw new BizException("仅支持 WAV 语音文件");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 44
                    || !"RIFF".equals(new String(bytes, 0, 4, StandardCharsets.US_ASCII))
                    || !"WAVE".equals(new String(bytes, 8, 4, StandardCharsets.US_ASCII))) {
                throw new BizException("语音文件格式不正确");
            }
            return bytes;
        } catch (IOException e) {
            throw new BizException("读取语音文件失败");
        }
    }

    static double readWavDuration(byte[] wav) {
        if (wav.length < 44) return 0;
        int channels = readLittleEndian16(wav, 22);
        long sampleRate = readLittleEndian32(wav, 24);
        int bitsPerSample = readLittleEndian16(wav, 34);
        long dataSize = readLittleEndian32(wav, 40);
        if (channels <= 0 || sampleRate <= 0 || bitsPerSample <= 0 || dataSize <= 0) return 0;
        return dataSize / (sampleRate * channels * (bitsPerSample / 8.0));
    }

    private static int readLittleEndian16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static long readLittleEndian32(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffL)
                | ((bytes[offset + 1] & 0xffL) << 8)
                | ((bytes[offset + 2] & 0xffL) << 16)
                | ((bytes[offset + 3] & 0xffL) << 24);
    }
}
