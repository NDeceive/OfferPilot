package com.zhimian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历 / 个人画像
 */
@Data
@TableName("resume")
public class Resume {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    /** 简历原文 */
    private String rawText;
    /** 提取的项目经历（JSON 数组字符串） */
    private String projects;
    /** 提取的技能关键词（JSON 数组字符串） */
    private String skills;
    /** 画像关键词（JSON 数组字符串） */
    private String keywords;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
