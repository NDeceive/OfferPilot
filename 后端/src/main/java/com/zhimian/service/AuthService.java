package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhimian.common.BizException;
import com.zhimian.config.JwtUtil;
import com.zhimian.dto.LoginRequest;
import com.zhimian.dto.LoginResponse;
import com.zhimian.dto.RegisterRequest;
import com.zhimian.entity.SysUser;
import com.zhimian.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务：注册 / 登录
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /** 注册 */
    public void register(RegisterRequest req) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (count != null && count > 0) {
            throw new BizException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail() == null || req.getEmail().isBlank() ? null : req.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname() != null ? req.getNickname() : req.getUsername());
        // 安全：公开注册只允许 STUDENT / ENTERPRISE 两种身份（企业端会议需要企业账号），
        // 其余任何值——包括尝试伪造的 TEACHER / ADMIN——一律静默降级为学生账号；
        // 教师/管理员仍只能通过种子数据/DB/管理后台创建，杜绝越权注册。
        String requested = req.getRole() == null ? "" : req.getRole().trim().toUpperCase(java.util.Locale.ROOT);
        user.setRole(java.util.Set.of("STUDENT", "ENTERPRISE").contains(requested) ? requested : "STUDENT");
        user.setStatus(1);
        userMapper.insert(user);
    }

    /** 登录 */
    public LoginResponse login(LoginRequest req) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException("账号已被禁用");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getNickname(), user.getRole());
    }
}
