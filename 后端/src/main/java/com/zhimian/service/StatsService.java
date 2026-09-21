package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhimian.config.UserContext;
import com.zhimian.dto.UserStats;
import com.zhimian.entity.InterviewModuleScore;
import com.zhimian.entity.InterviewReport;
import com.zhimian.entity.InterviewSession;
import com.zhimian.entity.ReportDimension;
import com.zhimian.entity.Resume;
import com.zhimian.entity.ScoreModule;
import com.zhimian.mapper.InterviewModuleScoreMapper;
import com.zhimian.mapper.InterviewReportMapper;
import com.zhimian.mapper.InterviewSessionMapper;
import com.zhimian.mapper.ReportDimensionMapper;
import com.zhimian.mapper.ResumeMapper;
import com.zhimian.mapper.ScoreModuleMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户训练统计服务：全部基于真实数据计算，无模拟值。
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewReportMapper reportMapper;
    private final ResumeMapper resumeMapper;
    private final InterviewModuleScoreMapper moduleScoreMapper;
    private final ReportDimensionMapper dimensionMapper;
    private final ScoreModuleMapper scoreModuleMapper;

    public UserStats getMyStats() {
        Long userId = UserContext.getUserId();
        UserStats stats = new UserStats();

        // 面试场次（已结束 / 进行中）
        stats.setFinishedInterviews(sessionMapper.selectCount(
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, userId)
                        .eq(InterviewSession::getStatus, "FINISHED")));
        stats.setOngoingInterviews(sessionMapper.selectCount(
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, userId)
                        .eq(InterviewSession::getStatus, "ONGOING")));

        // 报告与分数
        List<InterviewReport> reports = reportMapper.selectList(
                new LambdaQueryWrapper<InterviewReport>().eq(InterviewReport::getUserId, userId));
        stats.setReportCount(reports.size());
        if (!reports.isEmpty()) {
            double sum = 0, highest = 0;
            int counted = 0;
            for (InterviewReport r : reports) {
                if (r.getTotalScore() != null) {
                    double s = r.getTotalScore().doubleValue();
                    sum += s;
                    highest = Math.max(highest, s);
                    counted++;
                }
            }
            if (counted > 0) {
                // 平均分保留一位小数
                stats.setAverageScore(BigDecimal.valueOf(sum / counted)
                        .setScale(1, BigDecimal.ROUND_HALF_UP).doubleValue());
                stats.setHighestScore(highest);
            }
        }

        // 个人画像技能数
        Resume resume = resumeMapper.selectOne(
                new LambdaQueryWrapper<Resume>().eq(Resume::getUserId, userId).last("LIMIT 1"));
        if (resume != null && resume.getSkills() != null) {
            // skills 是 JSON 数组字符串，粗略统计元素个数
            String skills = resume.getSkills().trim();
            if (skills.length() > 2) {
                stats.setSkillCount(skills.split(",").length);
            }
        }

        return stats;
    }

    /**
     * 获取用户最新面试的能力雷达数据，供首页 Dashboard 使用。
     * 优先读新表 interview_module_score，降级读旧表 report_dimension。
     */
    public RadarProfile getRadarProfile() {
        Long userId = UserContext.getUserId();

        // 找最新一次已结束的面试
        InterviewSession latestSession = sessionMapper.selectList(
                new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getUserId, userId)
                        .eq(InterviewSession::getStatus, "FINISHED")
                        .orderByDesc(InterviewSession::getEndTime)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);

        if (latestSession == null) {
            return RadarProfile.empty();
        }

        // 找该会话的报告
        InterviewReport report = reportMapper.selectOne(
                new LambdaQueryWrapper<InterviewReport>()
                        .eq(InterviewReport::getSessionId, latestSession.getId())
                        .last("LIMIT 1"));
        if (report == null) {
            return RadarProfile.empty();
        }

        // 优先读新表
        List<InterviewModuleScore> moduleScores = moduleScoreMapper.selectList(
                new LambdaQueryWrapper<InterviewModuleScore>()
                        .eq(InterviewModuleScore::getReportId, report.getId()));
        if (!moduleScores.isEmpty()) {
            // 加载模块中文名
            List<ScoreModule> allModules = scoreModuleMapper.selectList(new LambdaQueryWrapper<>());
            Map<String, String> nameMap = allModules.stream()
                    .collect(Collectors.toMap(ScoreModule::getCode, ScoreModule::getName, (a, b) -> a));

            RadarProfile profile = new RadarProfile();
            for (InterviewModuleScore ms : moduleScores) {
                profile.labels.add(nameMap.getOrDefault(ms.getModuleCode(), ms.getModuleCode()));
                profile.values.add(ms.getRawScore().doubleValue());
            }
            profile.setSource("module_score");
            return profile;
        }

        // 降级：读旧表 report_dimension
        List<ReportDimension> dims = dimensionMapper.selectList(
                new LambdaQueryWrapper<ReportDimension>()
                        .eq(ReportDimension::getReportId, report.getId()));
        if (!dims.isEmpty()) {
            RadarProfile profile = new RadarProfile();
            for (ReportDimension d : dims) {
                profile.labels.add(d.getDimension());
                profile.values.add(d.getScore().doubleValue());
            }
            profile.setSource("dimension");
            return profile;
        }

        return RadarProfile.empty();
    }

    @Data
    public static class RadarProfile {
        private List<String> labels = new ArrayList<>();
        private List<Double> values = new ArrayList<>();
        private String source;
        private boolean empty;

        static RadarProfile empty() {
            RadarProfile p = new RadarProfile();
            p.empty = true;
            return p;
        }
    }
}
