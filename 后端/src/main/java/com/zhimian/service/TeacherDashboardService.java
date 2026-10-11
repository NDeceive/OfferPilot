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
 * 只读聚合查询，仅统计当前教师名下班级的教学任务，不包含其他教师的数据。
 * Controller 与服务层均校验教师身份，资源范围以 teaching_class.teacher_id 为准。
 * <p>
 * 不新增数据表，复用现有教学关系查询与 MyBatis-Plus Mapper。涉及难以从现有数据精确还原的部分
 * （薄弱项分布、常见问题），仅在存在可归类的真实追问记录时返回近似归类结果。
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
    private final TeachingService teachingService;

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
     * 组装教师仪表盘总览。无数据时返回 0 / 空列表，不伪造统计值。
     */
    public TeacherDashboardOverviewResponse getOverview() {
        teachingService.teacher();
        // 一次性拉取基础数据，后续在内存中聚合，避免 N+1 查询。当前数据规模可控。
        List<Long> studentIds=teachingService.people().stream().map(s->TeachingService.id(s,"id")).distinct().toList();
        List<Long> sessionIds=teachingService.rows("SELECT DISTINCT p.session_id FROM teaching_attempt p JOIN teaching_assignment a ON a.id=p.assignment_id JOIN teaching_task t ON t.id=a.task_id JOIN teaching_class c ON c.id=t.class_id WHERE c.teacher_id=?",teachingService.user()).stream().map(p->TeachingService.id(p,"sessionId")).toList();
        List<SysUser> students=studentIds.isEmpty()?List.of():userMapper.selectBatchIds(studentIds);
        List<InterviewSession> sessions=sessionIds.isEmpty()?List.of():sessionMapper.selectBatchIds(sessionIds);
        List<InterviewReport> reports=sessionIds.isEmpty()?List.of():reportMapper.selectList(new LambdaQueryWrapper<InterviewReport>().in(InterviewReport::getSessionId,sessionIds));
        Set<Long> validReportIds=sessionIds.isEmpty()?Set.of():validReportIds();
        List<InterviewFollowupRecord> followups=sessionIds.isEmpty()?List.of():followupRecordMapper.selectList(
                new LambdaQueryWrapper<InterviewFollowupRecord>().in(InterviewFollowupRecord::getSessionId,sessionIds));
        List<Long> jobIds=sessions.stream().map(InterviewSession::getJobId).filter(java.util.Objects::nonNull).distinct().toList();
        List<JobPosition> jobs=jobIds.isEmpty()?List.of():jobPositionMapper.selectBatchIds(jobIds);
        List<TeacherWeaknessItem> weaknesses=buildWeaknessDistribution(followups);

        TeacherDashboardOverviewResponse resp = new TeacherDashboardOverviewResponse();
        resp.setSummary(buildSummary(students, reports, validReportIds, followups));
        resp.setTrainingTrend(buildTrend(sessions));
        resp.setWeaknessDistribution(weaknesses);
        resp.setStudentTrainingList(buildStudentList(students, sessions, reports, jobs, validReportIds));
        resp.setCommonProblems(buildCommonProblems(weaknesses));

        log.info("[教师仪表盘] 学生数={}, 会话数={}, 报告数={}", students.size(), sessions.size(), reports.size());
        return resp;
    }

    // ---------------------------------------------------------------------
    // summary
    // ---------------------------------------------------------------------

    private TeacherDashboardSummary buildSummary(List<SysUser> students,
                                                 List<InterviewReport> reports,
                                                 Set<Long> validReportIds,
                                                 List<InterviewFollowupRecord> followups) {
        Set<Long> studentIds = new HashSet<>();
        for (SysUser s : students) {
            studentIds.add(s.getId());
        }

        // 完成至少一份分配任务，且仍属于本教师成员范围；开始训练不算完成。
        Set<Long> trainedStudentIds = new HashSet<>();
        for (var allocation : teachingService.rows("SELECT a.student_id FROM teaching_assignment a JOIN teaching_task t ON t.id=a.task_id JOIN teaching_class c ON c.id=t.class_id JOIN teaching_attempt p ON p.assignment_id=a.id AND p.state='READY' AND p.valid=TRUE WHERE c.teacher_id=? AND t.published_at IS NOT NULL GROUP BY a.id,a.student_id,t.min_attempts HAVING COUNT(p.id)>=t.min_attempts",teachingService.user())) {
            long studentId=TeachingService.id(allocation,"studentId");
            if(studentIds.contains(studentId))trainedStudentIds.add(studentId);
        }

        int studentTotal = students.size();
        int trainedCount = trainedStudentIds.size();

        // 平均分仅采用本教师任务的有效报告。
        double scoreSum = 0;
        int scored = 0;
        for (InterviewReport r : reports) {
            if (validReportIds.contains(r.getId())&&r.getTotalScore() != null) {
                scoreSum += r.getTotalScore().doubleValue();
                scored++;
            }
        }

        long aiCount = followups.stream().filter(r -> "AI".equals(r.getSource())).count();
        long ruleCount = followups.stream().filter(r -> "RULE".equals(r.getSource())).count();

        TeacherDashboardSummary summary = new TeacherDashboardSummary();
        summary.setStudentTotal(studentTotal);
        summary.setTrainedStudentCount(trainedCount);
        summary.setTrainingCompletionRate(studentTotal == 0 ? 0d : round(((double) trainedCount) / studentTotal, 2));
        summary.setAverageScore(scored == 0 ? 0d : round(scoreSum / scored, 1));
        summary.setAiFollowupCount(aiCount);
        summary.setRuleFollowupCount(ruleCount);
        return summary;
    }

    /** 仅认可已生成且有效的教学任务报告；无教师数据时不发起全表查询。 */
    private Set<Long> validReportIds() {
        return teachingService.rows("SELECT DISTINCT p.report_id FROM teaching_attempt p JOIN teaching_assignment a ON a.id=p.assignment_id JOIN teaching_task t ON t.id=a.task_id JOIN teaching_class c ON c.id=t.class_id WHERE c.teacher_id=? AND p.state='READY' AND p.valid=TRUE AND p.report_id IS NOT NULL",teachingService.user())
                .stream().map(p -> TeachingService.id(p,"reportId")).collect(java.util.stream.Collectors.toSet());
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
     * 当没有任何可归类的记录时返回空列表。
     */
    private List<TeacherWeaknessItem> buildWeaknessDistribution(List<InterviewFollowupRecord> records) {

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
            return List.of();
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

    // ---------------------------------------------------------------------
    // studentTrainingList
    // ---------------------------------------------------------------------

    private List<TeacherStudentTrainingItem> buildStudentList(List<SysUser> students,
                                                              List<InterviewSession> sessions,
                                                              List<InterviewReport> reports,
                                                              List<JobPosition> jobs,
                                                              Set<Long> validReportIds) {
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
            if (validReportIds.contains(r.getId()) && r.getUserId() != null && r.getTotalScore() != null) {
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
     * 常见问题：仅展示有对应追问记录的关键词类别。描述不推断记录中没有的学生行为。
     * 等级按已归类记录的占比划分：>=0.30 高 / >=0.20 中 / 其余 低。
     */
    private List<TeacherCommonProblemItem> buildCommonProblems(List<TeacherWeaknessItem> weaknesses) {
        Map<String, Double> percentByName = new HashMap<>();
        for (TeacherWeaknessItem w : weaknesses) {
            percentByName.put(w.getName(), w.getPercent() == null ? 0d : w.getPercent());
        }

        List<TeacherCommonProblemItem> list = new ArrayList<>();
        if (percentByName.getOrDefault(W_PROJECT, 0d) > 0) list.add(problem("项目经历表达笼统",
                "项目表达相关追问较集中，请结合具体报告核实问题并安排针对性练习。",
                percentByName.getOrDefault(W_PROJECT, 0d)));
        if (percentByName.getOrDefault(W_FOLLOWUP, 0d) > 0) list.add(problem("面对追问应对不足",
                "追问应对相关记录较集中，请结合具体报告核实并安排追问训练。",
                percentByName.getOrDefault(W_FOLLOWUP, 0d)));
        if (percentByName.getOrDefault(W_TECH, 0d) > 0) list.add(problem("技术细节阐述不清",
                "技术细节相关追问较集中，请结合具体报告核实并安排技术表达训练。",
                percentByName.getOrDefault(W_TECH, 0d)));
        if (percentByName.getOrDefault(W_LOGIC, 0d) > 0) list.add(problem("回答逻辑结构不完整",
                "逻辑结构相关追问较集中，请结合具体报告核实并安排结构化表达训练。",
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
