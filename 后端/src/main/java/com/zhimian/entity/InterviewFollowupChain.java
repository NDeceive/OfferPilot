package com.zhimian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 多层追问链：每一层追问（以及内容闸门判定「无需追问」）落一条。
 * <p>
 * 与 interview_followup_record 并存：老表仍然每题一条首层记录，教师看板与统计口径不变；
 * 新表记录完整链路，供报告展示与评分使用。
 */
@Data
@TableName("interview_followup_chain")
public class InterviewFollowupChain {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private Long questionId;
    private Integer roundNo;
    /** 第几层，从 1 开始 */
    private Integer layerNo;
    /** 追问方向：CLARIFY / MECHANISM / TRADEOFF / BOUNDARY / TRANSFER / CORRECT */
    private String dimension;
    /** 闸门结果：ASK / STOP_NO_NEED 等 */
    private String gate;
    /** 当层条件触发概率 */
    private Double probability;
    /** 追问问题（STOP 时为空） */
    private String question;
    /** 考生对该层追问的回答 */
    private String answer;
    /** 模型回报的未覆盖要点数量；-1 表示没有拿到 */
    private Integer uncoveredCount;
    /** 来源：AI / RULE */
    private String source;
    private String reason;
    private LocalDateTime createTime;
}
