package com.zhimian.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度为 3-20 位")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度为 6-20 位")
    private String password;

    private String nickname;

    // 安全说明：公开注册接口不再接受 role 字段。
    // 任何通过公开注册创建的账号一律为 STUDENT，管理员/教师/企业账号
    // 只能由种子数据、数据库操作或后续的管理后台创建，杜绝越权注册。
}
