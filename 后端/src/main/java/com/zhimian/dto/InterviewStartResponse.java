package com.zhimian.dto;

import lombok.Data;

/**
 * 开始面试响应：会话ID + 岗位名 + 第一题
 */
@Data
public class InterviewStartResponse {

    private Long sessionId;
    private String jobName;
    private QuestionView question;
}
