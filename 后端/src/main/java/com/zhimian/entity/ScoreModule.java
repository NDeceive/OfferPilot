package com.zhimian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评价模块字典（10个固定模块）
 */
@Data
@TableName("score_module")
public class ScoreModule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模块编码：technical_base / technical_depth / ... */
    private String code;

    /** 模块中文名 */
    private String name;

    /** 评估重点说明 */
    private String description;

    /** 排序 */
    private Integer sortOrder;

    private LocalDateTime createTime;
}
