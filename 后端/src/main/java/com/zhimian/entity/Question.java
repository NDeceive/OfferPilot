package com.zhimian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题库题目（题干/答案要点/能力标签/追问提示）
 */
@Data
@TableName("question")
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jobId;
    private String content;
    /** 答案要点，「、」分隔的关键词（规则引擎据此判断回答是否覆盖要点） */
    private String answerPoints;
    private String abilityTag;
    /** 难度: 1简单 2中等 3困难 */
    private Integer difficulty;
    /** 追问条件/方向提示 */
    private String followupHint;
    /** 类型: MAIN主问 FOLLOWUP追问样例 */
    private String type;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
