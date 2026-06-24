package com.zhimian.dto;

import lombok.Data;

/**
 * 个人资料修改请求。昵称与密码均为可选，留空则不修改对应项。
 */
@Data
public class ProfileUpdateRequest {
    /** 新昵称（可选） */
    private String nickname;
    /** 新密码（可选，留空不改） */
    private String newPassword;
}
