package com.zhimian.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

/**
 * 开始面试请求。可选定工作台简历版本；所有权仍由服务端核验。
 */
@Data
public class StartInterviewRequest {

    /** Teaching allocation; its immutable server configuration overrides client settings. */
    private Long assignmentId;

    /** Reuse for retried start requests; different configurations require a new ID. */
    @jakarta.validation.constraints.Size(max = 64)
    private String requestId;

    @NotNull(message = "岗位不能为空")
    private Long jobId;

    /** 未传时沿用旧版“当前简历”流程。 */
    private Long resumeVersionId;

    /** 用户在准备页明确选择“暂不上传”时不使用历史简历。 */
    private Boolean skipResume;

    /** 难度: 1简单 2中等 3困难，缺省按中等处理 */
    @Min(1)
    @Max(3)
    private Integer difficulty;

    /** 面试时长（秒），缺省 1800（30分钟） */
    @Min(value = 300, message = "面试时长不能少于5分钟")
    @Max(value = 7200, message = "面试时长不能超过2小时")
    private Integer durationSeconds;

    /** 模块偏好（可选）：[{code, rank, level}]，缺省使用默认5模块均衡模式 */
    @jakarta.validation.Valid
    @jakarta.validation.constraints.Size(max = 10)
    private List<@jakarta.validation.constraints.NotNull ModulePreferenceItem> modulePreferences;

    @Data
    public static class ModulePreferenceItem {
        @jakarta.validation.constraints.NotBlank
        private String code;
        @Min(1)
        @Max(5)
        private int rank;
        @Min(1)
        @Max(3)
        private int level;
    }
}
