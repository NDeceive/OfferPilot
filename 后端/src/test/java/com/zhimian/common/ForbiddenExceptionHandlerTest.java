package com.zhimian.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForbiddenExceptionHandlerTest {
    @Test void forbiddenResourceUsesHttp403AndStableBody() {
        var response = new GlobalExceptionHandler().handleForbidden(new ForbiddenException("无权访问"));
        assertEquals(403, response.getStatusCode().value());
        assertEquals(403, response.getBody().getCode());
    }
}
