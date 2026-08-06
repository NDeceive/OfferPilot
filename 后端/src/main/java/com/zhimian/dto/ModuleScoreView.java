package com.zhimian.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 单模块评分视图（用于报告详情响应）。
 */
@Data
public class ModuleScoreView {
    /** 模块编码 */
    private String moduleCode;
    /** 模块中文名 */
    private String moduleName;
    /** 原始得分(0-100) */
    private BigDecimal rawScore;
    /** 目标线 */
    private BigDecimal targetScore;
    /** 单模块匹配度 */
    private BigDecimal moduleMatch;
    /** 该模块权重 */
    private BigDecimal baseWeight;
    /** 差距分(目标-实际) */
    private BigDecimal gapScore;
    /** 提升优先级(权重×差距) */
    private BigDecimal improvementPriority;
    /** 评分证据 */
    private String evidence;
    /** 改进建议 */
    private String suggestion;
    /** AI置信度 */
    private BigDecimal aiConfidence;
    /** 评分来源: AI / RULE */
    private String scoreSource;
}
