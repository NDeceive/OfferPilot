package com.zhimian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 作答录音保留期限 */
@Data
@TableName("interview_voice_retention")
public class InterviewVoiceRetention {

    @TableId(type = IdType.INPUT)
    private Long sessionId;

    /** 0 表示永久；null 表示没设过 */
    private Integer retainDays;
    /** 到点清理；永久为 null */
    private LocalDateTime expireAt;
    private LocalDateTime updateTime;
}
