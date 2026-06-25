package com.zhimian.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 报告单维度视图（雷达图 + 维度解释卡）。
 */
@Data
public class ReportDimensionView {
    private String dimension;
    private BigDecimal score;
    private String level;
    private String explanation;
}
