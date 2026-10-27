package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.service.ResumeAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/resume-workbench/analysis")
@RequiredArgsConstructor
public class ResumeAnalysisController {
    private final ResumeAnalysisService service;
    @GetMapping public Result<ResumeAnalysisService.Report> latest(@RequestParam Long jobId,
            @RequestParam(required=false) Long versionId) { return Result.success(service.latest(jobId, versionId)); }
    @PostMapping public Result<ResumeAnalysisService.Report> analyze(@RequestBody ResumeAnalysisService.Input input) {
        return Result.success(service.analyze(input));
    }
    @GetMapping("/{id}") public Result<ResumeAnalysisService.Report> report(@PathVariable long id) {
        return Result.success(service.report(id));
    }
}
