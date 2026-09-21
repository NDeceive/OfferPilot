package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.dto.DashboardOverviewResponse;
import com.zhimian.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public Result<DashboardOverviewResponse> overview() {
        return Result.success(dashboardService.getOverview());
    }
}
