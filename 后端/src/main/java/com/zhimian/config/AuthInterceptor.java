package com.zhimian.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器：校验 JWT，写入 UserContext
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final com.zhimian.mapper.SysUserMapper users;
    private final com.zhimian.service.AccountTokenService accountTokens;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        Claims claims = token == null ? null : jwtUtil.parseToken(token);
        com.zhimian.entity.SysUser user = null;
        if (claims != null) {
            try {
                user = users.selectById(Long.valueOf(claims.getSubject()));
                Number version = claims.get("version", Number.class);
                if (user != null && (!Integer.valueOf(1).equals(user.getStatus()) ||
                        accountTokens.version(user.getId()) != (version == null ? 0 : version.longValue()))) user = null;
            } catch (NumberFormatException invalidSubject) { user = null; }
        }
        if (user == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\"}");
            return false;
        }
        UserContext.set(user.getId(), user.getRole());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
