package com.zhimian.dto;

import lombok.Data;

/**
 * 教师仪表盘 - 近 7 天当前教师教学任务会话新建趋势。
 */
@Data
public class TeacherTrainingTrendItem {

    /** 日期，格式 yyyy-MM-dd */
    private String date;
    /** 当日新建的教学任务面试会话数，不代表有效完成次数 */
    private Integer count;
}
