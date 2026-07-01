package com.zhimian.dto;

import lombok.Data;

import java.util.List;

/**
 * 教师仪表盘总览响应（Phase 5.3）。
 * 对应 GET /api/teacher/dashboard/overview。
 */
@Data
public class TeacherDashboardOverviewResponse {

    /** 顶部汇总指标 */
    private TeacherDashboardSummary summary;
    /** 近 7 天训练趋势（固定 7 条，从 6 天前到今天） */
    private List<TeacherTrainingTrendItem> trainingTrend;
    /** 薄弱项分布（固定四类） */
    private List<TeacherWeaknessItem> weaknessDistribution;
    /** 学生训练情况列表 */
    private List<TeacherStudentTrainingItem> studentTrainingList;
    /** 常见问题 */
    private List<TeacherCommonProblemItem> commonProblems;
}
