package com.zhimian.dto;

import lombok.Data;

/**
 * 教师仪表盘 - 顶部汇总指标（Phase 5.3）。
 * 仅统计当前教师名下班级及任务的教学数据。
 */
@Data
public class TeacherDashboardSummary {

    /** 当前教师班级中的已加入学生数（跨班去重） */
    private Integer studentTotal;
    /** 已完成至少一份发布任务所要求有效训练的学生去重数 */
    private Integer trainedStudentCount;
    /** 训练完成率 = trainedStudentCount / studentTotal（0~1，保留 2 位小数；学生为 0 时为 0） */
    private Double trainingCompletionRate;
    /** 当前教师任务中 READY 且有效的报告平均分；无报告时为 0 */
    private Double averageScore;
    /** 当前教师教学任务会话中的 AI 追问数 */
    private Long aiFollowupCount;
    /** 当前教师教学任务会话中的规则兜底追问数 */
    private Long ruleFollowupCount;
}
