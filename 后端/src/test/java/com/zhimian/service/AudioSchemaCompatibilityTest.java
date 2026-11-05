package com.zhimian.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

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

    @Test void missingAudioTableNeedsNoRepair() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
        new StudentBackendSchema(jdbc).ensureAnswerAudioSchema();
        verify(jdbc, never()).queryForList(anyString());
        verify(jdbc, never()).execute(anyString());
    }

    @Test void oldPerRoundUniqueKeyIsUpgradedToPerSegment() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1, 0);
        when(jdbc.queryForList(anyString())).thenReturn(List.<Map<String, Object>>of(
                Map.of("index_name", "uk_audio_session_round", "cols", "session_id,round_no")));

        new StudentBackendSchema(jdbc).ensureAnswerAudioSchema();

        var order = inOrder(jdbc);
        order.verify(jdbc).execute("ALTER TABLE interview_round_audio "
                + "ADD COLUMN answer_seq INT NOT NULL DEFAULT 0 AFTER round_no");
        order.verify(jdbc).execute("ALTER TABLE interview_round_audio DROP INDEX `uk_audio_session_round`");
        order.verify(jdbc).execute("ALTER TABLE interview_round_audio ADD UNIQUE KEY uk_audio_session_round_seq "
                + "(session_id, round_no, answer_seq)");
    }

    @Test void perSegmentSchemaIsLeftAlone() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1, 1);
        when(jdbc.queryForList(anyString())).thenReturn(List.<Map<String, Object>>of(
                Map.of("index_name", "uk_audio_session_round_seq", "cols", "session_id,round_no,answer_seq")));

        new StudentBackendSchema(jdbc).ensureAnswerAudioSchema();

        verify(jdbc, never()).execute(anyString());
    }

    @Test void oldKeyIsDroppedEvenWhenSegmentKeyAlreadyExists() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1, 1);
        when(jdbc.queryForList(anyString())).thenReturn(List.<Map<String, Object>>of(
                Map.of("index_name", "uk_audio_session_round", "cols", "session_id,round_no"),
                Map.of("index_name", "uk_audio_session_round_seq", "cols", "session_id,round_no,answer_seq")));

        new StudentBackendSchema(jdbc).ensureAnswerAudioSchema();

        verify(jdbc).execute("ALTER TABLE interview_round_audio DROP INDEX `uk_audio_session_round`");
        verify(jdbc, never()).execute("ALTER TABLE interview_round_audio ADD UNIQUE KEY uk_audio_session_round_seq "
                + "(session_id, round_no, answer_seq)");
    }
}
