package com.zhimian.service;

import com.zhimian.dto.StartInterviewRequest;
import com.zhimian.entity.InterviewSession;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewTimingTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validatesInterviewDurationRange() {
        StartInterviewRequest request = new StartInterviewRequest();
        request.setJobId(1L);
        request.setDurationSeconds(299);
        assertFalse(validator.validate(request).isEmpty());
        request.setDurationSeconds(300);
        assertTrue(validator.validate(request).isEmpty());
        request.setDurationSeconds(7201);
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void interviewEndsOnlyWhenConfiguredDurationExpires() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 1, 10, 0);
        InterviewSession session = new InterviewSession();
        session.setStartTime(startedAt);
        session.setDurationSeconds(1800);
        assertFalse(InterviewFlowService.isTimeExceeded(session, startedAt.plusSeconds(1799)));
        assertTrue(InterviewFlowService.isTimeExceeded(session, startedAt.plusSeconds(1800)));
    }
}
