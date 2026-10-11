package com.zhimian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 企业端视频会议（教师/学生凭会议号进房）
 */
@Data
@TableName("meeting")
public class Meeting {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 8 位会议号（无歧义字母表，不含 I/O/0/1），存库不带分隔符 */
    private String code;
    private String title;
    /** 发起人（企业/教师/管理员） */
    private Long hostId;
    private Long jobId;
    /** OPEN / ENDED */
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
