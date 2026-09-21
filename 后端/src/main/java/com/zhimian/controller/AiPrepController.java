package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.config.UserContext;
import com.zhimian.dto.AiPrepRequest;
import com.zhimian.service.AiPrepService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * AI 面试教练接口。
 * <p>
 * 路径落在 {@code /api/ai/**}，被 {@code WebConfig} 的拦截器覆盖，因此自动需要 JWT —— 这也是
 * 前端用 fetch 而不是 EventSource 的原因：EventSource 带不了 Authorization 头，
 * 要给它开 {@code ?token=} 旁路会把所有接口的安全面一起拉低。
 * <p>
 * ⚠️ <b>本类是异步（SSE）接口，不要往里加计数 / 限流 / 「仅执行一次」之类的逻辑。</b>
 * {@link SseEmitter} 走 Servlet 异步派发，ASYNC dispatch 会<b>再跑一遍</b> {@code preHandle}，
 * 加上首次派发，一次请求会经过拦截器两次。当前 {@code AuthInterceptor} 是幂等的
 * （每次都从 header 重新解析 JWT 再 set 一次 ThreadLocal），重复执行无害；
 * 但任何带副作用的逻辑放在这里都会被悄悄执行两遍。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiPrepController {

    /** 单次话术最多允许挂多久（毫秒），超时由容器兜底收尾 */
    private static final long STREAM_TIMEOUT_MS = 60_000L;

    private final AiPrepService aiPrepService;

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(aiPrepService.status());
    }

    /**
     * 取当前阶段的一句 AI 话术（流式）。
     * <p>
     * 恒定返回 200 + {@code text/event-stream}：AI 不启用、调用失败、并发已满都通过流内的
     * {@code meta} / {@code fallback} 事件表达，<b>不用状态码</b> —— 流一旦开始，
     * 中途失败根本无法再改状态码。
     */
    @PostMapping(value = "/prep/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter prepStream(@Valid @RequestBody AiPrepRequest req) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        // 必须在 Tomcat 线程上取：UserContext 是 ThreadLocal，异步 worker 里恒为 null
        Long userId = UserContext.getUserId();
        aiPrepService.streamPrep(req, userId, emitter);
        return emitter;
    }
}
