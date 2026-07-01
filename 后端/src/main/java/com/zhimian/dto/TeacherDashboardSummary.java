package com.zhimian.dto;

import lombok.Data;

/**
 * 教师仪表盘 - 顶部汇总指标（Phase 5.3）。
 * 全部为教师视角的全局统计，不按单个用户隔离。
 */
@Data
public class TeacherDashboardSummary {

    /** 学生总数（sys_user 中 role=STUDENT） */
    private Integer studentTotal;
    /** 已参与训练的学生数（在 interview_session 中出现过的学生去重数） */
    private Integer trainedStudentCount;
    /** 训练完成率 = trainedStudentCount / studentTotal（0~1，保留 2 位小数；学生为 0 时为 0） */
    private Double trainingCompletionRate;
    /** 平均分（interview_report.totalScore 的平均值，保留 1 位小数；无报告时为 0） */
    private Double averageScore;
    /** AI 追问数（interview_followup_record 中 source=AI） */
    private Long aiFollowupCount;
    /** 规则兜底追问数（interview_followup_record 中 source=RULE） */
    private Long ruleFollowupCount;
}
