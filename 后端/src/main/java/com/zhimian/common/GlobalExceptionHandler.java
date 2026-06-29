package com.zhimian.common;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        return Result.error(400, e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValid(Exception e) {
        String msg = "参数校验失败";
        if (e instanceof MethodArgumentNotValidException ex && ex.getBindingResult().getFieldError() != null) {
            msg = ex.getBindingResult().getFieldError().getDefaultMessage();
        }
        return Result.error(400, msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        // 为每个未知异常生成 traceId：服务端日志记录完整堆栈，前端只返回该 ID 便于排查，
        // 绝不把原始异常信息（e.getMessage()）暴露给前端，避免泄露内部细节或出现“系统异常:null”。
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try {
            MDC.put("traceId", traceId);
            log.error("未知系统异常 traceId={}", traceId, e);
        } finally {
            MDC.remove("traceId");
        }
        return Result.error(500, "系统繁忙，请稍后重试 (traceId: " + traceId + ")");
    }
}
