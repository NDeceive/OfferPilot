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
    private LocalDateTime createTime;
}
