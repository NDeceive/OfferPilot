package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhimian.dto.TeacherCommonProblemItem;
import com.zhimian.dto.TeacherDashboardOverviewResponse;
import com.zhimian.dto.TeacherDashboardSummary;
import com.zhimian.dto.TeacherStudentTrainingItem;
import com.zhimian.dto.TeacherTrainingTrendItem;
import com.zhimian.dto.TeacherWeaknessItem;
import com.zhimian.entity.InterviewFollowupRecord;
import com.zhimian.entity.InterviewReport;
import com.zhimian.entity.InterviewSession;
import com.zhimian.entity.JobPosition;
import com.zhimian.entity.SysUser;
import com.zhimian.mapper.InterviewFollowupRecordMapper;
import com.zhimian.mapper.InterviewReportMapper;
import com.zhimian.mapper.InterviewSessionMapper;
import com.zhimian.mapper.JobPositionMapper;
import com.zhimian.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 教师仪表盘数据服务（Phase 5.3）。
 * <p>
 * 只读聚合查询，全部为教师视角的全局统计，<b>不</b>按当前登录用户隔离
 * （与学生端 {@link StatsService} / {@link InterviewFollowupRecordService} 的「按本人过滤」相反）。
 * 访问权限由 Controller 上的 {@code @RequireRole({"TEACHER","ADMIN"})} 保证，仅教师/管理员可达。
 * <p>
 * 不新增数据表、不写裸 SQL，复用既有 MyBatis-Plus Mapper。涉及难以从现有数据精确还原的部分
 * （薄弱项分布、常见问题），优先基于 interview_followup_record 做关键词归类，无数据时回退到
 * 明确标注的 Phase 5.3 临时静态兜底值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherDashboardService {

    private final SysUserMapper userMapper;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewReportMapper reportMapper;
    private final JobPositionMapper jobPositionMapper;
    private final InterviewFollowupRecordMapper followupRecordMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 趋势天数（含今天向前共 7 天） */
    private static final int TREND_DAYS = 7;
    /** 学生训练列表最多返回条数，避免一次性返回过多；按最近训练时间倒序取前 N。 */
    private static final int STUDENT_LIST_LIMIT = 100;

    /** 固定四类薄弱项名称（按需求约定） */
    private static final String W_PROJECT = "项目表达笼统";
    private static final String W_FOLLOWUP = "追问应对不足";
    private static final String W_TECH = "技术细节不清";
    private static final String W_LOGIC = "逻辑结构不完整";
    private static final List<String> WEAKNESS_NAMES = List.of(W_PROJECT, W_FOLLOWUP, W_TECH, W_LOGIC);

    /**
     * 组装教师仪表盘总览。任一子项无数据时返回安全默认值（0 / 空列表 / 标注的临时兜底），不抛异常。
     */
    public TeacherDashboardOverviewResponse getOverview() {
        // 一次性拉取基础数据，后续在内存中聚合，避免 N+1 查询。当前数据规模可控。
        List<SysUser> students = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, "STUDENT"));
        List<InterviewSession> sessions = sessionMapper.selectList(new LambdaQueryWrapper<>());
        List<InterviewReport> reports = reportMapper.selectList(new LambdaQueryWrapper<>());
        List<JobPosition> jobs = jobPositionMapper.selectList(new LambdaQueryWrapper<>());

        TeacherDashboardOverviewResponse resp = new TeacherDashboardOverviewResponse();
        resp.setSummary(buildSummary(students, sessions, reports));
        resp.setTrainingTrend(buildTrend(sessions));
        resp.setWeaknessDistribution(buildWeaknessDistribution());
        resp.setStudentTrainingList(buildStudentList(students, sessions, reports, jobs));
        resp.setCommonProblems(buildCommonProblems());

        log.info("[教师仪表盘] 学生数={}, 会话数={}, 报告数={}", students.size(), sessions.size(), reports.size());
        return resp;
    }

    // ---------------------------------------------------------------------
    // summary
    // ---------------------------------------------------------------------

    private TeacherDashboardSummary buildSummary(List<SysUser> students,
                                                 List<InterviewSession> sessions,
                                                 List<InterviewReport> reports) {
        Set<Long> studentIds = new HashSet<>();
        for (SysUser s : students) {
            studentIds.add(s.getId());
        }

        // 已训练学生：在会话中出现过、且确实是学生的 userId 去重
        Set<Long> trainedStudentIds = new HashSet<>();
        for (InterviewSession ses : sessions) {
            if (ses.getUserId() != null && studentIds.contains(ses.getUserId())) {
                trainedStudentIds.add(ses.getUserId());
            }
        }

        int studentTotal = students.size();
        int trainedCount = trainedStudentIds.size();

        // 平均分：所有报告 totalScore 的平均，保留 1 位小数
        double scoreSum = 0;
        int scored = 0;
        for (InterviewReport r : reports) {
            if (r.getTotalScore() != null) {
                scoreSum += r.getTotalScore().doubleValue();
                scored++;
            }
        }

        long aiCount = countFollowupBySource("AI");
        long ruleCount = countFollowupBySource("RULE");

        TeacherDashboardSummary summary = new TeacherDashboardSummary();
        summary.setStudentTotal(studentTotal);
        summary.setTrainedStudentCount(trainedCount);
        summary.setTrainingCompletionRate(studentTotal == 0 ? 0d : round(((double) trainedCount) / studentTotal, 2));
        summary.setAverageScore(scored == 0 ? 0d : round(scoreSum / scored, 1));
        summary.setAiFollowupCount(aiCount);
        summary.setRuleFollowupCount(ruleCount);
        return summary;
    }

    /** 全局按来源统计追问数（不按用户隔离，教师视角） */
    private long countFollowupBySource(String source) {
        Long c = followupRecordMapper.selectCount(
                new LambdaQueryWrapper<InterviewFollowupRecord>()
                        .eq(InterviewFollowupRecord::getSource, source));
        return c == null ? 0L : c;
    }

    // ---------------------------------------------------------------------
    // trainingTrend
    // ---------------------------------------------------------------------

    private List<TeacherTrainingTrendItem> buildTrend(List<InterviewSession> sessions) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(TREND_DAYS - 1L);

        // 预置 7 天，count=0，保证返回顺序与完整性
        Map<LocalDate, Integer> counter = new LinkedHashMap<>();
        for (int i = 0; i < TREND_DAYS; i++) {
            counter.put(start.plusDays(i), 0);
        }

        for (InterviewSession ses : sessions) {
            LocalDateTime st = ses.getStartTime();
            if (st == null) {
                continue;
            }
            LocalDate d = st.toLocalDate();
            if (counter.containsKey(d)) {
                counter.merge(d, 1, Integer::sum);
            }
        }

        List<TeacherTrainingTrendItem> trend = new ArrayList<>(TREND_DAYS);
        for (Map.Entry<LocalDate, Integer> e : counter.entrySet()) {
            TeacherTrainingTrendItem item = new TeacherTrainingTrendItem();
            item.setDate(e.getKey().format(DATE_FMT));
            item.setCount(e.getValue());
            trend.add(item);
        }
        return trend;
    }

    // ---------------------------------------------------------------------
    // weaknessDistribution
    // ---------------------------------------------------------------------

    /**
     * 薄弱项分布：基于 interview_followup_record 的 triggerReason / abilityTag 做关键词归类。
     * 现有数据没有与这四类一一对应的字段，因此采用关键词映射做近似还原；
     * 当没有任何可归类的记录时，回退到 Phase 5.3 临时静态兜底分布（已明确标注）。
     */
    private List<TeacherWeaknessItem> buildWeaknessDistribution() {
        List<InterviewFollowupRecord> records = followupRecordMapper.selectList(new LambdaQueryWrapper<>());

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String name : WEAKNESS_NAMES) {
            counts.put(name, 0);
        }

        int total = 0;
        for (InterviewFollowupRecord r : records) {
            String text = ((r.getTriggerReason() == null ? "" : r.getTriggerReason()) + " "
                    + (r.getAbilityTag() == null ? "" : r.getAbilityTag()));
            String bucket = classifyWeakness(text);
            if (bucket != null) {
                counts.merge(bucket, 1, Integer::sum);
                total++;
            }
        }

        if (total == 0) {
            // Phase 5.3 临时兜底：暂无可归类的追问记录时返回静态占比（仅占位，待真实数据接入后自动替换）
            return staticWeaknessFallback();
        }

        List<TeacherWeaknessItem> list = new ArrayList<>(WEAKNESS_NAMES.size());
        for (String name : WEAKNESS_NAMES) {
            int c = counts.get(name);
            TeacherWeaknessItem item = new TeacherWeaknessItem();
            item.setName(name);
            item.setCount(c);
            item.setPercent(round(((double) c) / total, 2));
            list.add(item);
        }
        return list;
    }

    /** 关键词归类到四类薄弱项之一；无法归类返回 null。 */
    private String classifyWeakness(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (text.contains("追问") || text.contains("应对") || text.contains("反问")) {
            return W_FOLLOWUP;
        }
        if (text.contains("技术") || text.contains("细节") || text.contains("原理") || text.contains("实现")) {
            return W_TECH;
        }
        if (text.contains("逻辑") || text.contains("结构") || text.contains("条理")) {
            return W_LOGIC;
        }
        if (text.contains("项目") || text.contains("表达") || text.contains("笼统") || text.contains("空泛")) {
            return W_PROJECT;
        }
        return null;
    }

    /** Phase 5.3 临时静态兜底：薄弱项分布占位数据（无真实数据时使用）。 */
    private List<TeacherWeaknessItem> staticWeaknessFallback() {
        List<TeacherWeaknessItem> list = new ArrayList<>();
        list.add(weakness(W_PROJECT, 0.35, 0));
        list.add(weakness(W_FOLLOWUP, 0.28, 0));
        list.add(weakness(W_TECH, 0.22, 0));
        list.add(weakness(W_LOGIC, 0.15, 0));
        return list;
    }

    private TeacherWeaknessItem weakness(String name, double percent, int count) {
        TeacherWeaknessItem item = new TeacherWeaknessItem();
        item.setName(name);
        item.setPercent(percent);
        item.setCount(count);
        return item;
    }

    // ---------------------------------------------------------------------
    // studentTrainingList
    // ---------------------------------------------------------------------

    private List<TeacherStudentTrainingItem> buildStudentList(List<SysUser> students,
                                                              List<InterviewSession> sessions,
                                                              List<InterviewReport> reports,
                                                              List<JobPosition> jobs) {
        Map<Long, String> jobNameById = new HashMap<>();
        for (JobPosition j : jobs) {
            jobNameById.put(j.getId(), j.getName());
        }

        // 按学生聚合会话
        Map<Long, List<InterviewSession>> sessionsByUser = new HashMap<>();
        for (InterviewSession s : sessions) {
            if (s.getUserId() != null) {
                sessionsByUser.computeIfAbsent(s.getUserId(), k -> new ArrayList<>()).add(s);
            }
        }
        // 按学生聚合报告分数
        Map<Long, double[]> scoreAggByUser = new HashMap<>(); // [sum, count]
        for (InterviewReport r : reports) {
            if (r.getUserId() != null && r.getTotalScore() != null) {
                double[] agg = scoreAggByUser.computeIfAbsent(r.getUserId(), k -> new double[2]);
                agg[0] += r.getTotalScore().doubleValue();
                agg[1] += 1;
            }
        }

        List<TeacherStudentTrainingItem> list = new ArrayList<>(students.size());
        for (SysUser stu : students) {
            List<InterviewSession> userSessions = sessionsByUser.getOrDefault(stu.getId(), List.of());

            TeacherStudentTrainingItem item = new TeacherStudentTrainingItem();
            item.setStudentName(displayName(stu));
            item.setTrainingCount(userSessions.size());

            double[] agg = scoreAggByUser.get(stu.getId());
            item.setAverageScore(agg == null || agg[1] == 0 ? 0d : round(agg[0] / agg[1], 1));

            // 最近一次会话：决定 lastTrainingTime / position / status
            InterviewSession latest = userSessions.stream()
                    .filter(s -> s.getStartTime() != null)
                    .max(Comparator.comparing(InterviewSession::getStartTime))
                    .orElse(null);

            if (latest != null) {
                item.setLastTrainingTime(latest.getStartTime().format(DATETIME_FMT));
                String jobName = latest.getJobId() == null ? null : jobNameById.get(latest.getJobId());
                item.setPosition(jobName == null ? "-" : jobName);
            } else {
                item.setLastTrainingTime(null);
                item.setPosition("-");
            }
            item.setStatus(resolveStatus(userSessions));
            list.add(item);
        }

        // 最近训练过的排前面；从未训练的（lastTrainingTime=null）排后面。再截断到上限。
        list.sort(Comparator.comparing(
                TeacherStudentTrainingItem::getLastTrainingTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        if (list.size() > STUDENT_LIST_LIMIT) {
            log.info("[教师仪表盘] 学生列表 {} 条，按最近训练时间截断为前 {} 条", list.size(), STUDENT_LIST_LIMIT);
            return new ArrayList<>(list.subList(0, STUDENT_LIST_LIMIT));
        }
        return list;
    }

    /** 训练状态：无会话=未开始；存在 ONGOING=进行中；否则=已完成。 */
    private String resolveStatus(List<InterviewSession> userSessions) {
        if (userSessions.isEmpty()) {
            return "未开始";
        }
        for (InterviewSession s : userSessions) {
            if ("ONGOING".equalsIgnoreCase(s.getStatus())) {
                return "进行中";
            }
        }
        return "已完成";
    }

    /** 展示名：昵称优先，缺省回退用户名（不暴露邮箱/手机号等敏感字段）。 */
    private String displayName(SysUser stu) {
        if (stu.getNickname() != null && !stu.getNickname().isBlank()) {
            return stu.getNickname();
        }
        return stu.getUsername();
    }

    // ---------------------------------------------------------------------
    // commonProblems
    // ---------------------------------------------------------------------

    /**
     * 常见问题：以薄弱项分布为基础生成（占比直接取自 weaknessDistribution），
     * 问题描述文案为 Phase 5.3 约定文案。等级按占比阈值划分：>=0.30 高 / >=0.20 中 / 其余 低。
     */
    private List<TeacherCommonProblemItem> buildCommonProblems() {
        List<TeacherWeaknessItem> weaknesses = buildWeaknessDistribution();
        Map<String, Double> percentByName = new HashMap<>();
        for (TeacherWeaknessItem w : weaknesses) {
            percentByName.put(w.getName(), w.getPercent() == null ? 0d : w.getPercent());
        }

        List<TeacherCommonProblemItem> list = new ArrayList<>();
        list.add(problem("项目经历表达笼统",
                "学生描述项目时缺乏量化指标与个人贡献，难以体现真实能力。",
                percentByName.getOrDefault(W_PROJECT, 0d)));
        list.add(problem("面对追问应对不足",
                "在连续追问下容易答非所问或思路中断，需加强临场应变训练。",
                percentByName.getOrDefault(W_FOLLOWUP, 0d)));
        list.add(problem("技术细节阐述不清",
                "对所用技术的原理与实现细节掌握不深，回答停留在表层。",
                percentByName.getOrDefault(W_TECH, 0d)));
        list.add(problem("回答逻辑结构不完整",
                "回答缺少清晰的结构与条理，建议采用 STAR 等结构化表达。",
                percentByName.getOrDefault(W_LOGIC, 0d)));

        // 按占比从高到低排序，便于教师优先关注
        list.sort(Comparator.comparing(TeacherCommonProblemItem::getPercent, Comparator.reverseOrder()));
        return list;
    }

    private TeacherCommonProblemItem problem(String title, String description, double percent) {
        TeacherCommonProblemItem item = new TeacherCommonProblemItem();
        item.setTitle(title);
        item.setDescription(description);
        item.setPercent(round(percent, 2));
        item.setLevel(percent >= 0.30 ? "高" : (percent >= 0.20 ? "中" : "低"));
        return item;
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    /** 四舍五入保留 scale 位小数。 */
    private double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
