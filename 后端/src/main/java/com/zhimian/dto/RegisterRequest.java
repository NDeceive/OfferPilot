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

    @Size(max = 30)
    private String nickname;

    @jakarta.validation.constraints.Email
    @Size(max = 100)
    private String email;

    /** 注册身份：STUDENT / ENTERPRISE（可空，默认 STUDENT） */
    @Size(max = 20)
    private String role;

    // 安全说明：公开注册仅允许 STUDENT / ENTERPRISE 两种身份（见 AuthService.register 白名单），
    // 其余任何值——包括尝试伪造的 TEACHER / ADMIN——一律静默降级为 STUDENT。
    // 教师/管理员账号仍只能由种子数据、数据库操作或管理后台创建，杜绝越权注册。
}
