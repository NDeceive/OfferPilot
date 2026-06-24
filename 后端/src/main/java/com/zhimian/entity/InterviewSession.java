package com.zhimian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试会话（一次完整模拟面试）
 */
@Data
@TableName("interview_session")
public class InterviewSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long jobId;
    private Long resumeId;
    private Integer difficulty;
    /** ONGOING / FINISHED / ABORTED */
    private String status;
    private Integer isRetrain;
    private String weakTags;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
