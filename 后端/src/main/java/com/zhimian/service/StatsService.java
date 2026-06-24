package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhimian.config.UserContext;
import com.zhimian.dto.UserStats;
import com.zhimian.entity.InterviewReport;
import com.zhimian.entity.InterviewSession;
import com.zhimian.entity.Resume;
import com.zhimian.mapper.InterviewReportMapper;
import com.zhimian.mapper.InterviewSessionMapper;
import com.zhimian.mapper.ResumeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户训练统计服务：全部基于真实数据计算，无模拟值。
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewReportMapper reportMapper;
    private final ResumeMapper resumeMapper;

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
}
