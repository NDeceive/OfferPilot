package com.zhimian.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色校验注解：标注在 Controller 类或方法上，限定可访问的角色。
 * <p>
 * 由 {@link RoleInterceptor} 在 {@link AuthInterceptor} 之后执行校验。
 * <b>只有显式标注本注解的接口才会被校验</b>，未标注的接口保持原样、不受影响，
 * 因此不会改变任何现有学生端接口的行为。
 * <p>
 * 支持多个角色，命中其一即放行。Phase 5.1 仅提供机制、暂未在任何接口启用；
 * 后续教师端接口可直接使用，例如：
 * <pre>{@code @RequireRole({"TEACHER", "ADMIN"})}</pre>
 * 方法上的注解优先于类上的注解。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许访问的角色列表，命中其一即放行（如 "TEACHER"、"ADMIN"）。 */
    String[] value();
}
