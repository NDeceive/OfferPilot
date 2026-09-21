package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhimian.config.UserContext;
import com.zhimian.dto.DashboardOverviewResponse;
import com.zhimian.dto.ReportDetailResponse;
import com.zhimian.dto.ReportDimensionView;
import com.zhimian.entity.InterviewReport;
import com.zhimian.entity.InterviewSession;
import com.zhimian.entity.JobPosition;
import com.zhimian.entity.Resume;
import com.zhimian.mapper.InterviewReportMapper;
import com.zhimian.mapper.InterviewSessionMapper;
import com.zhimian.mapper.JobPositionMapper;
import com.zhimian.mapper.ResumeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final InterviewSessionMapper sessionMapper;
    private final InterviewReportMapper reportMapper;
    private final JobPositionMapper jobMapper;
    private final ResumeMapper resumeMapper;
    private final ReportService reportService;

    public DashboardOverviewResponse getOverview() {
        Long userId = UserContext.getUserId();
        List<InterviewSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, userId)
                        .orderByDesc(InterviewSession::getStartTime));
        List<InterviewReport> reports = reportMapper.selectList(
                new LambdaQueryWrapper<InterviewReport>()
                        .eq(InterviewReport::getUserId, userId)
                        .orderByDesc(InterviewReport::getCreateTime));
        Map<Long, InterviewReport> reportsBySession = new HashMap<>();
        reports.forEach(report -> reportsBySession.put(report.getSessionId(), report));
        Map<Long, JobPosition> jobs = loadJobs(sessions);

        DashboardOverviewResponse response = new DashboardOverviewResponse();
        response.setSummary(buildSummary(sessions, reports, jobs));
        response.setTrend(buildTrend(sessions, reportsBySession));
        response.setRecentInterviews(buildRecent(sessions, reportsBySession, jobs));
        response.setLatestInsight(buildLatestInsight(reports));
        response.setNextAction(buildNextAction(userId, sessions, response.getLatestInsight()));
        return response;
    }

    private Map<Long, JobPosition> loadJobs(List<InterviewSession> sessions) {
        Set<Long> ids = new HashSet<>();
        sessions.forEach(session -> {
            if (session.getJobId() != null) ids.add(session.getJobId());
        });
        Map<Long, JobPosition> jobs = new HashMap<>();
        if (!ids.isEmpty()) jobMapper.selectBatchIds(ids).forEach(job -> jobs.put(job.getId(), job));
        return jobs;
    }

    private DashboardOverviewResponse.Summary buildSummary(
            List<InterviewSession> sessions, List<InterviewReport> reports, Map<Long, JobPosition> jobs) {
        DashboardOverviewResponse.Summary summary = new DashboardOverviewResponse.Summary();
        LocalDate since = LocalDate.now().minusDays(29);
        List<InterviewSession> finished = sessions.stream()
                .filter(session -> "FINISHED".equals(session.getStatus()))
                .toList();
        summary.setCompletedCount(finished.size());
        summary.setRecentCount(finished.stream()
                .filter(session -> eventDate(session) != null && !eventDate(session).isBefore(since))
                .count());
        summary.setStreakDays(calculateStreak(finished));

        List<InterviewReport> scored = reports.stream().filter(r -> r.getTotalScore() != null).toList();
        if (!scored.isEmpty()) {
            summary.setAverageScore(round1(scored.stream()
                    .mapToDouble(r -> r.getTotalScore().doubleValue()).average().orElse(0)));
            InterviewReport best = scored.stream()
                    .max(Comparator.comparing(InterviewReport::getTotalScore)).orElseThrow();
            summary.setBestScore(best.getTotalScore().doubleValue());
            InterviewSession session = sessions.stream()
                    .filter(item -> item.getId().equals(best.getSessionId())).findFirst().orElse(null);
            JobPosition job = session == null ? null : jobs.get(session.getJobId());
            summary.setBestJobName(job == null ? "未知岗位" : job.getName());
        }
        return summary;
    }

    private List<DashboardOverviewResponse.TrendDay> buildTrend(
            List<InterviewSession> sessions, Map<Long, InterviewReport> reportsBySession) {
        LocalDate start = LocalDate.now().minusDays(29);
        Map<LocalDate, List<Double>> days = new LinkedHashMap<>();
        for (int i = 0; i < 30; i++) days.put(start.plusDays(i), new ArrayList<>());
        for (InterviewSession session : sessions) {
            LocalDate date = eventDate(session);
            if (!"FINISHED".equals(session.getStatus()) || date == null || date.isBefore(start)) continue;
            InterviewReport report = reportsBySession.get(session.getId());
            days.get(date).add(report == null || report.getTotalScore() == null
                    ? 0 : report.getTotalScore().doubleValue());
        }
        List<DashboardOverviewResponse.TrendDay> result = new ArrayList<>();
        days.forEach((date, scores) -> {
            DashboardOverviewResponse.TrendDay day = new DashboardOverviewResponse.TrendDay();
            day.setDate(date);
            day.setCount(scores.size());
            day.setAverageScore(round1(scores.stream().filter(score -> score > 0)
                    .mapToDouble(Double::doubleValue).average().orElse(0)));
            result.add(day);
        });
        return result;
    }

    private List<DashboardOverviewResponse.RecentInterview> buildRecent(
            List<InterviewSession> sessions,
            Map<Long, InterviewReport> reportsBySession,
            Map<Long, JobPosition> jobs) {
        List<DashboardOverviewResponse.RecentInterview> result = new ArrayList<>();
        for (InterviewSession session : sessions.stream().limit(4).toList()) {
            JobPosition job = jobs.get(session.getJobId());
            InterviewReport report = reportsBySession.get(session.getId());
            DashboardOverviewResponse.RecentInterview item = new DashboardOverviewResponse.RecentInterview();
            item.setSessionId(session.getId());
            item.setReportId(report == null ? null : report.getId());
            item.setJobId(session.getJobId());
            item.setJobName(job == null ? "未知岗位" : job.getName());
            item.setStatus(session.getStatus());
            item.setDifficulty(session.getDifficulty());
            item.setScore(report == null || report.getTotalScore() == null
                    ? null : report.getTotalScore().doubleValue());
            item.setDurationSeconds(actualDuration(session));
            item.setStartTime(session.getStartTime());
            item.setIconKey(iconKey(job));
            item.setSpecialtyKey(job == null ? "code" : valueOr(job.getCode(), "code").toLowerCase());
            item.setThemeKey(themeKey(job));
            result.add(item);
        }
        return result;
    }

    private DashboardOverviewResponse.LatestInsight buildLatestInsight(List<InterviewReport> reports) {
        if (reports.isEmpty()) return null;
        ReportDetailResponse detail = reportService.getDetail(reports.get(0).getId());
        DashboardOverviewResponse.LatestInsight insight = new DashboardOverviewResponse.LatestInsight();
        insight.setReportId(detail.getReportId());
        insight.setJobName(detail.getJobName());
        insight.setTotalScore(detail.getTotalScore() == null ? 0 : detail.getTotalScore().doubleValue());
        insight.setDimensions(detail.getDimensions());
        if (detail.getDimensions() != null && !detail.getDimensions().isEmpty()) {
            ReportDimensionView strongest = detail.getDimensions().stream()
                    .max(Comparator.comparing(ReportDimensionView::getScore)).orElseThrow();
            ReportDimensionView weakest = detail.getDimensions().stream()
                    .min(Comparator.comparing(ReportDimensionView::getScore)).orElseThrow();
            insight.setStrongestDimension(strongest.getDimension());
            insight.setStrongestScore(strongest.getScore().doubleValue());
            insight.setWeakestDimension(weakest.getDimension());
            insight.setWeakestScore(weakest.getScore().doubleValue());
        }
        if (detail.getSuggestions() != null && !detail.getSuggestions().isEmpty()) {
            insight.setSuggestion(detail.getSuggestions().get(0));
        }
        return insight;
    }

    private DashboardOverviewResponse.NextAction buildNextAction(
            Long userId, List<InterviewSession> sessions, DashboardOverviewResponse.LatestInsight insight) {
        DashboardOverviewResponse.NextAction action = new DashboardOverviewResponse.NextAction();
        if (sessions.stream().anyMatch(session -> "ONGOING".equals(session.getStatus()))) {
            return action(action, "START_INTERVIEW", "开始一场新的模拟面试",
                    "重新选择目标岗位与训练时长，进入完整面试流程。", "/jobs");
        }
        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId).orderByDesc(Resume::getUpdateTime).last("LIMIT 1"));
        if (resume == null) {
            return action(action, "COMPLETE_RESUME", "先完善你的简历",
                    "补充项目与技能信息，让后续问题更贴近真实经历。", "/profile");
        }
        if (sessions.stream().noneMatch(session -> "FINISHED".equals(session.getStatus()))) {
            return action(action, "FIRST_INTERVIEW", "开始第一次模拟面试",
                    "选择目标岗位，建立第一份可复盘的训练记录。", "/jobs");
        }
        String title = insight != null && insight.getWeakestDimension() != null
                ? "针对“" + insight.getWeakestDimension() + "”再练一次" : "安排下一次岗位训练";
        return action(action, "TARGETED_PRACTICE", title,
                "结合最近一次报告选择岗位与难度，继续积累可比较的表现数据。", "/jobs");
    }

    private DashboardOverviewResponse.NextAction action(
            DashboardOverviewResponse.NextAction action,
            String type, String title, String description, String route) {
        action.setType(type);
        action.setTitle(title);
        action.setDescription(description);
        action.setRoute(route);
        return action;
    }

    static int calculateStreak(List<InterviewSession> sessions) {
        Set<LocalDate> dates = new HashSet<>();
        sessions.forEach(session -> {
            LocalDate date = eventDate(session);
            if (date != null) dates.add(date);
        });
        LocalDate cursor = dates.contains(LocalDate.now()) ? LocalDate.now() : LocalDate.now().minusDays(1);
        int streak = 0;
        while (dates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private static LocalDate eventDate(InterviewSession session) {
        LocalDateTime time = session.getEndTime() != null ? session.getEndTime() : session.getStartTime();
        return time == null ? null : time.toLocalDate();
    }

    private static long actualDuration(InterviewSession session) {
        if (session.getStartTime() == null) return 0;
        LocalDateTime end = session.getEndTime() == null ? LocalDateTime.now() : session.getEndTime();
        return Math.max(0, Duration.between(session.getStartTime(), end).getSeconds());
    }

    private static double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    static String iconKey(JobPosition job) {
        String code = job == null ? "" : valueOr(job.getCode(), "");
        return switch (code) {
            case "BE-JAVA" -> "openjdk";
            case "BE-PY" -> "python";
            case "BE-GO" -> "go";
            case "BE-NODE" -> "nodejs";
            case "BE-CPP" -> "chip-speed";
            case "FE-WEB" -> "web";
            case "FE-ANDROID" -> "android";
            case "FE-IOS" -> "apple";
            case "FE-CROSS" -> "flutter";
            case "FE-MINI" -> "wechat";
            case "FE-DESKTOP" -> "electron";
            case "FS-JAVA" -> "openjdk";
            case "FS-NODE" -> "nodejs";
            case "FS-PY" -> "python";
            case "FS-AI" -> "ai-app";
            case "ALG-ML" -> "machine-learning";
            case "ALG-NLP" -> "nlp";
            case "ALG-CV" -> "computer-vision";
            case "ALG-REC" -> "recommendation";
            case "ALG-SPEECH" -> "speech";
            case "ALG-MM" -> "multimodal";
            case "ALG-MLOPS" -> "mlops";
            case "PM-C" -> "product-consumer";
            case "PM-B" -> "product-enterprise";
            case "PM-AI" -> "product-ai";
            case "DA-BIZ" -> "data-business";
            case "DA-PROD" -> "data-product";
            case "DA-BI" -> "data-bi";
            case "QA-FUNC" -> "qa-functional";
            case "QA-AUTO" -> "qa-automation";
            case "QA-PERF" -> "qa-performance";
            default -> "qa-sdet";
        };
    }

    static String themeKey(JobPosition job) {
        String code = job == null ? "" : valueOr(job.getCode(), "");
        if (code.startsWith("ALG-")) return "violet";
        if (code.startsWith("PM-")) return "orange";
        if (code.startsWith("DA-")) return "blue";
        if (code.startsWith("QA-")) return "amber";
        if (code.startsWith("FE-")) return "cyan";
        if (code.startsWith("FS-")) return "green";
        return "jade";
    }
}
