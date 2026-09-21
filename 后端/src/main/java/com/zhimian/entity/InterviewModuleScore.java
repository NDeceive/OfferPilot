package com.zhimian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 模块评分明细（每模块一条，含AI证据+建议+置信度）
 */
@Data
@TableName("interview_module_score")
public class InterviewModuleScore {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联报告ID */
    private Long reportId;

    /** 模块编码 */
    private String moduleCode;

    /** 原始得分(0-100) */
    private BigDecimal rawScore;

    /** 目标线(65/75/85) */
    private BigDecimal targetScore;

    /** 单模块匹配度 */
    private BigDecimal moduleMatch;

    /** 该模块权重 */
    private BigDecimal baseWeight;

    /** 差距分(目标-实际,≥0) */
    private BigDecimal gapScore;

    /** 提升优先级(权重×差距) */
    private BigDecimal improvementPriority;

    /** AI评分证据 */
    private String evidence;

    /** AI改进建议 */
    private String suggestion;

    /** AI置信度(0-1) */
    private BigDecimal aiConfidence;

    /** 评分来源: AI / RULE */
    private String scoreSource;
}
