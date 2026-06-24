package com.zhimian.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录响应：token + 用户基本信息
 */
@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private Long userId;
    private String username;
    private String nickname;
    private String role;
}
