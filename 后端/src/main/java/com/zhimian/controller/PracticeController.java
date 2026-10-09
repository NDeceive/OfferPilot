package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.service.PracticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/practice")
@RequiredArgsConstructor
public class PracticeController {
    private final PracticeService practice;

    @GetMapping("/overview") public Result<Map<String, Object>> overview() { return Result.success(practice.overview()); }
    @PostMapping("/sessions") public Result<Map<String, Object>> create(@Valid @RequestBody PracticeService.StartInput input) { return Result.success(practice.create(input)); }
    @GetMapping("/sessions/{sessionId}") public Result<Map<String, Object>> session(@PathVariable long sessionId) { return Result.success(practice.session(sessionId)); }
    @PostMapping("/sessions/{sessionId}/questions/{questionId}/answers")
    public Result<Map<String, Object>> answer(@PathVariable long sessionId, @PathVariable long questionId, @Valid @RequestBody PracticeService.AnswerInput input) { return Result.success(practice.answer(sessionId, questionId, input)); }
    @PostMapping("/sessions/{sessionId}/questions/{questionId}/run")
    public Result<Map<String, Object>> run(@PathVariable long sessionId, @PathVariable long questionId, @Valid @RequestBody PracticeService.CodeInput input) { return Result.success(practice.run(sessionId, questionId, input)); }
    @PostMapping("/sessions/{sessionId}/finish") public Result<Map<String, Object>> finish(@PathVariable long sessionId) { return Result.success(practice.finish(sessionId)); }
    @PostMapping("/questions/{questionId}/favorite") public Result<Map<String, Object>> favorite(@PathVariable long questionId) { return Result.success(practice.toggleFavorite(questionId)); }
}
