package com.zhimian.common;

/** Authenticated user lacks access to the requested resource. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) { super(message); }
}
