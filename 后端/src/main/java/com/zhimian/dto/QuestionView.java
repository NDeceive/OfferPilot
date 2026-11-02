package com.zhimian.dto;

import lombok.Data;

/**
 * 面试问题视图（开场题/下一题/追问复用此结构）
 */
@Data
public class QuestionView {
    private Long questionInstanceId;
    private String interviewStage;

    private Long id;
    private String content;
    private String abilityTag;
    private Integer roundNo;
    /** 题目类型: SKILL=题库题目, EXPERIENCE=体验式 */
    private String questionType;
}
