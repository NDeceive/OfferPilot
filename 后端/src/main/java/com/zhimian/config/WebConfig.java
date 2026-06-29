package com.zhimian.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Web 配置：跨域、拦截器、密码编码器
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final RoleInterceptor roleInterceptor;

    /**
     * CORS 允许的前端来源（逗号分隔），来自配置 cors.allowed-origins。
     * 开发默认本地 Vite；生产由 application-prod.yml 注入固定白名单，不用通配符。
     */
    @Value("${cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    /** 不需要登录就能访问的路径 */
    private static final String[] WHITE_LIST = {
            "/api/auth/login",
            "/api/auth/register",
            "/error"
    };

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(WHITE_LIST);
        // 角色拦截器在登录拦截器之后执行（依赖 UserContext 已写入角色）。
        // 仅对标注 @RequireRole 的接口生效，未标注接口不受影响。
        registry.addInterceptor(roleInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(WHITE_LIST);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 用配置的固定白名单替代通配符 "*"：与 allowCredentials(true) 兼容且更安全。
        // 白名单为空（生产未注入）时 = 不放行任何跨域来源（fail-closed），
        // 同域部署的前端无需跨域，不受影响。
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        registry.addMapping("/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
