package com.zhimian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评分配置快照（面试开始时冻结权重+目标线，保证历史报告可复现）
 */
@Data
@TableName("interview_score_config_snapshot")
public class InterviewScoreConfigSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 面试会话ID（一对一） */
    private Long sessionId;

    /** JSON: {moduleCode: weight} */
    private String weightsJson;

    /** JSON: {moduleCode: targetScore} */
    private String targetsJson;

    private LocalDateTime createTime;
}
