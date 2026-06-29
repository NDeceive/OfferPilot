package com.zhimian.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 角色拦截器：在 {@link AuthInterceptor}（已写入 {@link UserContext}）之后执行。
 * <p>
 * 仅对标注了 {@link RequireRole} 的 Controller 方法 / 类生效；未标注的接口直接放行，
 * 因此完全不影响现有学生端接口。校验失败时返回 403，且响应体保持项目统一的
 * {@code {"code":..,"message":..}} 结构，不破坏前端已有错误处理逻辑。
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 预检请求 / 非 Controller 方法（静态资源等）一律放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 方法上的注解优先于类上的注解；都没有则说明该接口不需要角色校验，放行
        RequireRole require = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (require == null) {
            require = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (require == null) {
            return true;
        }

        String role = UserContext.getRole();
        boolean allowed = role != null && Arrays.asList(require.value()).contains(role);
        if (!allowed) {
            writeForbidden(response);
            return false;
        }
        return true;
    }

    /** 返回 403，响应体与全局 Result 结构一致。 */
    private void writeForbidden(HttpServletResponse response) throws Exception {
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"无权限访问，需要更高的角色权限\"}");
    }
}
