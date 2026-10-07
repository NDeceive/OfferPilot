package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.BizException;
import com.zhimian.config.UserContext;
import com.zhimian.dto.ExpressionBatchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExpressionServiceTest {
    @Test void ambiguousSamplesAreCountedSeparatelyAndRawAveragesRemainAvailable() {
        var p=probabilities("neutral");p.put("neutral",.4);p.put("happy",.35);
        var summary=ExpressionService.summarize(List.of(Map.of("faceDetected",true,"probabilities",p)));
        assertEquals(1,summary.get("uncertainCount"));
        assertEquals(0,((Map<?,?>)summary.get("dominantCounts")).get("neutral"));
        assertEquals(.4,((Map<?,?>)summary.get("averageProbabilities")).get("neutral"));
    }
    private Map<String, Double> probabilities(String dominant) {
        Map<String, Double> p = new LinkedHashMap<>();
        ExpressionService.KEYS.forEach(k -> p.put(k, k.equals(dominant) ? 0.7 : 0.05));
        return p;
    }

    @Test void rejectsMalformedProbabilitiesAndExcludesMissingFacesFromStatistics() {
        var happy = probabilities("happy");
        assertDoesNotThrow(() -> ExpressionService.validateProbabilities(true, happy));
        assertThrows(BizException.class, () -> ExpressionService.validateProbabilities(true, Map.of("happy", 1.0)));
        var invalid = new LinkedHashMap<>(happy); invalid.put("sad", Double.NaN);
        assertThrows(BizException.class, () -> ExpressionService.validateProbabilities(true, invalid));
        invalid.put("sad", 0.5);
        assertThrows(BizException.class, () -> ExpressionService.validateProbabilities(true, invalid));
        assertThrows(BizException.class, () -> ExpressionService.validateProbabilities(false, happy));
        assertDoesNotThrow(() -> ExpressionService.validateProbabilities(false, null));
        var summary = ExpressionService.summarize(List.of(
                Map.of("faceDetected", true, "probabilities", happy),
                Map.of("faceDetected", false),
                Map.of("faceDetected", true, "probabilities", probabilities("neutral"))));
        assertEquals(3, summary.get("sampleCount"));
        assertEquals(2, summary.get("detectedCount"));
        assertEquals(1, summary.get("missingCount"));
        assertEquals(0.375, ((Map<?, ?>)summary.get("averageProbabilities")).get("happy"));
        assertEquals(1, ((Map<?, ?>)summary.get("dominantCounts")).get("happy"));
        assertEquals(0, ExpressionService.summarize(List.of()).get("detectedCount"));
    }

    @Test void rejectsAnotherUsersSessionBeforeInsertingSamples() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(8L))).thenReturn(List.of(Map.of("user_id", 99L)));
        UserContext.set(21L, "USER");
        try {
            var service = new ExpressionService(jdbc, new ObjectMapper());
            assertThrows(BizException.class, () -> service.save(8L, new ExpressionBatchRequest(List.of())));
            verify(jdbc, never()).update(anyString(), any(Object[].class));
        } finally { UserContext.clear(); }
    }

    @Test void retriesAtSampleLimitRemainIdempotentButNewSamplesAreRejected() {
        var jdbc = mock(JdbcTemplate.class);
        long now = System.currentTimeMillis();
        when(jdbc.queryForList(anyString(), eq(8L))).thenReturn(List.of(Map.of("user_id", 21L, "start_ms", now)));
        when(jdbc.queryForObject("SELECT COUNT(*) FROM interview_expression_sample WHERE session_id=?", Long.class, 8L)).thenReturn(10000L);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM interview_message WHERE session_id=? AND question_id=? AND round_no=? AND role='INTERVIEWER'",
                Long.class, 8L, -1L, 1)).thenReturn(1L);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM interview_expression_sample WHERE session_id=? AND sample_id=?", Long.class, 8L, "retry-id")).thenReturn(1L);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM interview_expression_sample WHERE session_id=? AND sample_id=?", Long.class, 8L, "new-id")).thenReturn(0L);
        UserContext.set(21L, "USER");
        try {
            var service = new ExpressionService(jdbc, new ObjectMapper());
            assertDoesNotThrow(() -> service.save(8L, new ExpressionBatchRequest(List.of(
                    new ExpressionBatchRequest.Sample("retry-id", -1L, 1, now, false, null)))));
            var rejected=service.save(8L, new ExpressionBatchRequest(List.of(
                    new ExpressionBatchRequest.Sample("new-id", -1L, 1, now, false, null))));
            assertEquals(1,((List<?>)rejected.get("rejected")).size());
            assertEquals(List.of(),rejected.get("acceptedIds"));
        } finally { UserContext.clear(); }
    }
}
