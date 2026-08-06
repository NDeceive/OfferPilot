package com.zhimian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户模块选择配置（每次面试前选择5个模块+排序+评级）
 */
@Data
@TableName("interview_module_preference")
public class InterviewModulePreference {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 面试会话ID（一对一） */
    private Long sessionId;

    /** 用户ID */
    private Long userId;

    /** JSON: [{code, rank, level}] */
    private String modulesJson;

    private LocalDateTime createTime;
}
