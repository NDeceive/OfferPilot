package com.zhimian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 参会记录（断线重进按 (meeting_id, user_id) upsert，不产生重复行）
 */
@Data
@TableName("meeting_participant")
public class MeetingParticipant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long meetingId;
    private Long userId;
    /** 进房时快照的昵称，便于历史记录展示 */
    private String displayName;
    /** STUDENT / TEACHER / ENTERPRISE / ADMIN */
    private String role;
    private LocalDateTime joinTime;
    private LocalDateTime leaveTime;
}
