package com.zhimian.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 简历保存请求
 */
@Data
public class ResumeSaveRequest {

    @NotBlank(message = "简历内容不能为空")
    @Size(min = 30, max = 30000, message = "简历内容须为 30-30000 个字符")
    private String rawText;
}
