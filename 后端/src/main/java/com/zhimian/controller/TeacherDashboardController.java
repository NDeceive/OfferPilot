package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.config.RequireRole;
import com.zhimian.dto.TeacherDashboardOverviewResponse;
import com.zhimian.service.TeacherDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教师仪表盘接口（Phase 5.3，后端 only）。
 * <p>
 * 仅 TEACHER / ADMIN 可访问，由 {@link RequireRole} + RoleInterceptor 校验，
 * 普通 STUDENT 访问返回 403，不暴露教师端聚合数据。只读查询，不触碰学生面试主流程。
 */
@Slf4j
@RestController
@RequestMapping("/api/teacher/dashboard")
@RequireRole({"TEACHER", "ADMIN"})
@RequiredArgsConstructor
public class TeacherDashboardController {

    private final TeacherDashboardService teacherDashboardService;

    /**
     * 教师仪表盘总览：汇总指标 + 7 天训练趋势 + 薄弱项分布 + 学生训练列表 + 常见问题。
     */
    @GetMapping("/overview")
    public Result<TeacherDashboardOverviewResponse> overview() {
        log.info("[教师仪表盘] 查询总览");
        return Result.success(teacherDashboardService.getOverview());
    }
}
