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
    /** 岗位族名称（后端开发/前端与客户端开发/全栈开发/算法与人工智能/产品经理/数据分析/软件测试） */
    private String family;
    /** 岗位编码（如 BE-JAVA, FE-WEB 等） */
    private String code;
    private String description;
    /** 岗位能力要求（JSON 数组字符串） */
    private String abilities;
    /** 岗位关键词（JSON 数组字符串，用于 RAG 匹配） */
    private String keywords;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
