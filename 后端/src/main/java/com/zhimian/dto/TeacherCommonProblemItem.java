package com.zhimian.dto;

import lombok.Data;

/**
 * 教师仪表盘 - 常见问题的单条数据（Phase 5.3）。
 */
@Data
public class TeacherCommonProblemItem {

    /** 问题标题 */
    private String title;
    /** 问题描述 */
    private String description;
    /** 出现占比（0~1，保留 2 位小数） */
    private Double percent;
    /** 严重程度：高 / 中 / 低 */
    private String level;
}
