package com.zhimian.dto;

import lombok.Data;

/**
 * 面试问题视图（开场题/下一题/追问复用此结构）
 */
@Data
public class QuestionView {

    private Long id;
    private String content;
    private String abilityTag;
    private Integer roundNo;
}
