package com.zhimian.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AudioSchemaCompatibilityTest {
    @Test void blobAudioTableIsPreserved() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(0, 0);
        new StudentBackendSchema(jdbc).separateLegacyAudioTable();
        verify(jdbc, never()).execute(anyString());
    }

    @Test void existingRoundRecordingsMoveWithoutLosingTheirIds() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1, 0);
        new StudentBackendSchema(jdbc).separateLegacyAudioTable();
        verify(jdbc).execute("RENAME TABLE interview_answer_audio TO interview_round_audio");
    }

    @Test void migrationIsIdempotent() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1, 1);
        new StudentBackendSchema(jdbc).separateLegacyAudioTable();
        verify(jdbc, never()).execute(anyString());
    }
}
