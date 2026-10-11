package com.zhimian.config;

import com.zhimian.ws.MeetingRoomRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 会议模块独立配置：房间名册单例。
 * <p>
 * 单独成类而不是放进 {@link WebSocketConfig}：MeetingService 要注入名册，
 * WebSocketConfig 又要注入 MeetingSignalHandler，若名册 @Bean 定义在
 * WebSocketConfig 中，就形成
 * WebSocketConfig → Handler → Service → 名册(WebSocketConfig) 的循环依赖。
 */
@Configuration
public class MeetingConfig {

    /** 纯内存房间名册：HTTP 侧（进会预检报人数）与 WS 侧共享同一实例 */
    @Bean
    public MeetingRoomRegistry meetingRoomRegistry() {
        return new MeetingRoomRegistry();
    }
}
