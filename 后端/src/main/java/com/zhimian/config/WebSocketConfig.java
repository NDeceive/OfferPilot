package com.zhimian.config;

import com.zhimian.ws.MeetingSignalHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import java.util.Arrays;

/**
 * WebSocket 配置：/ws/meeting 会议信令端点。
 * <p>
 * Origin 校验用独立配置 {@code meeting.ws.allowed-origins}（与 HTTP CORS 白名单分开）：
 * 局域网演示时前端 IP 可能天天变，若复刻 CORS 白名单会频繁 403；开发默认 "*"，
 * 生产在 application-prod.yml 注入具体来源（未注入 = 锁死所有跨域握手，fail-closed）。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final MeetingSignalHandler meetingSignalHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Value("${meeting.ws.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        if (origins.length == 0) {
            // 生产未注入白名单：用一个不可能匹配的来源显式锁死，不依赖 Spring 对空数组的处理细节
            origins = new String[]{"https://blocked.invalid"};
        }
        registry.addHandler(meetingSignalHandler, "/ws/meeting")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns(origins);
    }

    /** 64KB 文本帧（SDP 可达数 KB，默认 8KB 会截断）+ 30 分钟空闲超时防僵尸连接 */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(64 * 1024);
        container.setMaxSessionIdleTimeout(30 * 60 * 1000L);
        return container;
    }
}
