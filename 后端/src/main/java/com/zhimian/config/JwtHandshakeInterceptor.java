package com.zhimian.config;

import com.zhimian.entity.SysUser;
import com.zhimian.mapper.SysUserMapper;
import com.zhimian.service.AccountTokenService;
import com.zhimian.ws.WsPrincipal;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * WebSocket 握手鉴权：token 从 <b>query 参数</b>读取。
 * <p>
 * 取舍：浏览器的 {@code WebSocket} 构造器无法携带自定义请求头，JWT 只能走
 * {@code ws://host/ws/meeting?token=...}；token 会出现在服务端访问日志里，
 * 局域网演示场景可接受，生产环境应改用一次性 ticket 换取连接。
 * <p>
 * 校验逻辑与 {@link AuthInterceptor} 同源的三重检查：
 * parseToken → 查库用户存在且 status==1 → 比对 AccountTokenService 的版本号
 * （改密码/登出会 revoke 版本，旧 token 立即失效）。
 * 注意 WS 握手不经过 MVC 的 HandlerInterceptor，因此必须独立实现本类。
 */
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;
    private final SysUserMapper users;
    private final AccountTokenService accountTokens;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        String token = null;
        if (request instanceof ServletServerHttpRequest servletRequest) {
            token = servletRequest.getServletRequest().getParameter("token");
        }
        Claims claims = token == null || token.isBlank() ? null : jwtUtil.parseToken(token);
        SysUser user = null;
        if (claims != null) {
            try {
                user = users.selectById(Long.valueOf(claims.getSubject()));
                Number version = claims.get("version", Number.class);
                if (user != null && (!Integer.valueOf(1).equals(user.getStatus())
                        || accountTokens.version(user.getId()) != (version == null ? 0 : version.longValue()))) {
                    user = null;
                }
            } catch (NumberFormatException invalidSubject) {
                user = null;
            }
        }
        if (user == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            response.getBody().write("{\"code\":401,\"message\":\"未登录或登录已过期\"}".getBytes(StandardCharsets.UTF_8));
            return false;
        }
        String name = user.getNickname() != null && !user.getNickname().isBlank()
                ? user.getNickname() : user.getUsername();
        attributes.put(WsPrincipal.ATTR, new WsPrincipal(user.getId(), name, user.getRole()));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无后置处理
    }
}
