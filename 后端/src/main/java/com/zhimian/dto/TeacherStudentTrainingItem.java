package com.zhimian.dto;

import lombok.Data;

/**
 * 教师仪表盘 - 学生训练情况列表的单行数据（Phase 5.3）。
 * 不包含密码、邮箱、手机号等敏感字段。
 */
@Data
public class TeacherStudentTrainingItem {

    /** 学生展示名（昵称优先，缺省回退用户名） */
    private String studentName;
    /** 最近一次训练的目标岗位名称；无则为 "-" */
    private String position;
    /** 训练次数（该学生的面试会话总数） */
    private Integer trainingCount;
    /** 平均分（该学生报告 totalScore 平均值，保留 1 位小数；无报告为 0） */
    private Double averageScore;
    /** 最近一次训练时间，格式 yyyy-MM-dd HH:mm；从未训练为 null */
    private String lastTrainingTime;
    /** 训练状态：未开始 / 进行中 / 已完成 */
    private String status;
}
