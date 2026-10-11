package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.config.RequireRole;
import com.zhimian.service.TeacherAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/teaching/analytics")
@RequireRole({"TEACHER", "ADMIN"})
@RequiredArgsConstructor
public class TeacherAnalyticsController {
    private final TeacherAnalyticsService analytics;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.success(analytics.overview(new TeacherAnalyticsService.Filter(classId, semester, jobId, from, to)));
    }

    @GetMapping("/records")
    public Result<Map<String, Object>> records(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(analytics.records(new TeacherAnalyticsService.Filter(classId, semester, jobId, from, to), page, size));
    }
}
