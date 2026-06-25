package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhimian.common.BizException;
import com.zhimian.config.UserContext;
import com.zhimian.dto.AnswerRequest;
import com.zhimian.dto.InterviewStartResponse;
import com.zhimian.dto.InterviewStep;
import com.zhimian.dto.QuestionView;
import com.zhimian.dto.StartInterviewRequest;
import com.zhimian.entity.InterviewMessage;
import com.zhimian.entity.InterviewSession;
import com.zhimian.entity.JobPosition;
import com.zhimian.entity.Question;
import com.zhimian.entity.Resume;
import com.zhimian.mapper.InterviewMessageMapper;
import com.zhimian.mapper.InterviewSessionMapper;
import com.zhimian.mapper.JobPositionMapper;
import com.zhimian.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 面试流程服务（Phase 1 规则化）：开始会话 → 提交回答（规则化追问）→ 下一题 → 结束。
 * 全程不依赖会话表上的轮次/进度字段，轮次与“已问题目”均由 interview_message 推导，
 * 因此 start / answer / next 共用同一套确定性候选题查询，逻辑不会漂移。
 * 报告生成留待 Phase 2，finish 仅关闭会话。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewFlowService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final QuestionMapper questionMapper;
    private final JobPositionMapper jobMapper;
    private final ResumeService resumeService;
    private final ReportService reportService;

    /** 一次面试最多主问题数 */
    private static final int MAX_QUESTIONS = 5;

    private static final String STATUS_ONGOING = "ONGOING";
    private static final String STATUS_FINISHED = "FINISHED";

    private static final String ROLE_INTERVIEWER = "INTERVIEWER";
    private static final String ROLE_CANDIDATE = "CANDIDATE";

    private static final String MSG_MAIN = "MAIN";
    private static final String MSG_FOLLOWUP = "FOLLOWUP";
    private static final String MSG_ANSWER = "ANSWER";

    private static final String TYPE_MAIN = "MAIN";

    /** nextAction 取值 */
    private static final String ACTION_FOLLOWUP = "FOLLOWUP";
    private static final String ACTION_NEXT = "NEXT";
    private static final String ACTION_FINISHABLE = "FINISHABLE";

    /** 项目信号词：回答中出现则视为有项目细节 */
    private static final List<String> PROJECT_SIGNALS =
            Arrays.asList("项目", "负责", "实现", "实践", "经历", "做过", "我们", "系统", "搭建", "设计");

    /** 含糊词：出现则触发追问 */
    private static final List<String> VAGUE_WORDS =
            Arrays.asList("大概", "可能", "应该", "不太清楚", "不知道", "不清楚", "忘了", "不会", "没用过", "不了解");

    // ============================ 1. 开始面试 ============================

    public InterviewStartResponse start(StartInterviewRequest req) {
        Long userId = UserContext.getUserId();
        int difficulty = normalizeDifficulty(req.getDifficulty());

        JobPosition job = jobMapper.selectById(req.getJobId());
        if (job == null) {
            throw new BizException("岗位不存在");
        }

        List<Question> candidates = candidateQuestions(req.getJobId(), difficulty);
        if (candidates.isEmpty()) {
            throw new BizException("该岗位暂无题目，请先导入题库");
        }

        // resumeId 始终按当前用户解析，忽略任何前端传入值
        Resume resume = resumeService.getMine();
        Long resumeId = (resume != null) ? resume.getId() : null;

        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setJobId(req.getJobId());
        session.setResumeId(resumeId);
        session.setDifficulty(difficulty);
        session.setStatus(STATUS_ONGOING);
        session.setIsRetrain(0);
        // startTime 由数据库默认值填充
        sessionMapper.insert(session);

        Question first = candidates.get(0);
        saveInterviewerQuestion(session.getId(), first, MSG_MAIN, 1, first.getContent());

        InterviewStartResponse resp = new InterviewStartResponse();
        resp.setSessionId(session.getId());
        resp.setJobName(job.getName());
        resp.setQuestion(toView(first, 1));
        return resp;
    }

    // ============================ 2. 提交回答 ============================

    public InterviewStep answer(Long sessionId, AnswerRequest req) {
        InterviewSession session = requireOngoingSession(sessionId);

        // 找到该题对应的主问消息，回答/追问都沿用其轮次
        InterviewMessage parentMain = messageMapper.selectOne(
                new LambdaQueryWrapper<InterviewMessage>()
                        .eq(InterviewMessage::getSessionId, sessionId)
                        .eq(InterviewMessage::getQuestionId, req.getQuestionId())
                        .eq(InterviewMessage::getRole, ROLE_INTERVIEWER)
                        .eq(InterviewMessage::getMsgType, MSG_MAIN)
                        .last("LIMIT 1"));
        if (parentMain == null) {
            throw new BizException("该题尚未提问，无法作答");
        }
        int round = parentMain.getRoundNo();

        // 保存考生回答
        saveMessage(sessionId, req.getQuestionId(), round, ROLE_CANDIDATE, MSG_ANSWER,
                req.getAnswer(), parentMain.getAbilityTag());

        Question question = questionMapper.selectById(req.getQuestionId());

        // 每题至多追问一次：已存在追问则不再追问（也用于区分主问回答 vs 追问回答）
        boolean followupExists = messageMapper.selectCount(
                new LambdaQueryWrapper<InterviewMessage>()
                        .eq(InterviewMessage::getSessionId, sessionId)
                        .eq(InterviewMessage::getQuestionId, req.getQuestionId())
                        .eq(InterviewMessage::getMsgType, MSG_FOLLOWUP)) > 0;

        InterviewStep step = new InterviewStep();
        if (!followupExists && shouldFollowUp(req.getAnswer(), question)) {
            String followup = buildFollowup(question);
            saveMessage(sessionId, req.getQuestionId(), round, ROLE_INTERVIEWER, MSG_FOLLOWUP,
                    followup, parentMain.getAbilityTag());
            step.setNextAction(ACTION_FOLLOWUP);
            step.setFollowupQuestion(followup);
            return step;
        }

        // 不追问：用共享逻辑判断还有没有没问过的主问题
        step.setNextAction(hasRemainingQuestions(session) ? ACTION_NEXT : ACTION_FINISHABLE);
        return step;
    }

    // ============================ 3. 下一题 ============================

    public InterviewStep next(Long sessionId) {
        InterviewSession session = requireOngoingSession(sessionId);

        List<Question> candidates = candidateQuestions(session.getJobId(), session.getDifficulty());
        Set<Long> asked = askedMainQuestionIds(sessionId);

        Question nextQuestion = candidates.stream()
                .filter(q -> !asked.contains(q.getId()))
                .findFirst()
                .orElse(null);

        InterviewStep step = new InterviewStep();
        if (nextQuestion == null) {
            step.setNextAction(ACTION_FINISHABLE);
            return step;
        }

        int round = asked.size() + 1; // 提问前统计，故为新一轮编号
        saveInterviewerQuestion(sessionId, nextQuestion, MSG_MAIN, round, nextQuestion.getContent());

        step.setNextAction(ACTION_NEXT);
        step.setQuestion(toView(nextQuestion, round));
        return step;
    }

    // ============================ 4. 结束面试 ============================

    /**
     * 结束面试并生成报告（Phase 2）。幂等：
     *  - 已 FINISHED 且报告已存在 → 返回已有 reportId；
     *  - 已 FINISHED 但无报告 → 补生成报告；
     *  - 进行中 → 置 FINISHED、补 endTime，再生成报告。
     * 返回 reportId（非 sessionId）。
     */
    public Long finish(Long sessionId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException("会话不存在");
        }
        if (!session.getUserId().equals(UserContext.getUserId())) {
            throw new BizException("无权操作该会话");
        }
        if (!STATUS_FINISHED.equals(session.getStatus())) {
            session.setStatus(STATUS_FINISHED);
            if (session.getEndTime() == null) {
                session.setEndTime(LocalDateTime.now());
            }
            sessionMapper.updateById(session);
        }
        // 生成报告（内部幂等：已存在则返回原 reportId）
        return reportService.generateForSession(session);
    }

    // ============================ 共享辅助逻辑 ============================

    /**
     * 候选题：同岗位、MAIN 主问、难度 <= 目标难度，按 id 升序取前 N。
     * 必须确定性（id 升序）—— start/answer/next 多次调用要保证“已问集合”稳定。
     */
    private List<Question> candidateQuestions(Long jobId, Integer difficulty) {
        return questionMapper.selectList(
                new LambdaQueryWrapper<Question>()
                        .eq(Question::getJobId, jobId)
                        .eq(Question::getType, TYPE_MAIN)
                        .le(Question::getDifficulty, difficulty)
                        .orderByAsc(Question::getId)
                        .last("LIMIT " + MAX_QUESTIONS));
    }

    /** 本会话已经问过的主问题目ID集合 */
    private Set<Long> askedMainQuestionIds(Long sessionId) {
        List<InterviewMessage> mains = messageMapper.selectList(
                new LambdaQueryWrapper<InterviewMessage>()
                        .eq(InterviewMessage::getSessionId, sessionId)
                        .eq(InterviewMessage::getRole, ROLE_INTERVIEWER)
                        .eq(InterviewMessage::getMsgType, MSG_MAIN));
        return mains.stream()
                .map(InterviewMessage::getQuestionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 是否还有没问过的候选主问题（answer 与 next 共用同一判断口径） */
    private boolean hasRemainingQuestions(InterviewSession session) {
        List<Question> candidates = candidateQuestions(session.getJobId(), session.getDifficulty());
        Set<Long> asked = askedMainQuestionIds(session.getId());
        return candidates.stream().anyMatch(q -> !asked.contains(q.getId()));
    }

    /** 校验会话存在、属于当前用户、且处于进行中 */
    private InterviewSession requireOngoingSession(Long sessionId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException("会话不存在");
        }
        if (!session.getUserId().equals(UserContext.getUserId())) {
            throw new BizException("无权操作该会话");
        }
        if (!STATUS_ONGOING.equals(session.getStatus())) {
            throw new BizException("面试已结束");
        }
        return session;
    }

    private void saveInterviewerQuestion(Long sessionId, Question q, String msgType, int round, String content) {
        saveMessage(sessionId, q.getId(), round, ROLE_INTERVIEWER, msgType, content, q.getAbilityTag());
    }

    private void saveMessage(Long sessionId, Long questionId, int round, String role,
                             String msgType, String content, String abilityTag) {
        InterviewMessage msg = new InterviewMessage();
        msg.setSessionId(sessionId);
        msg.setQuestionId(questionId);
        msg.setRoundNo(round);
        msg.setRole(role);
        msg.setMsgType(msgType);
        msg.setContent(content);
        msg.setAbilityTag(abilityTag);
        // createTime 由数据库默认值填充
        messageMapper.insert(msg);
    }

    private QuestionView toView(Question q, int round) {
        QuestionView view = new QuestionView();
        view.setId(q.getId());
        view.setContent(q.getContent());
        view.setAbilityTag(q.getAbilityTag());
        view.setRoundNo(round);
        return view;
    }

    private int normalizeDifficulty(Integer difficulty) {
        if (difficulty == null || difficulty < 1 || difficulty > 3) {
            return 2; // 默认中等
        }
        return difficulty;
    }

    // ============================ 规则化追问引擎 ============================

    /**
     * 规则化判断是否需要追问。任一命中即追问：
     *  1) 回答过短（去空白后 < 15 字）；
     *  2) 与答案要点/能力标签均无关键词重合；
     *  3) 缺少项目信号词（项目/负责/实现/...）；
     *  4) 含含糊词（大概/可能/不知道/...）。
     */
    private boolean shouldFollowUp(String answer, Question question) {
        String text = answer == null ? "" : answer.trim();

        // 1) 过短
        if (text.length() < 15) {
            return true;
        }
        // 4) 含糊
        for (String vague : VAGUE_WORDS) {
            if (text.contains(vague)) {
                return true;
            }
        }
        // 2) 与答案要点/能力标签无重合
        if (!hasKeywordOverlap(text, question)) {
            return true;
        }
        // 3) 缺项目细节
        boolean hasProjectSignal = PROJECT_SIGNALS.stream().anyMatch(text::contains);
        if (!hasProjectSignal) {
            return true;
        }
        return false;
    }

    /** 回答是否覆盖到答案要点或能力标签中的任一关键词 */
    private boolean hasKeywordOverlap(String text, Question question) {
        if (question == null) {
            return true; // 找不到题目则不因关键词触发追问，避免误判
        }
        List<String> keywords = new ArrayList<>();
        if (question.getAnswerPoints() != null) {
            // 答案要点以「、」分隔，兼容英文逗号/中文逗号/斜杠
            for (String kw : question.getAnswerPoints().split("[、,，/]")) {
                String k = kw.trim();
                if (!k.isEmpty()) {
                    keywords.add(k);
                }
            }
        }
        if (question.getAbilityTag() != null && !question.getAbilityTag().trim().isEmpty()) {
            keywords.add(question.getAbilityTag().trim());
        }
        if (keywords.isEmpty()) {
            return true; // 没有可比对的要点，不触发
        }
        String lower = text.toLowerCase();
        return keywords.stream().anyMatch(k -> lower.contains(k.toLowerCase()));
    }

    /** 优先使用题目预设的追问提示，缺省则用能力标签拼一句通用追问 */
    private String buildFollowup(Question question) {
        if (question != null && question.getFollowupHint() != null
                && !question.getFollowupHint().trim().isEmpty()) {
            return question.getFollowupHint().trim();
        }
        String tag = (question != null && question.getAbilityTag() != null)
                ? question.getAbilityTag() : "这个问题";
        return "能否结合你的具体项目，再深入说明一下「" + tag + "」相关的细节和你的思考？";
    }
}
