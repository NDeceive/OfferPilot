package com.zhimian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 面试评估报告
 */
@Data
@TableName("interview_report")
public class InterviewReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private Long userId;
    private BigDecimal totalScore;
    private String summary;
    private String strengths;
    private String weaknesses;
    private String suggestions;
    private String weakTags;

    // 评分系统改造新增字段
    /** 整体匹配度(0-120+) */
    private BigDecimal overallMatchScore;
    /** 匹配度等级：已达标/接近/部分达到/差距较大 */
    private String displayLevel;
    /** 匹配画像标签(逗号分隔) */
    private String profileLabel;
    /** 完整报告JSON */
    private String reportJson;
    /** 可视化专用JSON */
    private String visualizationJson;

    private LocalDateTime createTime;
}
