package com.zhimian.dto;

import lombok.Data;

/**
 * 教师仪表盘 - 近 7 天训练趋势的单日数据（Phase 5.3）。
 */
@Data
public class TeacherTrainingTrendItem {

    /** 日期，格式 yyyy-MM-dd */
    private String date;
    /** 当日新建的训练（面试会话）数 */
    private Integer count;
}
