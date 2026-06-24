package com.zhimian.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 开始面试请求。resumeId 由服务端按当前用户解析，不从前端接收。
 */
@Data
public class StartInterviewRequest {

    @NotNull(message = "岗位不能为空")
    private Long jobId;

    /** 难度: 1简单 2中等 3困难，缺省按中等处理 */
    private Integer difficulty;
}
