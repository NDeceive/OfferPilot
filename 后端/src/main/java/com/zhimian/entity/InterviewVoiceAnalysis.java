package com.zhimian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 逐题语音分析结果 */
@Data
@TableName("interview_voice_analysis")
public class InterviewVoiceAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private Integer roundNo;
    private String metricsJson;
    private BigDecimal fluency;
    private BigDecimal volumeScore;
    private BigDecimal confidenceEst;
    private LocalDateTime createTime;
}
