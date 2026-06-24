package com.zhimian.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试记录（会话 + 报告 + 岗位名的组合视图）
 */
@Data
public class InterviewRecord {
    private Long sessionId;
    private Long jobId;
    private String jobName;
    private Integer difficulty;
    private String status;
    private Double totalScore;
    private Long reportId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
