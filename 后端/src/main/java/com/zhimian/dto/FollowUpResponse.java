package com.zhimian.dto;

import lombok.Data;

/**
 * 动态追问响应体。
 * source: AI 表示由 DeepSeek 生成；RULE 表示规则化兜底生成。
 */
@Data
public class FollowUpResponse {

    /** 生成的追问问题 */
    private String followUpQuestion;

    /** 追问来源：AI / RULE */
    private String source;

    /** 触发追问的原因说明 */
    private String triggerReason;

    /** 本层实际使用的追问方向 */
    private String dimension;

    /** 模型是否判定本轮回答存在事实性错误 */
    private boolean errorDetected;

    /** 模型回报的未覆盖要点数量；-1 表示没有拿到 */
    private int uncoveredCount = -1;

    public static FollowUpResponse of(String followUpQuestion, String source, String triggerReason) {
        FollowUpResponse r = new FollowUpResponse();
        r.followUpQuestion = followUpQuestion;
        r.source = source;
        r.triggerReason = triggerReason;
        return r;
    }
}
