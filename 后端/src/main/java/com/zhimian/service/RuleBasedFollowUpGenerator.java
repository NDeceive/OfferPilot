package com.zhimian.service;

import com.zhimian.dto.FollowUpResponse;
import com.zhimian.service.followup.FollowupDimension;
import org.springframework.stereotype.Component;

/**
 * 规则化追问生成器（V3）。
 * <p>
 * 大模型不可用或调用失败时使用：按追问方向给一句兜底问题，保证离线演示也能看到多层追问。
 */
@Component
public class RuleBasedFollowUpGenerator {

    /** 回答（去空白后）短于该长度视为过短 */
    public static final int MIN_ANSWER_LENGTH = 15;

    private static final String SOURCE_RULE = "RULE";

    /** 回答过短时的追问 */
    public FollowUpResponse tooShortResponse() {
        return FollowUpResponse.of(
                "你的回答比较简短，能否详细说说你的思路或做法？",
                SOURCE_RULE,
                "回答过于简短，缺少具体细节");
    }

    /** AI 不可用或调用失败时的通用兜底追问 */
    public FollowUpResponse defaultResponse() {
        return FollowUpResponse.of(
                "针对刚才的回答，能否进一步说明你在实现过程中遇到的难点以及是如何解决的？",
                SOURCE_RULE,
                "AI 不可用或调用失败，使用规则兜底追问");
    }

    /** 按方向给兜底追问；方向为空时退回通用问题 */
    public FollowUpResponse responseFor(FollowupDimension dimension, int layer) {
        if (dimension == null) {
            return defaultResponse();
        }
        String question = switch (dimension) {
            case CLARIFY -> "你刚才提到的这一点，能具体说说实际是怎么做的吗？";
            case MECHANISM -> "这个做法为什么能解决问题？背后的原理是什么？";
            case TRADEOFF -> "如果换成另一种方案，你会怎么权衡？各自的代价在哪里？";
            case BOUNDARY -> "在什么情况下这套做法会失效？你会怎么兜底？";
            case TRANSFER -> "如果把规模放大十倍，这套做法需要怎么调整？";
            case CORRECT -> "刚才的说法里有不准确的地方，你能重新梳理一遍吗？";
        };
        FollowUpResponse resp = FollowUpResponse.of(question, SOURCE_RULE,
                "第" + layer + "层 · " + dimension.getLabel() + "方向，规则兜底");
        resp.setDimension(dimension.name());
        return resp;
    }
}
