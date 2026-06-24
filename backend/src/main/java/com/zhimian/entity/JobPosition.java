package com.zhimian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 岗位
 */
@Data
@TableName("job_position")
public class JobPosition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String category;
    private String description;
    /** 岗位能力要求（JSON 数组字符串） */
    private String abilities;
    /** 岗位关键词（JSON 数组字符串，用于 RAG 匹配） */
    private String keywords;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
