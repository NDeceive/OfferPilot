package com.zhimian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 追问记录（实验分析与论文写作用）。
 * 仅在真实面试流程「确实生成了追问」时落库一条，区分 AI 生成与规则兜底。
 * 该表不参与面试主流程逻辑，纯粹用于事后统计/复盘，不影响接口返回结构。
 */
@Data
@TableName("interview_followup_record")
public class InterviewFollowupRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID（来自会话） */
    private Long userId;
    /** 会话ID */
    private Long sessionId;
    /** 岗位ID（来自会话） */
    private Long jobId;
    /** 关联题库ID（被追问的主问题目ID） */
    private Long questionId;
    /** 岗位名称（追问 position） */
    private String position;
    /** 原始主问题题干 */
    private String originalQuestion;
    /** 考生原始回答 */
    private String userAnswer;
    /** 生成的追问问题 */
    private String followUpQuestion;
    /** 追问来源: AI / RULE */
    private String source;
    /** 触发追问/兜底的原因说明 */
    private String triggerReason;
    /** 能力标签 */
    private String abilityTag;
    /** 创建时间（由数据库默认值填充） */
    private LocalDateTime createTime;
}
