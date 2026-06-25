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

    public static FollowUpResponse of(String followUpQuestion, String source, String triggerReason) {
        FollowUpResponse r = new FollowUpResponse();
        r.followUpQuestion = followUpQuestion;
        r.source = source;
        r.triggerReason = triggerReason;
        return r;
    }
}
