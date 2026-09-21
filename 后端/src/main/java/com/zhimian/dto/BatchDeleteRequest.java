package com.zhimian.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量删除面试会话请求体。
 */
@Data
public class BatchDeleteRequest {

    /** 待删除的会话 ID 列表 */
    @NotEmpty(message = "请选择要删除的记录")
    private List<Long> sessionIds;
}
