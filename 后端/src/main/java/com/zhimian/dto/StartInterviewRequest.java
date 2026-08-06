package com.zhimian.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

/**
 * 开始面试请求。resumeId 由服务端按当前用户解析，不从前端接收。
 */
@Data
public class StartInterviewRequest {

    @NotNull(message = "岗位不能为空")
    private Long jobId;

    /** 难度: 1简单 2中等 3困难，缺省按中等处理 */
    private Integer difficulty;

    /** 面试时长（秒），缺省 1800（30分钟） */
    @Min(value = 300, message = "面试时长不能少于5分钟")
    @Max(value = 7200, message = "面试时长不能超过2小时")
    private Integer durationSeconds;

    /** 模块偏好（可选）：[{code, rank, level}]，缺省使用默认5模块均衡模式 */
    private List<ModulePreferenceItem> modulePreferences;

    @Data
    public static class ModulePreferenceItem {
        private String code;
        private int rank;
        private int level;
    }
}
