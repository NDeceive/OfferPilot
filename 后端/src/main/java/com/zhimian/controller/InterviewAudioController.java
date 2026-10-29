package com.zhimian.controller;

import com.zhimian.common.BizException;
import com.zhimian.common.Result;
import com.zhimian.config.UserContext;
import com.zhimian.entity.InterviewAnswerAudio;
import com.zhimian.service.AnswerAudioService;
import com.zhimian.service.VoiceRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 作答录音上传与回放。
 * <p>
 * 回放走带鉴权的接口 + 前端 fetch 成 Blob，不把音频 URL 暴露成公开链接。
 */
@Slf4j
@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewAudioController {

    private static final long MAX_BYTES = 20L * 1024 * 1024;

    private final AnswerAudioService audioService;
    private final VoiceRetentionService retentionService;
    private final com.zhimian.mapper.InterviewSessionMapper sessionMapper;

    @PostMapping("/{sessionId}/answers/{roundNo}/audio")
    public Result<Map<String, Object>> upload(@PathVariable Long sessionId,
                                              @PathVariable int roundNo,
                                              @RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "questionId", required = false) Long questionId) {
        Long userId = UserContext.getUserId();
        if (file == null || file.isEmpty()) throw new BizException("录音为空");
        if (file.getSize() > MAX_BYTES) throw new BizException("录音过大");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BizException("录音读取失败");
        }
        if (bytes.length < 44 || bytes[0] != 'R' || bytes[1] != 'I' || bytes[2] != 'F' || bytes[3] != 'F') {
            throw new BizException("录音格式不正确");
        }
        int durationMs = wavDurationMs(bytes);
        InterviewAnswerAudio row = audioService.save(sessionId, roundNo, questionId, bytes, durationMs, userId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("audioId", row.getId());
        data.put("durationMs", durationMs);
        data.put("size", row.getByteSize());
        return Result.success(data);
    }

    /** 面试结束后手动改保留期限：1/7/30/365 天，0 表示永久 */
    @PostMapping("/{sessionId}/voice-retention")
    public Result<Map<String, Object>> setRetention(@PathVariable Long sessionId,
                                                    @RequestBody Map<String, Integer> body) {
        var session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(UserContext.getUserId())) {
            throw new BizException("无权操作该会话");
        }
        int days = body.getOrDefault("days", VoiceRetentionService.DEFAULT_DAYS);
        boolean ok = false;
        for (int d : VoiceRetentionService.OPTIONS) if (d == days) ok = true;
        if (!ok) throw new BizException("不支持的保留期限");
        retentionService.set(sessionId, days);
        return Result.success(retentionService.info(sessionId));
    }

    /** 只删本场语音，面试记录和报告都保留 */
    @DeleteMapping("/{sessionId}/voice")
    public Result<Map<String, Object>> deleteVoice(@PathVariable Long sessionId) {
        var session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(UserContext.getUserId())) {
            throw new BizException("无权操作该会话");
        }
        retentionService.purgeVoice(sessionId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "DELETED");
        return Result.success(data);
    }

    /** 逐题回放；支持 Range，拖进度条才正常 */
    @GetMapping("/audio/{audioId}")
    public ResponseEntity<byte[]> play(@PathVariable Long audioId,
                                       @RequestHeader(value = "Range", required = false) String range) {
        InterviewAnswerAudio row = audioService.requireOwned(audioId, UserContext.getUserId());
        Path path = audioService.pathOf(row);
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new BizException("录音文件已丢失");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("audio/wav"));
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setCacheControl("private, max-age=600");

        if (range != null && range.startsWith("bytes=")) {
            String spec = range.substring(6).trim();
            String[] parts = spec.split("-", -1);
            int start;
            int end;
            try {
                start = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
                end = parts.length < 2 || parts[1].isEmpty() ? bytes.length - 1 : Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                start = 0;
                end = bytes.length - 1;
            }
            start = Math.max(0, Math.min(start, bytes.length - 1));
            end = Math.max(start, Math.min(end, bytes.length - 1));
            int len = end - start + 1;
            byte[] slice = new byte[len];
            System.arraycopy(bytes, start, slice, 0, len);
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + bytes.length);
            headers.setContentLength(len);
            return new ResponseEntity<>(slice, headers, HttpStatus.PARTIAL_CONTENT);
        }
        headers.setContentLength(bytes.length);
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    /** 从 WAV 头里算时长：按 byteRate 与 data 块大小 */
    static int wavDurationMs(byte[] b) {
        try {
            int byteRate = (b[28] & 0xff) | ((b[29] & 0xff) << 8) | ((b[30] & 0xff) << 16) | ((b[31] & 0xff) << 24);
            int dataSize = (b[40] & 0xff) | ((b[41] & 0xff) << 8) | ((b[42] & 0xff) << 16) | ((b[43] & 0xff) << 24);
            if (byteRate <= 0 || dataSize <= 0) return 0;
            return (int) Math.round(dataSize * 1000.0 / byteRate);
        } catch (Exception e) {
            return 0;
        }
    }
}
