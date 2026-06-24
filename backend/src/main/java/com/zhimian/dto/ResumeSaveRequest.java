package com.zhimian.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 简历保存请求
 */
@Data
public class ResumeSaveRequest {

    @NotBlank(message = "简历内容不能为空")
    private String rawText;
}
