package com.zhimian.dto;

import lombok.Data;

/**
 * 教师仪表盘 - 薄弱项分布的单条数据（Phase 5.3）。
 */
@Data
public class TeacherWeaknessItem {

    /** 薄弱项名称（固定四类） */
    private String name;
    /** 占比（0~1，保留 2 位小数） */
    private Double percent;
    /** 命中次数 */
    private Integer count;
}
