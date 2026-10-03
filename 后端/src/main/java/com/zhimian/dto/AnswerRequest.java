package com.zhimian.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 提交回答请求
 */
@Data
public class AnswerRequest {

    /** 题库题目必传；体验题（AI 生成）可传 null 或 0 */
    private Long questionId;

    /** Round identifies repeated experience questions whose bank ID is always zero. */
    @jakarta.validation.constraints.Min(1)
    private Integer roundNo;

    /** Interviewer message ID; disambiguates a main question and its follow-up. */
    private Long questionInstanceId;

    private boolean skipped;

    /** Reuse this value when retrying the same HTTP submission. */
    @jakarta.validation.constraints.Size(max = 64)
    private String requestId;

    @jakarta.validation.constraints.Size(max = 10000, message = "回答最多10000个字符")
    private String answer;
}
