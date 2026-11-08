package com.zhimian.dto;

import lombok.Data;

/**
 * 开始面试响应：会话ID + 岗位名 + 第一题
 */
@Data
public class InterviewStartResponse {
    private java.util.Map<String,Object> interviewPlan;
    private java.util.Map<String,Object> minutes;

    private Long sessionId;
    private String jobName;
    private QuestionView question;
    /** 面试时长（秒） */
    private Integer durationSeconds;
    /** Remaining server time when resuming an existing teaching attempt. */
    private Integer remainingSeconds;
    private Boolean finishable;
}
