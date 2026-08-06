package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.dto.SpeechTranscriptResponse;
import com.zhimian.service.SpeechRecognitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/speech")
@RequiredArgsConstructor
public class SpeechController {

    private final SpeechRecognitionService speechRecognitionService;

    @PostMapping("/transcribe")
    public Result<SpeechTranscriptResponse> transcribe(
            @RequestParam("sessionId") Long sessionId,
            @RequestParam("file") MultipartFile file) {
        return Result.success(speechRecognitionService.transcribe(sessionId, file));
    }
}
