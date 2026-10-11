package com.zhimian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会议中的建议留言（targetUserId 为空 = 面向全体）
 */
@Data
@TableName("meeting_advice")
public class MeetingAdvice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long meetingId;
    /** 建议作者（企业/教师/管理员） */
    private Long authorId;
    private Long targetUserId;
    private String content;
    private LocalDateTime createdAt;
}
