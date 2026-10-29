package com.zhimian.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 动态追问请求体。
 * <p>
 * 单层追问时代只依赖岗位 / 原始问题 / 回答；多层追问会额外带上层号、方向与此前问答链。
 */
@Data
public class FollowUpRequest {

    /** 目标岗位，如「Java后端开发」 */
    @NotBlank(message = "岗位不能为空")
    private String position;

    /** 面试官提出的原始问题 */
    @NotBlank(message = "问题不能为空")
    private String question;

    /** 考生对该问题的回答 */
    @NotBlank(message = "回答不能为空")
    private String answer;

    /** 题库参考答案（供 DeepSeek 对比面试者回答与标准答案，决定是否追问） */
    private String referenceAnswer;

    /** 第几层，从 1 开始；为空时按单层处理 */
    private Integer layer;

    /** 本轮约定方向：CLARIFY / MECHANISM / TRADEOFF / BOUNDARY / TRANSFER / CORRECT */
    private String dimension;

    /** 此前已问过的方向，逗号分隔 */
    private String askedDimensions;

    /** 此前几轮问答链，已压缩成文本 */
    private String chainText;
}
