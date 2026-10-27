package com.zhimian.service;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PracticeJudgingTest {
    @Test void comparisonsDoNotCollapseSignificantWhitespace() {
        assertFalse(PracticeJudging.matches("a b","a\nb","LINE_ENDING"));
        assertFalse(PracticeJudging.matches("a  b","a b","LINE_ENDING"));
        assertTrue(PracticeJudging.matches("a \r\n","a","LINE_ENDING"));
        assertFalse(PracticeJudging.matches("1\n","1","EXACT"));
        assertTrue(PracticeJudging.matches("1  2\n","1\n2","TOKENS"));
        assertThrows(RuntimeException.class,()->PracticeJudging.matches("1","1","UNKNOWN"));
    }
    @Test void infrastructureErrorsAreNotWrongAnswers() {
        assertEquals("SYSTEM_ERROR",PracticeJudging.status(12));
        assertEquals("SYSTEM_ERROR",PracticeJudging.status(13));
        assertEquals("SYSTEM_ERROR",PracticeJudging.status(999));
        assertEquals("COMPILE_ERROR",PracticeJudging.status(6));
        assertEquals("TIME_LIMIT_EXCEEDED",PracticeJudging.status(5));
        assertTrue(PracticeJudging.log("x".repeat(40000)).length()<33000);
    }
    @Test void settlementSeparatesSelfReviewAndSystemFailures() {
        var questions=List.of(Map.of("id",1,"type","coding","latestSubmission",Map.of("id",10,"status","ACCEPTED","isCorrect",1)),Map.of("id",2,"type","knowledge","latestSubmission",Map.of("id",11,"status","SELF_REVIEW")),Map.of("id",3,"type","coding"));
        var tasks=List.of(Map.<String,Object>of("operation","submit","status","SYSTEM_ERROR"));
        var report=PracticeWorkflowService.buildReport(Map.of("questionList",questions),tasks);
        var stats=(Map)report.get("statistics");
        assertEquals(2,stats.get("submitted"));assertEquals(1,stats.get("codingEvaluated"));assertEquals(1,stats.get("selfReview"));assertEquals(0,stats.get("codingFailed"));assertEquals(1,report.get("systemFailureCount"));
    }
}
