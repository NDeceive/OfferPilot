package com.zhimian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试问答记录（含主问、追问与考生回答）
 */
@Data
@TableName("interview_message")
public class InterviewMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    /** 关联题库ID（追问/回答沿用所属主问的题目ID） */
    private Long questionId;
    /** 第几轮 */
    private Integer roundNo;
    /** 角色: INTERVIEWER面试官 CANDIDATE考生 */
    private String role;
    /** 类型: OPENING开场 MAIN主问 FOLLOWUP追问 ANSWER回答 SUMMARY总结 */
    private String msgType;
    private String content;
    private String abilityTag;
    private LocalDateTime createTime;
}
