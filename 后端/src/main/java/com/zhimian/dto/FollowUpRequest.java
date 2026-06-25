package com.zhimian.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 动态追问请求体。无状态接口：仅依赖岗位 / 原始问题 / 回答，不依赖会话或题库。
 */
@Data
public class FollowUpRequest {

    /** 目标岗位，如「Java后端开发」 */
    @NotBlank(message = "岗位不能为空")
    private String position;

    /** 面试官提出的原始问题 */
    @NotBlank(message = "原始问题不能为空")
    private String question;

    /** 考生对该问题的回答 */
    @NotBlank(message = "回答不能为空")
    private String answer;
}
