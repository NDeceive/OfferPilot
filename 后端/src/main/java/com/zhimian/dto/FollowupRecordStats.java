package com.zhimian.dto;

import lombok.Data;

/**
 * 追问记录统计（AI vs RULE 占比，供实验分析 / 论文写作用）。
 */
@Data
public class FollowupRecordStats {

    /** 总记录数 */
    private Long totalCount;
    /** AI 生成的追问数 */
    private Long aiCount;
    /** 规则兜底生成的追问数 */
    private Long ruleCount;
    /** AI 占比（0~1，保留 4 位小数）；无记录时为 0 */
    private Double aiRatio;
    /** 规则占比（0~1，保留 4 位小数）；无记录时为 0 */
    private Double ruleRatio;
}
