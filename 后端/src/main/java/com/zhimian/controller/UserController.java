package com.zhimian.controller;

import com.zhimian.common.BizException;
import com.zhimian.common.Result;
import com.zhimian.config.UserContext;
import com.zhimian.dto.ProfileUpdateRequest;
import com.zhimian.dto.CareerProfileRequest;
import com.zhimian.service.CareerProfileService;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;
import com.zhimian.dto.UserStats;
import com.zhimian.entity.SysUser;
import com.zhimian.mapper.SysUserMapper;
import com.zhimian.service.StatsService;
import com.zhimian.service.StatsService.RadarProfile;
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
    private final com.zhimian.service.AccountTokenService accountTokens;
    private final StatsService statsService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final CareerProfileService careerProfileService;

    /** 获取当前登录用户信息 */
    @GetMapping("/me")
    public Result<SysUser> me() {
        SysUser user = userMapper.selectById(UserContext.getUserId());
        if (user != null) {
            user.setPassword(null); // 不返回密码
            String avatar = careerProfileService.avatar();
            if (avatar != null) user.setAvatar(avatar);
        }
        return Result.success(user);
    }

    /** 当前用户的真实训练统计 */
    @GetMapping("/stats")
    public Result<UserStats> stats() {
        return Result.success(statsService.getMyStats());
    }

    /** 修改个人资料：昵称 / 密码（留空则不改对应项） */
    @org.springframework.transaction.annotation.Transactional
    @PutMapping("/profile")
    public Result<SysUser> updateProfile(@RequestBody ProfileUpdateRequest req) {
        SysUser user = userMapper.selectById(UserContext.getUserId());
        if (user == null) {
            throw new BizException("用户不存在");
        }
        boolean changed = false;
        if (req.getNickname() != null && !req.getNickname().isBlank()) {
            if (req.getNickname().trim().length() > 30) throw new BizException("昵称最多 30 个字符");
            user.setNickname(req.getNickname().trim());
            changed = true;
        }
        if (req.getNewPassword() != null && !req.getNewPassword().isBlank()) {
            if (req.getCurrentPassword() == null || !passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
                throw new BizException("当前密码不正确");
            }
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
        if (req.getNewPassword() != null && !req.getNewPassword().isBlank()) accountTokens.revoke(user.getId());
        user.setPassword(null);
        return Result.success(user);
    }

    /** 当前账号的长期求职档案 */
    @GetMapping("/career-profile")
    public Result<CareerProfileRequest> careerProfile() {
        return Result.success(careerProfileService.get());
    }

    @PutMapping("/career-profile")
    public Result<CareerProfileRequest> saveCareerProfile(@Valid @RequestBody CareerProfileRequest profile) {
        return Result.success(careerProfileService.save(profile));
    }

    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(careerProfileService.uploadAvatar(file));
    }

    /** 首页能力雷达数据（最新一次面试的模块得分） */
    @GetMapping("/dashboard/profile")
    public Result<RadarProfile> dashboardProfile() {
        return Result.success(statsService.getRadarProfile());
    }
}
