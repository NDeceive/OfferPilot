package com.zhimian.service;

import com.zhimian.entity.InterviewSession;
import com.zhimian.entity.JobPosition;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DashboardServiceTest {
    @Test
    void countsConsecutiveTrainingDaysEndingToday() {
        assertEquals(2, DashboardService.calculateStreak(List.of(
                sessionAt(LocalDateTime.now()),
                sessionAt(LocalDateTime.now().minusDays(1)),
                sessionAt(LocalDateTime.now().minusDays(3)))));
    }

    @Test
    void derivesVisualMetadataFromV2JobCode() {
        JobPosition javaJob = new JobPosition();
        javaJob.setCode("BE-JAVA");
        assertEquals("openjdk", DashboardService.iconKey(javaJob));
        assertEquals("jade", DashboardService.themeKey(javaJob));

        JobPosition nlpJob = new JobPosition();
        nlpJob.setCode("ALG-NLP");
        assertEquals("nlp", DashboardService.iconKey(nlpJob));
        assertEquals("violet", DashboardService.themeKey(nlpJob));
    }

    private InterviewSession sessionAt(LocalDateTime time) {
        InterviewSession session = new InterviewSession();
        session.setStatus("FINISHED");
        session.setEndTime(time);
        return session;
    }
}
