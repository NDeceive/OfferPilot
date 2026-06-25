package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.dto.ReportDetailResponse;
import com.zhimian.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 能力报告接口（Phase 2 规则化）。
 */
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** 报告详情：仅本人可查看（服务层按 UserContext 校验归属） */
    @GetMapping("/{reportId}")
    public Result<ReportDetailResponse> detail(@PathVariable Long reportId) {
        return Result.success(reportService.getDetail(reportId));
    }
}
