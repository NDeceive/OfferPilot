package com.zhimian.controller;

import com.zhimian.common.BizException;
import com.zhimian.common.Result;
import com.zhimian.config.UserContext;
import com.zhimian.dto.ProfileUpdateRequest;
import com.zhimian.dto.UserStats;
import com.zhimian.entity.SysUser;
import com.zhimian.mapper.SysUserMapper;
import com.zhimian.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * 用户个人中心接口
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final SysUserMapper userMapper;
    private final StatsService statsService;
    private final BCryptPasswordEncoder passwordEncoder;

    /** 获取当前登录用户信息 */
    @GetMapping("/me")
    public Result<SysUser> me() {
        SysUser user = userMapper.selectById(UserContext.getUserId());
        if (user != null) {
            user.setPassword(null); // 不返回密码
        }
        return Result.success(user);
    }

    /** 当前用户的真实训练统计 */
    @GetMapping("/stats")
    public Result<UserStats> stats() {
        return Result.success(statsService.getMyStats());
    }

    /** 修改个人资料：昵称 / 密码（留空则不改对应项） */
    @PutMapping("/profile")
    public Result<SysUser> updateProfile(@RequestBody ProfileUpdateRequest req) {
        SysUser user = userMapper.selectById(UserContext.getUserId());
        if (user == null) {
            throw new BizException("用户不存在");
        }
        boolean changed = false;
        if (req.getNickname() != null && !req.getNickname().isBlank()) {
            user.setNickname(req.getNickname().trim());
            changed = true;
        }
        if (req.getNewPassword() != null && !req.getNewPassword().isBlank()) {
            if (req.getNewPassword().length() < 6 || req.getNewPassword().length() > 20) {
                throw new BizException("密码长度为 6-20 位");
            }
            user.setPassword(passwordEncoder.encode(req.getNewPassword()));
            changed = true;
        }
        if (!changed) {
            throw new BizException("没有需要修改的内容");
        }
        userMapper.updateById(user);
        user.setPassword(null);
        return Result.success(user);
    }
}
