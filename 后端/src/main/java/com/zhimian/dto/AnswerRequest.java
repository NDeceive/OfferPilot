package com.zhimian.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 提交回答请求
 */
@Data
public class AnswerRequest {

    @NotNull(message = "题目ID不能为空")
    private Long questionId;

    @NotBlank(message = "回答内容不能为空")
    private String answer;
}
