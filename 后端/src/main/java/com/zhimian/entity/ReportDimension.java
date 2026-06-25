package com.zhimian.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 报告评分维度明细（雷达图数据源），一份报告固定 5 条。
 */
@Data
@TableName("report_dimension")
public class ReportDimension {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reportId;
    /** 维度名：专业知识掌握/项目实践表达/逻辑表达能力/岗位匹配程度/动态追问应对 */
    private String dimension;
    /** 该维度得分(0-100) */
    private BigDecimal score;
    /** 等级描述：优秀/良好/合格/待提升 */
    private String level;
    /** 评分解释 */
    private String explanation;
}
