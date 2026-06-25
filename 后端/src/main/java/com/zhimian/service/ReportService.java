package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.BizException;
import com.zhimian.config.UserContext;
import com.zhimian.dto.ReportDetailResponse;
import com.zhimian.dto.ReportDimensionView;
import com.zhimian.entity.InterviewMessage;
import com.zhimian.entity.InterviewReport;
import com.zhimian.entity.InterviewSession;
import com.zhimian.entity.JobPosition;
import com.zhimian.entity.Question;
import com.zhimian.entity.ReportDimension;
import com.zhimian.mapper.InterviewMessageMapper;
import com.zhimian.mapper.InterviewReportMapper;
import com.zhimian.mapper.InterviewSessionMapper;
import com.zhimian.mapper.JobPositionMapper;
import com.zhimian.mapper.QuestionMapper;
import com.zhimian.mapper.ReportDimensionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 规则化能力报告服务（Phase 2，无 AI）。
 * 依据会话内的全部问答消息，按五个固定维度做确定性打分并生成中文评语，
 * 持久化为 1 条 interview_report + 5 条 report_dimension。生成与查询均做权限与幂等保护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewReportMapper reportMapper;
    private final ReportDimensionMapper dimensionMapper;
    private final QuestionMapper questionMapper;
    private final JobPositionMapper jobMapper;

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String STATUS_FINISHED = "FINISHED";
    private static final String ROLE_CANDIDATE = "CANDIDATE";
    private static final String ROLE_INTERVIEWER = "INTERVIEWER";
    private static final String MSG_ANSWER = "ANSWER";
    private static final String MSG_FOLLOWUP = "FOLLOWUP";

    /** 五个评分维度（顺序固定，与权重一一对应） */
    private static final String DIM_KNOWLEDGE = "专业知识掌握";
    private static final String DIM_PROJECT = "项目实践表达";
    private static final String DIM_LOGIC = "逻辑表达能力";
    private static final String DIM_MATCH = "岗位匹配程度";
    private static final String DIM_FOLLOWUP = "动态追问应对";

    /** 含糊词：出现则扣分 */
    private static final List<String> VAGUE_WORDS = Arrays.asList(
            "不知道", "不清楚", "不会", "不太了解", "不了解", "没用过", "忘了", "可能", "应该", "大概");

    /** 项目实践信号词 */
    private static final List<String> PROJECT_WORDS = Arrays.asList(
            "项目", "负责", "实现", "系统", "接口", "数据库", "模块", "功能", "优化", "搭建", "设计", "重构");

    /** 逻辑表达连接词 */
    private static final List<String> LOGIC_WORDS = Arrays.asList(
            "首先", "其次", "然后", "接着", "最后", "因为", "所以", "因此", "总结", "综上", "例如", "比如", "一方面", "另一方面");

    // ============================ 报告生成 ============================

    /**
     * 为已结束的会话生成报告（幂等）：已存在则直接返回其 reportId。
     * 由 finish 调用，调用方负责保证会话状态与归属。
     */
    public Long generateForSession(InterviewSession session) {
        InterviewReport existing = reportMapper.selectOne(
                new LambdaQueryWrapper<InterviewReport>()
                        .eq(InterviewReport::getSessionId, session.getId())
                        .last("LIMIT 1"));
        if (existing != null) {
            return existing.getId();
        }

        List<InterviewMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<InterviewMessage>()
                        .eq(InterviewMessage::getSessionId, session.getId())
                        .orderByAsc(InterviewMessage::getId));

        JobPosition job = jobMapper.selectById(session.getJobId());
        Metrics m = collectMetrics(messages, job);

        Map<String, BigDecimal> scores = new HashMap<>();
        scores.put(DIM_KNOWLEDGE, scoreKnowledge(m));
        scores.put(DIM_PROJECT, scoreProject(m));
        scores.put(DIM_LOGIC, scoreLogic(m));
        scores.put(DIM_MATCH, scoreMatch(m));
        scores.put(DIM_FOLLOWUP, scoreFollowup(m));

        BigDecimal total = weightedTotal(scores);

        // 弱项（得分 < 60）作为薄弱标签
        List<String> weakDims = new ArrayList<>();
        for (String dim : orderedDimensions()) {
            if (scores.get(dim).doubleValue() < 60) {
                weakDims.add(dim);
            }
        }

        InterviewReport report = new InterviewReport();
        report.setSessionId(session.getId());
        report.setUserId(session.getUserId());
        report.setTotalScore(total);
        report.setSummary(buildSummary(total, scores, m));
        report.setStrengths(toJson(buildStrengths(scores, m)));
        report.setWeaknesses(toJson(buildWeaknesses(scores, m)));
        report.setSuggestions(toJson(buildSuggestions(scores, m)));
        report.setWeakTags(String.join(",", weakDims));
        reportMapper.insert(report);

        for (String dim : orderedDimensions()) {
            BigDecimal s = scores.get(dim);
            ReportDimension d = new ReportDimension();
            d.setReportId(report.getId());
            d.setDimension(dim);
            d.setScore(s);
            d.setLevel(levelOf(s));
            d.setExplanation(explainDimension(dim, s, m));
            dimensionMapper.insert(d);
        }

        // 回写会话薄弱标签，供后续复训使用
        if (!weakDims.isEmpty()) {
            session.setWeakTags(String.join(",", weakDims));
            sessionMapper.updateById(session);
        }
        return report.getId();
    }

    // ============================ 报告查询 ============================

    public ReportDetailResponse getDetail(Long reportId) {
        InterviewReport report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BizException("报告不存在");
        }
        if (!report.getUserId().equals(UserContext.getUserId())) {
            throw new BizException("无权查看该报告");
        }

        InterviewSession session = sessionMapper.selectById(report.getSessionId());
        String jobName = "未知岗位";
        if (session != null) {
            JobPosition job = jobMapper.selectById(session.getJobId());
            if (job != null) {
                jobName = job.getName();
            }
        }

        List<ReportDimension> dims = dimensionMapper.selectList(
                new LambdaQueryWrapper<ReportDimension>()
                        .eq(ReportDimension::getReportId, reportId)
                        .orderByAsc(ReportDimension::getId));
        List<ReportDimensionView> dimViews = new ArrayList<>();
        for (ReportDimension d : dims) {
            ReportDimensionView v = new ReportDimensionView();
            v.setDimension(d.getDimension());
            v.setScore(d.getScore());
            v.setLevel(d.getLevel());
            v.setExplanation(d.getExplanation());
            dimViews.add(v);
        }

        ReportDetailResponse resp = new ReportDetailResponse();
        resp.setReportId(report.getId());
        resp.setSessionId(report.getSessionId());
        resp.setJobName(jobName);
        resp.setTotalScore(report.getTotalScore());
        resp.setSummary(report.getSummary());
        resp.setStrengths(fromJson(report.getStrengths()));
        resp.setWeaknesses(fromJson(report.getWeaknesses()));
        resp.setSuggestions(fromJson(report.getSuggestions()));
        resp.setWeakTags(report.getWeakTags());
        resp.setDimensions(dimViews);
        return resp;
    }

    // ============================ 指标采集 ============================

    /** 一次会话聚合后的可量化信号，所有打分都基于它 —— 保证确定性、可解释。 */
    private static class Metrics {
        int answerCount;          // 考生主问回答条数
        int followupAsked;        // 面试官追问条数
        int followupAnswered;     // 有实质内容的追问回答条数
        int totalLength;          // 全部回答总字数
        int vagueHits;            // 含糊词命中次数
        int techHits;             // 命中题目答案要点关键词次数
        int projectHits;          // 命中项目信号词次数
        int logicHits;            // 命中逻辑连接词次数
        int jobHits;              // 命中岗位关键词的去重数
        int shortAnswers;         // 过短回答(<15字)条数

        double avgLength() {
            return answerCount == 0 ? 0 : (double) totalLength / answerCount;
        }
    }

    /**
     * 遍历消息：先标出哪些题目被追问过，再据此把考生回答区分为「主问回答」与「追问回答」。
     * 主问回答作为主要打分来源；追问回答用于评估动态追问应对。
     */
    private Metrics collectMetrics(List<InterviewMessage> messages, JobPosition job) {
        Metrics m = new Metrics();

        Set<Long> followedUpQuestionIds = new HashSet<>();
        for (InterviewMessage msg : messages) {
            if (ROLE_INTERVIEWER.equals(msg.getRole()) && MSG_FOLLOWUP.equals(msg.getMsgType())) {
                m.followupAsked++;
                if (msg.getQuestionId() != null) {
                    followedUpQuestionIds.add(msg.getQuestionId());
                }
            }
        }

        // 岗位关键词（JSON 数组字符串）
        List<String> jobKeywords = job != null ? parseJsonArray(job.getKeywords()) : new ArrayList<>();
        Set<Long> jobKeywordCountedAnswers = new HashSet<>(); // 防同一岗位词跨题重复累计
        Set<String> matchedJobKeywords = new HashSet<>();

        // 记录每题已出现过几条回答：首条算主问回答，其后算追问回答
        Map<Long, Integer> answerSeenPerQuestion = new HashMap<>();

        for (InterviewMessage msg : messages) {
            if (!ROLE_CANDIDATE.equals(msg.getRole()) || !MSG_ANSWER.equals(msg.getMsgType())) {
                continue;
            }
            String text = msg.getContent() == null ? "" : msg.getContent().trim();
            Long qId = msg.getQuestionId();
            int seen = answerSeenPerQuestion.getOrDefault(qId, 0);
            answerSeenPerQuestion.put(qId, seen + 1);
            boolean isFollowupAnswer = seen >= 1 && qId != null && followedUpQuestionIds.contains(qId);

            if (isFollowupAnswer) {
                if (isMeaningful(text)) {
                    m.followupAnswered++;
                }
                // 追问回答只计入追问应对，不重复污染主问指标
                continue;
            }

            // 主问回答：计入各项主指标
            m.answerCount++;
            m.totalLength += text.length();
            if (text.length() < 15) {
                m.shortAnswers++;
            }
            String lower = text.toLowerCase();

            for (String vague : VAGUE_WORDS) {
                if (text.contains(vague)) {
                    m.vagueHits++;
                }
            }
            for (String pw : PROJECT_WORDS) {
                if (text.contains(pw)) {
                    m.projectHits++;
                }
            }
            for (String lw : LOGIC_WORDS) {
                if (text.contains(lw)) {
                    m.logicHits++;
                }
            }

            // 题目答案要点关键词
            Question q = qId != null ? questionMapper.selectById(qId) : null;
            for (String kw : answerKeywords(q)) {
                if (lower.contains(kw.toLowerCase())) {
                    m.techHits++;
                }
            }

            // 岗位关键词（同一题内去重，跨题可累计不同词）
            for (String jk : jobKeywords) {
                if (jk.isEmpty()) {
                    continue;
                }
                if (lower.contains(jk.toLowerCase())) {
                    matchedJobKeywords.add(jk.toLowerCase());
                }
            }
        }
        m.jobHits = matchedJobKeywords.size();
        return m;
    }

    private boolean isMeaningful(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        if (t.length() < 15) {
            return false;
        }
        for (String vague : VAGUE_WORDS) {
            if (t.contains(vague) && t.length() < 30) {
                return false;
            }
        }
        return true;
    }

    private List<String> answerKeywords(Question q) {
        List<String> result = new ArrayList<>();
        if (q == null || q.getAnswerPoints() == null) {
            return result;
        }
        for (String kw : q.getAnswerPoints().split("[、,，/]")) {
            String k = kw.trim();
            if (!k.isEmpty()) {
                result.add(k);
            }
        }
        return result;
    }

    // ============================ 维度打分（确定性，0~100） ============================
    // 设计原则：基线偏中性，回答即使很短也不会过低；信号越充分得分越高；含糊/过短扣分。

    /** 专业知识掌握：以命中答案要点关键词为主。 */
    private BigDecimal scoreKnowledge(Metrics m) {
        double score = 50;
        score += Math.min(40, m.techHits * 8.0);      // 每命中一个要点关键词 +8，上限 +40
        score -= m.vagueHits * 6.0;                     // 含糊词扣分
        score -= m.shortAnswers * 5.0;                  // 过短回答扣分
        if (m.answerCount == 0) {
            score = 0;
        }
        return clamp(score);
    }

    /** 项目实践表达：项目信号词越多越好。 */
    private BigDecimal scoreProject(Metrics m) {
        double score = 48;
        score += Math.min(42, m.projectHits * 7.0);
        score -= m.shortAnswers * 5.0;
        score -= m.vagueHits * 4.0;
        if (m.answerCount == 0) {
            score = 0;
        }
        return clamp(score);
    }

    /** 逻辑表达能力：逻辑连接词 + 回答篇幅（表达是否展开）。 */
    private BigDecimal scoreLogic(Metrics m) {
        double score = 50;
        score += Math.min(30, m.logicHits * 8.0);
        if (m.avgLength() >= 60) {
            score += 14;
        } else if (m.avgLength() >= 30) {
            score += 8;
        } else if (m.avgLength() > 0 && m.avgLength() < 15) {
            score -= 10;
        }
        score -= m.vagueHits * 4.0;
        if (m.answerCount == 0) {
            score = 0;
        }
        return clamp(score);
    }

    /** 岗位匹配程度：命中岗位关键词的去重数量。 */
    private BigDecimal scoreMatch(Metrics m) {
        double score = 52;
        score += Math.min(40, m.jobHits * 10.0);        // 每命中一个不同岗位词 +10
        score -= m.shortAnswers * 4.0;
        if (m.answerCount == 0) {
            score = 0;
        }
        return clamp(score);
    }

    /** 动态追问应对：未触发追问视为表现稳定（偏中上）；触发后看是否有实质回应。 */
    private BigDecimal scoreFollowup(Metrics m) {
        if (m.answerCount == 0) {
            return clamp(0);
        }
        if (m.followupAsked == 0) {
            return clamp(75); // 全程无需追问，视为应答稳定
        }
        double ratio = (double) m.followupAnswered / m.followupAsked;
        double score = 45 + ratio * 45;                 // 实质回应比例越高越好
        return clamp(score);
    }

    private BigDecimal weightedTotal(Map<String, BigDecimal> scores) {
        double total = scores.get(DIM_KNOWLEDGE).doubleValue() * 0.30
                + scores.get(DIM_PROJECT).doubleValue() * 0.25
                + scores.get(DIM_LOGIC).doubleValue() * 0.20
                + scores.get(DIM_MATCH).doubleValue() * 0.15
                + scores.get(DIM_FOLLOWUP).doubleValue() * 0.10;
        return clamp(total);
    }

    private List<String> orderedDimensions() {
        return Arrays.asList(DIM_KNOWLEDGE, DIM_PROJECT, DIM_LOGIC, DIM_MATCH, DIM_FOLLOWUP);
    }

    private BigDecimal clamp(double v) {
        if (v < 0) {
            v = 0;
        }
        if (v > 100) {
            v = 100;
        }
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP);
    }

    private String levelOf(BigDecimal score) {
        double s = score.doubleValue();
        if (s >= 85) {
            return "优秀";
        }
        if (s >= 70) {
            return "良好";
        }
        if (s >= 60) {
            return "合格";
        }
        return "待提升";
    }

    // ============================ 中文评语生成 ============================

    private String buildSummary(BigDecimal total, Map<String, BigDecimal> scores, Metrics m) {
        String topDim = bestDimension(scores);
        String weakDim = worstDimension(scores);
        String band;
        double t = total.doubleValue();
        if (t >= 85) {
            band = "整体表现优秀，已具备较强的岗位胜任力";
        } else if (t >= 70) {
            band = "整体表现良好，具备扎实的基础";
        } else if (t >= 60) {
            band = "整体表现合格，已能完成基本作答";
        } else {
            band = "整体仍有较大提升空间，建议加强系统准备";
        }
        return String.format(
                "本次模拟面试综合得分 %.1f 分，%s。共完成 %d 道主问题作答，其中触发追问 %d 次。"
                        + "你在「%s」方面表现相对突出，而「%s」是当前最需要加强的环节。"
                        + "建议结合下方各维度解释与改进建议，针对薄弱项进行专项练习。",
                t, band, m.answerCount, m.followupAsked, topDim, weakDim);
    }

    private List<String> buildStrengths(Map<String, BigDecimal> scores, Metrics m) {
        List<String> list = new ArrayList<>();
        for (String dim : orderedDimensions()) {
            if (scores.get(dim).doubleValue() >= 75) {
                list.add(dim + "表现良好（" + scores.get(dim) + " 分），" + strengthHint(dim));
            }
        }
        if (m.projectHits >= 3 && !containsDim(list, DIM_PROJECT)) {
            list.add("回答中较多结合了项目与实践细节，说服力较强。");
        }
        if (list.isEmpty()) {
            list.add("能够完整参与并完成全部主问题作答，具备继续提升的良好基础。");
        }
        return list;
    }

    private List<String> buildWeaknesses(Map<String, BigDecimal> scores, Metrics m) {
        List<String> list = new ArrayList<>();
        for (String dim : orderedDimensions()) {
            if (scores.get(dim).doubleValue() < 60) {
                list.add(dim + "较为薄弱（" + scores.get(dim) + " 分），" + weaknessHint(dim));
            }
        }
        if (m.vagueHits > 0) {
            list.add("回答中出现了「不清楚 / 可能 / 应该」等含糊表述，削弱了专业说服力。");
        }
        if (m.shortAnswers > 0) {
            list.add("存在 " + m.shortAnswers + " 处过于简短的回答，建议展开论述。");
        }
        if (list.isEmpty()) {
            list.add("暂无明显薄弱项，可继续向更高难度与更深技术细节挑战。");
        }
        return list;
    }

    private List<String> buildSuggestions(Map<String, BigDecimal> scores, Metrics m) {
        List<String> list = new ArrayList<>();
        if (scores.get(DIM_KNOWLEDGE).doubleValue() < 70) {
            list.add("围绕岗位核心知识点系统梳理，作答时主动覆盖题目的关键术语与要点。");
        }
        if (scores.get(DIM_PROJECT).doubleValue() < 70) {
            list.add("多用 STAR 法（情境-任务-行动-结果）讲述项目，突出个人职责与量化成果。");
        }
        if (scores.get(DIM_LOGIC).doubleValue() < 70) {
            list.add("回答时使用「首先 / 其次 / 因此」等连接词，先给结论再展开，让逻辑更清晰。");
        }
        if (scores.get(DIM_MATCH).doubleValue() < 70) {
            list.add("提前研究目标岗位的关键词与技术栈，在作答中有意识地贴合岗位要求。");
        }
        if (scores.get(DIM_FOLLOWUP).doubleValue() < 70) {
            list.add("面对追问不要回避，针对被追问的点补充具体细节与思考过程。");
        }
        if (list.isEmpty()) {
            list.add("保持当前水平，可尝试更高难度题目并控制作答节奏，进一步打磨表达。");
        }
        return list;
    }

    private String explainDimension(String dim, BigDecimal score, Metrics m) {
        String level = levelOf(score);
        switch (dim) {
            case DIM_KNOWLEDGE:
                return String.format("等级：%s。回答共命中题目要点关键词约 %d 处，含糊表述 %d 处。"
                        + "得分主要取决于是否准确覆盖核心知识点。", level, m.techHits, m.vagueHits);
            case DIM_PROJECT:
                return String.format("等级：%s。回答中出现项目/实践相关表述约 %d 处。"
                        + "结合具体项目与个人职责越充分，得分越高。", level, m.projectHits);
            case DIM_LOGIC:
                return String.format("等级：%s。使用逻辑连接词约 %d 处，平均作答长度约 %.0f 字。"
                        + "结构化、有条理的表达更易获得高分。", level, m.logicHits, m.avgLength());
            case DIM_MATCH:
                return String.format("等级：%s。回答覆盖目标岗位关键词约 %d 个。"
                        + "作答越贴合岗位技术栈，匹配度越高。", level, m.jobHits);
            case DIM_FOLLOWUP:
                if (m.followupAsked == 0) {
                    return "等级：" + level + "。本次未触发追问，视为主问作答较稳定。";
                }
                return String.format("等级：%s。共触发追问 %d 次，其中有实质回应 %d 次。"
                        + "面对追问能补充细节者得分更高。", level, m.followupAsked, m.followupAnswered);
            default:
                return "等级：" + level + "。";
        }
    }

    private String strengthHint(String dim) {
        switch (dim) {
            case DIM_KNOWLEDGE: return "对核心知识点的覆盖较准确。";
            case DIM_PROJECT: return "能较好地结合项目经历作答。";
            case DIM_LOGIC: return "表达条理清晰、有结构。";
            case DIM_MATCH: return "作答与岗位要求贴合度较高。";
            case DIM_FOLLOWUP: return "面对追问能稳定应对。";
            default: return "";
        }
    }

    private String weaknessHint(String dim) {
        switch (dim) {
            case DIM_KNOWLEDGE: return "建议加强对题目核心知识点的覆盖。";
            case DIM_PROJECT: return "建议多结合真实项目细节展开。";
            case DIM_LOGIC: return "建议使用连接词让表达更有条理。";
            case DIM_MATCH: return "建议作答时更贴合岗位关键词。";
            case DIM_FOLLOWUP: return "建议面对追问时补充更多细节。";
            default: return "";
        }
    }

    private boolean containsDim(List<String> list, String dim) {
        return list.stream().anyMatch(s -> s.startsWith(dim));
    }

    private String bestDimension(Map<String, BigDecimal> scores) {
        String best = DIM_KNOWLEDGE;
        for (String dim : orderedDimensions()) {
            if (scores.get(dim).doubleValue() > scores.get(best).doubleValue()) {
                best = dim;
            }
        }
        return best;
    }

    private String worstDimension(Map<String, BigDecimal> scores) {
        String worst = DIM_KNOWLEDGE;
        for (String dim : orderedDimensions()) {
            if (scores.get(dim).doubleValue() < scores.get(worst).doubleValue()) {
                worst = dim;
            }
        }
        return worst;
    }

    // ============================ JSON 辅助 ============================

    private String toJson(List<String> list) {
        try {
            return JSON.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("报告文本序列化失败", e);
            return "[]";
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return JSON.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            // 兜底：旧数据若非 JSON，包装成单元素列表
            return new ArrayList<>(Arrays.asList(json));
        }
    }

    private List<String> parseJsonArray(String json) {
        return fromJson(json);
    }
}
