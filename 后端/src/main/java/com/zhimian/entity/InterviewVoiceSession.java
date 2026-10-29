package com.zhimian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 会话级语音开关与状态 */
@Data
@TableName("interview_voice_session")
public class InterviewVoiceSession {

    @TableId(type = IdType.INPUT)
    private Long sessionId;

    private Integer enabled;
    /** PENDING / ANALYZED / FAILED / DISABLED */
    private String status;
    private String failureReason;
    private LocalDateTime updateTime;
}
