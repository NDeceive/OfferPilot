package com.zhimian.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DashboardOverviewResponse {
    private Summary summary = new Summary();
    private NextAction nextAction = new NextAction();
    private List<TrendDay> trend = new ArrayList<>();
    private List<RecentInterview> recentInterviews = new ArrayList<>();
    private LatestInsight latestInsight;

    @Data
    public static class Summary {
        private long completedCount;
        private long recentCount;
        private double averageScore;
        private double bestScore;
        private String bestJobName;
        private int streakDays;
    }

    @Data
    public static class NextAction {
        private String type;
        private String title;
        private String description;
        private String route;
    }

    @Data
    public static class TrendDay {
        private LocalDate date;
        private int count;
        private double averageScore;
    }

    @Data
    public static class RecentInterview {
        private Long sessionId;
        private Long reportId;
        private Long jobId;
        private String jobName;
        private String status;
        private Integer difficulty;
        private Double score;
        private long durationSeconds;
        private LocalDateTime startTime;
        private String iconKey;
        private String specialtyKey;
        private String themeKey;
    }

    @Data
    public static class LatestInsight {
        private Long reportId;
        private String jobName;
        private double totalScore;
        private String strongestDimension;
        private double strongestScore;
        private String weakestDimension;
        private double weakestScore;
        private String suggestion;
        private List<ReportDimensionView> dimensions = new ArrayList<>();
    }
}
