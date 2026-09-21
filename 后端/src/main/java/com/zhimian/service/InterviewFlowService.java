package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhimian.common.BizException;
import com.zhimian.config.UserContext;
import com.zhimian.dto.AnswerRequest;
import com.zhimian.dto.FollowUpRequest;
import com.zhimian.dto.FollowUpResponse;
import com.zhimian.dto.InterviewStartResponse;
import com.zhimian.dto.InterviewStep;
import com.zhimian.dto.QuestionView;
import com.zhimian.dto.StartInterviewRequest;
import com.zhimian.entity.InterviewFollowupRecord;
import com.zhimian.entity.InterviewMessage;
import com.zhimian.entity.InterviewReport;
import com.zhimian.entity.InterviewSession;
import com.zhimian.entity.JobPosition;
import com.zhimian.entity.ReportDimension;
import com.zhimian.entity.Resume;
import com.zhimian.entity.SkillQuestion;
import com.zhimian.entity.SkillQuestionTagRel;
import com.zhimian.entity.SkillTag;
import com.zhimian.mapper.InterviewMessageMapper;
import com.zhimian.mapper.InterviewReportMapper;
import com.zhimian.mapper.InterviewSessionMapper;
import com.zhimian.mapper.JobPositionMapper;
import com.zhimian.mapper.ReportDimensionMapper;
import com.zhimian.mapper.SkillQuestionMapper;
import com.zhimian.mapper.SkillQuestionTagRelMapper;
import com.zhimian.mapper.SkillTagMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 面试流程服务：标签化出题 + 规则化追问。
 * 从用户个人画像标签中随机选题，追问逻辑保持不变。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewFlowService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final JobPositionMapper jobMapper;
    private final ResumeService resumeService;
    private final ReportService reportService;
    private final FollowUpService followUpService;
    private final InterviewFollowupRecordService followupRecordService;
    private final ExperienceQuestionService experienceQuestionService;
    private final QuestionBootstrapper questionBootstrapper;
    private final InterviewReportMapper reportMapper;
    private final ReportDimensionMapper dimensionMapper;
    private final ModulePreferenceService modulePreferenceService;
    private final ModuleScoreService moduleScoreService;

    // 新标签化题库
    private final SkillQuestionMapper skillQuestionMapper;
    private final SkillTagMapper skillTagMapper;
    private final SkillQuestionTagRelMapper skillQuestionTagRelMapper;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** 候选题目池上限 */
    private static final int CANDIDATE_POOL_MAX = 100;

    private static final String STATUS_ONGOING = "ONGOING";
    private static final String STATUS_FINISHED = "FINISHED";

    private static final String ROLE_INTERVIEWER = "INTERVIEWER";
    private static final String ROLE_CANDIDATE = "CANDIDATE";

    private static final String MSG_MAIN = "MAIN";
    private static final String MSG_FOLLOWUP = "FOLLOWUP";
    private static final String MSG_ANSWER = "ANSWER";

    /** nextAction 取值 */
    private static final String ACTION_FOLLOWUP = "FOLLOWUP";
    private static final String ACTION_NEXT = "NEXT";
    private static final String ACTION_FINISHABLE = "FINISHABLE";

    // ============================ 体验式题目 ============================

    /** 体验式题目块大小：每 N 题一个块，块内必含 1 道体验式题目 */
    private static final int EXPERIENCE_BLOCK_SIZE = 5;

    /** 体验式题目开始的最小题号（前 N-1 题不出现体验题） */
    private static final int EXPERIENCE_FIRST_BLOCK_SLOT = 5;

    private static final String QUESTION_TYPE_SKILL = "SKILL";
    private static final String QUESTION_TYPE_EXPERIENCE = "EXPERIENCE";

    // ============================ 1. 开始面试 ============================

    public InterviewStartResponse start(StartInterviewRequest req) {
        Long userId = UserContext.getUserId();
        int difficulty = normalizeDifficulty(req.getDifficulty());
        int duration = (req.getDurationSeconds() != null && req.getDurationSeconds() > 0)
                ? req.getDurationSeconds() : 1800; // 默认 30 分钟

        JobPosition job = jobMapper.selectById(req.getJobId());
        if (job == null) {
            throw new BizException("岗位不存在");
        }

        // 获取用户简历画像标签 + 岗位标签
        Resume resume = resumeService.getMine();
        Long resumeId = (resume != null) ? resume.getId() : null;
        List<String> userTags = extractTagsFromResume(resume);
        List<String> jobTags = extractTagsFromJob(job);
        Set<String> mergedTags = new LinkedHashSet<>();
        mergedTags.addAll(userTags);
        mergedTags.addAll(jobTags);
        List<String> allTags = new ArrayList<>(mergedTags);

        // 确保每个标签都有对应的题库题目（缺题则 AI 自动生成）
        questionBootstrapper.ensure(allTags, job.getFamily(), difficulty);

        // 标签化选题：岗位标签优先，画像标签兜底
        List<SkillQuestion> candidates = candidateQuestionsByTags(jobTags, userTags, difficulty);
        if (candidates.isEmpty()) {
            throw new BizException("未找到匹配的面试题目，请先完善个人简历画像或扩充题库");
        }

        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setJobId(req.getJobId());
        session.setResumeId(resumeId);
        session.setDifficulty(difficulty);
        session.setDurationSeconds(duration);
        session.setStartTime(LocalDateTime.now());
        session.setStatus(STATUS_ONGOING);
        session.setIsRetrain(0);

        // 评分系统改造：保存模块偏好
        List<ModulePreferenceService.PreferenceItem> moduleItems = null;
        log.info("收到modulePreferences: {}", req.getModulePreferences());
        if (req.getModulePreferences() != null && !req.getModulePreferences().isEmpty()) {
            moduleItems = req.getModulePreferences().stream()
                    .map(p -> new ModulePreferenceService.PreferenceItem(p.getCode(), p.getRank(), p.getLevel()))
                    .collect(Collectors.toList());
            log.info("解析后moduleItems: {}", moduleItems.stream().map(m -> m.getCode()+" rank="+m.getRank()+" level="+m.getLevel()).collect(Collectors.joining(", ")));
            session.setHasModulePreference(1);
        } else {
            log.warn("未收到模块偏好或为空！req.getModulePreferences()={}", req.getModulePreferences());
        }
        sessionMapper.insert(session);

        // 保存偏好必须在session.id生成之后
        if (moduleItems != null) {
            modulePreferenceService.savePreference(session.getId(), userId, moduleItems);
        }

        SkillQuestion first = candidates.get(0);
        String abilityTag = resolveAbilityTag(first.getId());
        saveInterviewerMessage(session.getId(), first.getId(), MSG_MAIN, 1, first.getContent(), abilityTag);

        InterviewStartResponse resp = new InterviewStartResponse();
        resp.setSessionId(session.getId());
        resp.setJobName(job.getName());
        resp.setQuestion(toView(first, 1, abilityTag));
        resp.setDurationSeconds(duration);
        return resp;
    }

    // ============================ 2. 提交回答 ============================

    public InterviewStep answer(Long sessionId, AnswerRequest req) {
        InterviewSession session = requireOngoingSession(sessionId);

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

        saveMessage(sessionId, req.getQuestionId(), round, ROLE_CANDIDATE, MSG_ANSWER,
                req.getAnswer(), parentMain.getAbilityTag());

        SkillQuestion question = skillQuestionMapper.selectById(req.getQuestionId());
        // 体验题（questionId=0）：从 parentMain 中取题目内容和参考答案
        String questionContent = (question != null) ? question.getContent() : parentMain.getContent();
        String questionRefAnswer = (question != null) ? question.getReferenceAnswer() : parentMain.getReferenceAnswer();
        String abilityTag = parentMain.getAbilityTag();

        boolean followupExists = messageMapper.selectCount(
                new LambdaQueryWrapper<InterviewMessage>()
                        .eq(InterviewMessage::getSessionId, sessionId)
                        .eq(InterviewMessage::getQuestionId, req.getQuestionId())
                        .eq(InterviewMessage::getMsgType, MSG_FOLLOWUP)) > 0;

        InterviewStep step = new InterviewStep();
        if (!followupExists && shouldFollowUp(req.getAnswer())) {
            FollowupResult followup = generateFollowup(session, question, abilityTag,
                    req.getAnswer(), questionContent, questionRefAnswer);
            if (followup == null) {
                // AI 判定无需追问，直接进入下一题
                step.setNextAction(hasTimeLeft(session) ? ACTION_NEXT : ACTION_FINISHABLE);
                return step;
            }
            saveMessage(sessionId, req.getQuestionId(), round, ROLE_INTERVIEWER, MSG_FOLLOWUP,
                    followup.followUpQuestion, abilityTag);

            saveFollowupRecord(session, question, req, abilityTag, followup);

            step.setNextAction(ACTION_FOLLOWUP);
            step.setFollowupQuestion(followup.followUpQuestion);
            return step;
        }

        step.setNextAction(hasTimeLeft(session) ? ACTION_NEXT : ACTION_FINISHABLE);
        return step;
    }

    // ============================ 3. 下一题 ============================

    public InterviewStep next(Long sessionId) {
        InterviewSession session = requireOngoingSession(sessionId);
        Set<Long> asked = askedMainQuestionIds(sessionId);

        InterviewStep step = new InterviewStep();

        // 时间到 → 结束面试（前端计时器为主控，后端作为安全网）
        if (isTimeExceeded(session)) {
            step.setNextAction(ACTION_FINISHABLE);
            return step;
        }

        Resume resume = resumeService.getMine();
        List<String> userTags = extractTagsFromResume(resume);
        // 合并岗位标签，与start()保持一致，避免仅用用户标签导致候选不足
        List<String> jobTags = extractTagsFromJob(jobMapper.selectById(session.getJobId()));
        List<SkillQuestion> candidates = candidateQuestionsByTags(
                jobTags, userTags, session.getDifficulty());

        SkillQuestion nextQuestion = candidates.stream()
                .filter(q -> !asked.contains(q.getId()))
                .findFirst()
                .orElse(null);

        if (nextQuestion == null) {
            step.setNextAction(ACTION_FINISHABLE);
            return step;
        }

        // 题号按已出主问题条数递增（含体验题），保证体验题也占用一个题号
        int round = askedMainMessages(sessionId).size() + 1;

        // 判断本轮是否为体验式题目槽位
        if (isExperienceQuestionSlot(sessionId, round)) {
            InterviewStep expStep = buildExperienceQuestionStep(session, round);
            if (expStep != null) {
                return expStep;
            }
            // 体验题生成失败 → 回退到题库选题（继续往下走）
        }

        String abilityTag = resolveAbilityTag(nextQuestion.getId());
        saveInterviewerMessage(sessionId, nextQuestion.getId(), MSG_MAIN, round, nextQuestion.getContent(), abilityTag);

        step.setNextAction(ACTION_NEXT);
        step.setQuestion(toView(nextQuestion, round, abilityTag));
        return step;
    }

    // ============================ 4. 结束面试 ============================

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

        // 异步生成报告（不阻塞返回，前端轮询 report-status 接口获取结果）
        CompletableFuture.runAsync(() -> {
            try {
                Long reportId = reportService.generateForSession(session);
                moduleScoreService.scoreAndUpdateReport(sessionId, reportId);
                log.info("异步报告生成完成 sessionId={} reportId={}", sessionId, reportId);
            } catch (Exception e) {
                log.error("异步报告生成失败 sessionId={}", sessionId, e);
            }
        });

        return sessionId; // 立即返回 sessionId，前端用此轮询报告状态
    }

    /** 检查报告是否已生成完毕（含模块评分） */
    public boolean isReportReady(Long sessionId) {
        InterviewReport report = reportService.getReportBySession(sessionId);
        return report != null && report.getOverallMatchScore() != null;
    }

    /** 获取已就绪的报告 ID */
    public Long getReadyReportId(Long sessionId) {
        InterviewReport report = reportService.getReportBySession(sessionId);
        if (report != null && report.getOverallMatchScore() != null) {
            return report.getId();
        }
        return null;
    }

    /** 删除面试会话及其关联数据（消息+报告+维度+追问记录），仅允许操作本人会话 */
    public void delete(Long sessionId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException("会话不存在");
        }
        if (!session.getUserId().equals(UserContext.getUserId())) {
            throw new BizException("无权操作该会话");
        }
        // 删除关联的报告（含维度）、追问记录、消息
        InterviewReport report = reportService.getReportBySession(sessionId);
        if (report != null) {
            dimensionMapper.delete(new LambdaQueryWrapper<ReportDimension>()
                    .eq(ReportDimension::getReportId, report.getId()));
            reportMapper.deleteById(report.getId());
        }
        followupRecordService.deleteBySession(sessionId);
        messageMapper.delete(new LambdaQueryWrapper<InterviewMessage>()
                .eq(InterviewMessage::getSessionId, sessionId));
        sessionMapper.deleteById(sessionId);
    }

    /** 批量删除面试会话；单条失败不影响其余会话。 */
    @Transactional
    public Map<String, Object> deleteBatch(List<Long> sessionIds) {
        int deleted = 0;
        int failed = 0;
        if (sessionIds != null) {
            for (Long id : sessionIds) {
                if (id == null) {
                    failed++;
                    continue;
                }
                try {
                    delete(id);
                    deleted++;
                } catch (BizException e) {
                    log.warn("批量删除会话失败 sessionId={}, reason={}", id, e.getMessage());
                    failed++;
                }
            }
        }
        return Map.of("deleted", deleted, "failed", failed);
    }

    // ============================ 标签化出题 ============================

    /**
     * 从用户简历画像中提取技能标签名列表。
     */
    private List<String> extractTagsFromResume(Resume resume) {
        if (resume == null) return Collections.emptyList();

        List<String> tags = new ArrayList<>();
        tags.addAll(parseJsonList(resume.getSkills()));
        tags.addAll(parseJsonList(resume.getKeywords()));
        return tags.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 从岗位的 abilities / keywords 中提取标签名列表。
     */
    private List<String> extractTagsFromJob(JobPosition job) {
        if (job == null) return Collections.emptyList();

        List<String> tags = new ArrayList<>();
        tags.addAll(parseJsonList(job.getAbilities()));
        tags.addAll(parseJsonList(job.getKeywords()));
        return tags.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 根据岗位标签与画像标签匹配题目。
     *
     * 岗位题排在前、画像题排在后：next() 用 findFirst() 取未问过的题，所以顺序即优先级。
     * 不加这个区分的话，简历里写了 Redis/Kafka/微服务的后端同学去面「产品经理」「数据分析」
     * 也会被问到 Redis 持久化、缓存雪崩 —— 岗位形同虚设（实测过）。画像题保留在后面兜底，
     * 岗位题池不足时仍然用得上。
     */
    private List<SkillQuestion> candidateQuestionsByTags(List<String> jobTags,
                                                        List<String> resumeTags,
                                                        int difficulty) {
        // Step 1: 分别匹配岗位标签与画像标签
        List<Long> jobTagIds = matchTagIds(jobTags);
        List<Long> resumeTagIds = matchTagIds(resumeTags);

        List<Long> jobQuestionIds = questionIdsByTagIds(jobTagIds);
        Set<Long> jobQuestionIdSet = new HashSet<>(jobQuestionIds);

        // Step 2: 画像命中但不属于岗位题的，作为低优先级补充
        List<Long> resumeQuestionIds = questionIdsByTagIds(resumeTagIds).stream()
                .filter(id -> !jobQuestionIdSet.contains(id))
                .collect(Collectors.toList());

        if (jobQuestionIds.isEmpty() && resumeQuestionIds.isEmpty()) {
            // 岗位标签和画像标签都没匹配上 → 从全库随机取，保证面试开得起来
            List<SkillQuestion> all = skillQuestionMapper.selectList(
                    new LambdaQueryWrapper<SkillQuestion>()
                            .le(SkillQuestion::getDifficulty, difficulty));
            resumeQuestionIds = all.stream().map(SkillQuestion::getId).collect(Collectors.toList());
        }

        // Step 3: 岗位题先占满候选池，剩余名额才给画像题。
        // 不能先合并再统一截断：简历里写了 30 项技能时画像题动辄上百道，
        // 混在一起随机截断会把岗位题挤掉，面试又变成问简历。
        Collections.shuffle(jobQuestionIds);
        Collections.shuffle(resumeQuestionIds);

        List<Long> finalIds = new ArrayList<>();
        Set<Long> picked = new HashSet<>();
        for (Long id : jobQuestionIds) {
            if (finalIds.size() >= CANDIDATE_POOL_MAX) break;
            if (picked.add(id)) finalIds.add(id);
        }
        for (Long id : resumeQuestionIds) {
            if (finalIds.size() >= CANDIDATE_POOL_MAX) break;
            if (picked.add(id)) finalIds.add(id);
        }

        if (finalIds.isEmpty()) return Collections.emptyList();

        List<SkillQuestion> result = skillQuestionMapper.selectList(
                new LambdaQueryWrapper<SkillQuestion>()
                        .in(SkillQuestion::getId, finalIds)
                        .le(SkillQuestion::getDifficulty, difficulty)
                        .last("LIMIT " + CANDIDATE_POOL_MAX));

        // 二次随机打乱保证每次顺序不同
        // 组内随机（每次面试顺序不同），组间保持岗位优先
        Map<Boolean, List<SkillQuestion>> grouped = result.stream()
                .collect(Collectors.partitioningBy(q -> jobQuestionIdSet.contains(q.getId())));
        List<SkillQuestion> ordered = new ArrayList<>();
        List<SkillQuestion> jobFirst = new ArrayList<>(grouped.get(true));
        List<SkillQuestion> resumeLast = new ArrayList<>(grouped.get(false));
        Collections.shuffle(jobFirst);
        Collections.shuffle(resumeLast);
        ordered.addAll(jobFirst);
        ordered.addAll(resumeLast);
        return ordered;
    }

    /** 按标签 ID 集合取出关联的题目 ID（去重，无匹配返回空列表）。 */
    private List<Long> questionIdsByTagIds(List<Long> tagIds) {
        if (tagIds.isEmpty()) return Collections.emptyList();
        return skillQuestionTagRelMapper.selectList(
                        new LambdaQueryWrapper<SkillQuestionTagRel>()
                                .in(SkillQuestionTagRel::getTagId, tagIds))
                .stream()
                .map(SkillQuestionTagRel::getQuestionId)
                .distinct()
                .collect(Collectors.toList());
    }

    /** 纯 ASCII 字符串（拉丁字母/数字/符号），用于决定要不要做词边界校验。 */
    private static final Pattern ASCII_ONLY = Pattern.compile("^[\\x00-\\x7f]+$");

    /**
     * 将用户技能名匹配到数据库 skill_tag 表的 ID。
     */
    private List<Long> matchTagIds(List<String> userTags) {
        if (userTags.isEmpty()) return Collections.emptyList();

        // 取所有标签，做模糊匹配（用户标签可能是 "Spring Boot"，库里有 "Spring" 和 "Spring Boot"）
        List<SkillTag> allTags = skillTagMapper.selectList(new LambdaQueryWrapper<>());
        Set<Long> matched = new LinkedHashSet<>();

        for (String userTag : userTags) {
            String lower = userTag.toLowerCase().trim();
            if (lower.isEmpty()) continue;
            for (SkillTag t : allTags) {
                String tagName = t.getName().toLowerCase();
                // 双向包含匹配：用户画像的 "Spring Boot" 匹配库里的 "Spring"
                if (containsTag(lower, tagName) || containsTag(tagName, lower)) {
                    matched.add(t.getId());
                }
            }
        }
        return new ArrayList<>(matched);
    }

    /**
     * 判断 haystack 是否命中 needle。
     *
     * 中文照旧走子串匹配（「性能」命中「性能优化」），但**纯 ASCII 的 needle 要求整词命中**。
     * 否则短标签会把无关内容一网打尽，实测踩过的坑：
     * 标签 `C` 命中 "Spring Cloud"、标签 `Go` 命中 "django"、`java` 命中 "javascript"、
     * `BI` 命中 "RabbitMQ"、`ROI` 命中 "android" —— Python 后端面试会问到 C 语言指针。
     * 词边界只认 [a-z0-9]，所以 "node" 仍能命中 "node.js"、"vue" 命中 "vue.js"，
     * 而 "spring" 不再命中 "springboot"（本来靠空格差异也没命中过）。
     */
    private static boolean containsTag(String haystack, String needle) {
        if (needle.isEmpty() || haystack.isEmpty()) return false;
        if (!haystack.contains(needle)) return false;
        if (!ASCII_ONLY.matcher(needle).matches()) return true;
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(needle) + "(?![a-z0-9])")
                .matcher(haystack).find();
    }

    /**
     * 解析题目所属的能力标签名（取第一个关联标签名）。
     *
     * 必须显式 orderByAsc(id)：不加 ORDER BY 时 MySQL 走 uk_qt(question_id, tag_id)
     * 索引，返回的是 tag_id 顺序，主标签会变成「id 最小的那个标签」而不是录入时的第一个，
     * 报告页的能力标签会串。
     */
    private String resolveAbilityTag(Long questionId) {
        List<SkillQuestionTagRel> rels = skillQuestionTagRelMapper.selectList(
                new LambdaQueryWrapper<SkillQuestionTagRel>()
                        .eq(SkillQuestionTagRel::getQuestionId, questionId)
                        .orderByAsc(SkillQuestionTagRel::getId));
        if (rels.isEmpty()) return "综合";

        SkillTag tag = skillTagMapper.selectById(rels.get(0).getTagId());
        return tag != null ? tag.getName() : "综合";
    }

    /** 解析 JSON 数组字符串 */
    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // ============================ 共享辅助 ============================

    private Set<Long> askedMainQuestionIds(Long sessionId) {
        return askedMainMessages(sessionId).stream()
                .map(InterviewMessage::getQuestionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 已出的主问题消息（按顺序）。
     *
     * 题号必须按**消息条数**算，不能按 askedMainQuestionIds().size()：体验题的
     * questionId 是占位值 0，会被 LinkedHashSet 去重，导致体验题出完后集合不增长
     * → 题号原地踏步 → 同一槽位被反复判定命中，面试里连出两道一模一样的体验题
     * （实测 session 239 出现两条 round_no=7）。
     */
    private List<InterviewMessage> askedMainMessages(Long sessionId) {
        return messageMapper.selectList(
                new LambdaQueryWrapper<InterviewMessage>()
                        .eq(InterviewMessage::getSessionId, sessionId)
                        .eq(InterviewMessage::getRole, ROLE_INTERVIEWER)
                        .eq(InterviewMessage::getMsgType, MSG_MAIN)
                        .orderByAsc(InterviewMessage::getId));
    }

    /**
     * 面试是否还有剩余时间。
     *
     * 面试长度由用户选的时长驱动（前端默认 1800 秒），**不设题量上限**：
     * 只要时间没到就继续出题。题目抽完了由 next() 返回 FINISHABLE 收口，
     * 所以这里不需要再数题数。（原先有个 MAX_QUESTIONS=8 的上限，但它只在
     * answer() 不触发追问的分支上检查，只要每题都产生追问就形同虚设。）
     */
    private boolean hasTimeLeft(InterviewSession session) {
        return !isTimeExceeded(session);
    }

    /**
     * 检查面试是否超时（基于 durationSeconds 与 startTime 计算）。
     */
    private boolean isTimeExceeded(InterviewSession session) {
        return isTimeExceeded(session, LocalDateTime.now());
    }

    static boolean isTimeExceeded(InterviewSession session, LocalDateTime now) {
        if (session.getDurationSeconds() == null || session.getStartTime() == null) {
            return true;
        }
        long elapsed = java.time.Duration.between(session.getStartTime(), now).getSeconds();
        return elapsed >= session.getDurationSeconds();
    }

    private InterviewSession requireOngoingSession(Long sessionId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) throw new BizException("会话不存在");
        if (!session.getUserId().equals(UserContext.getUserId())) throw new BizException("无权操作该会话");
        if (!STATUS_ONGOING.equals(session.getStatus())) throw new BizException("面试已结束");
        return session;
    }

    private void saveInterviewerMessage(Long sessionId, Long questionId, String msgType,
                                         int round, String content, String abilityTag) {
        saveMessage(sessionId, questionId, round, ROLE_INTERVIEWER, msgType, content, abilityTag);
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
        messageMapper.insert(msg);
    }

    private QuestionView toView(SkillQuestion q, int round, String abilityTag) {
        QuestionView view = new QuestionView();
        view.setId(q.getId());
        view.setContent(q.getContent());
        view.setAbilityTag(abilityTag);
        view.setRoundNo(round);
        return view;
    }

    private int normalizeDifficulty(Integer difficulty) {
        if (difficulty == null || difficulty < 1 || difficulty > 3) return 2;
        return difficulty;
    }

    // ============================ 追问引擎（V2：DeepSeek 智能决策） ============================

    private boolean shouldFollowUp(String answer) {
        // V2: 所有回答都先进入追问流程，具体是规则兜底还是 DeepSeek 决策
        // 由 generateFollowup() 内部处理。AI 判定无需追问时返回 null，此处跳过。
        return true;
    }

    private FollowupResult generateFollowup(InterviewSession session, SkillQuestion question,
                                             String abilityTag, String answer,
                                             String questionContent, String refAnswer) {
        // 回答 < 15 字：走规则兜底，不浪费 AI 调用
        String text = answer == null ? "" : answer.trim();
        if (text.length() < 15) {
            return new FollowupResult(
                    "你的回答比较简短，能否详细说说你的思路或做法？",
                    "RULE", "回答过于简短，缺少具体细节");
        }

        try {
            FollowUpRequest fr = new FollowUpRequest();
            fr.setPosition(resolveJobName(session));
            fr.setQuestion(questionContent);
            fr.setAnswer(answer);
            fr.setReferenceAnswer(refAnswer);

            FollowUpResponse resp = followUpService.generate(fr);

            // AI 判定不需要追问
            if (resp == null) {
                log.info("[面试追问] AI判定无需追问 sessionId={}, questionId={}",
                        session.getId(), question != null ? question.getId() : null);
                return null; // 信号：跳过追问，直接进入下一题
            }

            if (resp.getFollowUpQuestion() != null && !resp.getFollowUpQuestion().isBlank()) {
                log.info("[面试追问] sessionId={}, questionId={}, source={}",
                        session.getId(), question != null ? question.getId() : null, resp.getSource());
                return new FollowupResult(resp.getFollowUpQuestion().trim(), resp.getSource(), resp.getTriggerReason());
            }

            log.warn("[面试追问] FollowUpService 返回空，回退题库兜底 sessionId={}", session.getId());
        } catch (Exception e) {
            log.warn("[面试追问] FollowUpService 异常，回退题库兜底 sessionId={}, err={}",
                    session.getId(), e.getMessage());
        }
        return new FollowupResult(buildFollowup(question, abilityTag), "RULE",
                "FollowUpService异常或返回为空，使用题库兜底追问");
    }

    private void saveFollowupRecord(InterviewSession session, SkillQuestion question, AnswerRequest req,
                                    String abilityTag, FollowupResult followup) {
        InterviewFollowupRecord record = new InterviewFollowupRecord();
        record.setUserId(session.getUserId());
        record.setSessionId(session.getId());
        record.setJobId(session.getJobId());
        record.setQuestionId(req.getQuestionId());
        record.setPosition(resolveJobName(session));
        record.setOriginalQuestion(question != null ? question.getContent() : null);
        record.setUserAnswer(req.getAnswer());
        record.setFollowUpQuestion(followup.followUpQuestion);
        record.setSource(followup.source);
        record.setTriggerReason(followup.triggerReason);
        record.setAbilityTag(abilityTag);
        followupRecordService.saveSafely(record);
    }

    private static class FollowupResult {
        final String followUpQuestion;
        final String source;
        final String triggerReason;
        FollowupResult(String q, String s, String r) { this.followUpQuestion = q; this.source = s; this.triggerReason = r; }
    }

    private String resolveJobName(InterviewSession session) {
        JobPosition job = jobMapper.selectById(session.getJobId());
        String name = (job != null) ? job.getName() : null;
        return (name != null && !name.isBlank()) ? name : "该岗位";
    }

    // ============================ 体验式题目 ============================

    /**
     * 判定第 roundNo 题是否为体验式题目槽位（确定性，无状态）。
     * 规则：roundNo &lt; EXPERIENCE_FIRST_BLOCK_SLOT → false；
     * roundNo &gt;= EXPERIENCE_FIRST_BLOCK_SLOT 时，每 5 题一块，
     * 块内用 sessionId+blockIndex 做种子随机选一个位置放体验题。
     */
    private boolean isExperienceQuestionSlot(Long sessionId, int roundNo) {
        if (roundNo < EXPERIENCE_FIRST_BLOCK_SLOT) return false;
        int blockIndex = (roundNo - 1) / EXPERIENCE_BLOCK_SIZE;
        int blockStart = blockIndex * EXPERIENCE_BLOCK_SIZE + 1;
        // 用 sessionId + blockIndex 做种子，确定性选一个槽位
        int offset = (int) ((sessionId * 31L + blockIndex * 17L) % EXPERIENCE_BLOCK_SIZE);
        int experienceSlot = blockStart + offset;
        return roundNo == experienceSlot;
    }

    /**
     * 构建体验式题目 InterviewStep。失败时返回 null，由调用方回退到题库选题。
     */
    private InterviewStep buildExperienceQuestionStep(InterviewSession session, int roundNo) {
        Resume resume = resumeService.getMine();
        JobPosition job = jobMapper.selectById(session.getJobId());
        String jobName = (job != null) ? job.getName() : null;
        if (jobName == null || jobName.isBlank()) jobName = "该岗位";
        // 岗位能力项，用来把体验题的焦点技能框在岗位域内（否则会照着简历报出跑题的技术栈）
        List<String> jobTags = extractTagsFromJob(job);

        try {
            // 收集已问题目，传给 AI 以避免重复
            List<String> askedContents = messageMapper.selectList(
                    new LambdaQueryWrapper<InterviewMessage>()
                            .eq(InterviewMessage::getSessionId, session.getId())
                            .eq(InterviewMessage::getRole, ROLE_INTERVIEWER)
                            .eq(InterviewMessage::getMsgType, MSG_MAIN)
                            .orderByAsc(InterviewMessage::getId))
                    .stream()
                    .map(m -> m.getContent() != null ? m.getContent() : "")
                    .collect(Collectors.toList());

            ExperienceQuestionService.ExperienceQuestionResult result =
                    experienceQuestionService.generate(jobName, resume, askedContents, jobTags);
            String labeledContent = result.getQuestion();

            InterviewMessage msg = new InterviewMessage();
            msg.setSessionId(session.getId());
            msg.setQuestionId(0L); // 体验题占位ID（非题库题目）
            msg.setRoundNo(roundNo);
            msg.setRole(ROLE_INTERVIEWER);
            msg.setMsgType(MSG_MAIN);
            msg.setContent(labeledContent);
            msg.setAbilityTag(result.getAbilityTag() != null ? result.getAbilityTag() : "综合能力");
            msg.setQuestionType(QUESTION_TYPE_EXPERIENCE);
            msg.setReferenceAnswer(result.getReferenceAnswer());
            messageMapper.insert(msg);

            QuestionView view = new QuestionView();
            view.setId(0L);
            view.setContent(labeledContent);
            view.setAbilityTag(msg.getAbilityTag());
            view.setRoundNo(roundNo);
            view.setQuestionType(QUESTION_TYPE_EXPERIENCE);

            InterviewStep step = new InterviewStep();
            step.setNextAction(ACTION_NEXT);
            step.setQuestion(view);
            return step;
        } catch (Exception e) {
            log.warn("[体验题] 生成失败，回退题库选题: sessionId={}, roundNo={}, err={}",
                    session.getId(), roundNo, e.getMessage());
            return null;
        }
    }

    private String buildFollowup(SkillQuestion question, String abilityTag) {
        if (question != null && question.getFollowupGuide() != null
                && !question.getFollowupGuide().trim().isEmpty()) {
            return question.getFollowupGuide().trim();
        }
        String tag = (abilityTag != null && !abilityTag.isBlank()) ? abilityTag : "这个问题";
        return "能否结合你的具体项目，再深入说明一下「" + tag + "」相关的细节和你的思考？";
    }
}
