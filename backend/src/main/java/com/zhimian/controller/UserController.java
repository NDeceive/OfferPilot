package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.config.UserContext;
import com.zhimian.entity.SysUser;
import com.zhimian.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户个人中心接口
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final SysUserMapper userMapper;

    /** 获取当前登录用户信息 */
    @GetMapping("/me")
    public Result<SysUser> me() {
        SysUser user = userMapper.selectById(UserContext.getUserId());
        if (user != null) {
            user.setPassword(null); // 不返回密码
        }
        return Result.success(user);
    }
}
