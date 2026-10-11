package com.zhimian.ws;

/**
 * WebSocket 握手时解析出的登录身份（token → 用户）。
 * <p>
 * WS 消息线程拿不到 MVC 的 {@code UserContext}（ThreadLocal 不跨线程），
 * 因此身份在握手期固化到 session attributes，后续每个消息都直接读本对象。
 */
public record WsPrincipal(long userId, String name, String role) {

    /** 握手时写入 WebSocketSession attributes、消息处理时读取的身份键 */
    public static final String ATTR = "wsPrincipal";
}
