package com.zhimian.service;

import com.zhimian.config.JwtUtil;
import com.zhimian.dto.RegisterRequest;
import com.zhimian.entity.SysUser;
import com.zhimian.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 公开注册角色白名单：仅 STUDENT / ENTERPRISE；其余（含伪造 TEACHER/ADMIN）静默降级为学生。
 */
class AuthServiceRegisterRoleTest {

    private String registeredRole(String requestedRole) {
        SysUserMapper users = mock(SysUserMapper.class);
        when(users.selectCount(any())).thenReturn(0L);
        BCryptPasswordEncoder encoder = mock(BCryptPasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("hashed");
        AuthService service = new AuthService(users, encoder, mock(JwtUtil.class));

        RegisterRequest req = new RegisterRequest();
        req.setUsername("tester01");
        req.setPassword("secret123");
        req.setRole(requestedRole);
        service.register(req);

        ArgumentCaptor<SysUser> captured = ArgumentCaptor.forClass(SysUser.class);
        verify(users).insert(captured.capture());
        return captured.getValue().getRole();
    }

    @Test
    void enterpriseRoleIsAllowedAndCaseNormalized() {
        assertEquals("ENTERPRISE", registeredRole("enterprise"));
        assertEquals("ENTERPRISE", registeredRole("  Enterprise "));
    }

    @Test
    void missingRoleDefaultsToStudent() {
        assertEquals("STUDENT", registeredRole(null));
        assertEquals("STUDENT", registeredRole(""));
    }

    @Test
    void privilegedAndUnknownRolesSilentlyDegradeToStudent() {
        assertEquals("STUDENT", registeredRole("ADMIN"));
        assertEquals("STUDENT", registeredRole("TEACHER"));
        assertEquals("STUDENT", registeredRole("hacker"));
    }
}
