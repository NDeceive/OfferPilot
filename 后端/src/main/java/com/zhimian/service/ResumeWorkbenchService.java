package com.zhimian.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.BizException;
import com.zhimian.config.UserContext;
import com.zhimian.dto.ResumeAnalysis;
import com.zhimian.entity.JobPosition;
import com.zhimian.entity.Resume;
import com.zhimian.mapper.JobPositionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** ResumeForge-inspired workflows adapted to OfferPilot's authenticated MySQL model. */
@Service
@DependsOn("studentBackendSchema")
@RequiredArgsConstructor
public class ResumeWorkbenchService {
    private static final Set<String> CATEGORIES = Set.of("EDUCATION", "EXPERIENCE", "PROJECT", "SKILL", "AWARD");
    private static final Set<String> STATUSES = Set.of("PENDING", "CONFIRMED", "EXPIRED", "REJECTED");
    private static final Set<String> RESPONSIBILITIES = Set.of("PARTICIPATED", "MODULE", "LED", "OWNER");
    private static final List<String> DEFAULT_MODULES = List.of(
            "technical_base", "technical_depth", "engineering_practice", "problem_analysis", "project_expression");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ResumeAnalyzer analyzer;
    private final ResumeService legacyResumes;
    private final JobPositionMapper jobs;
    private final ResumeAiComposer aiComposer;

    public record ClaimInput(String category, String title, String factText, String resumeText,
                             String responsibility, String personalBoundary, String verificationStatus,
                             String sourceExcerpt) {}

    public record ClaimView(long id, String category, String title, String factText, String resumeText,
                            String responsibility, String personalBoundary, String verificationStatus,
                            String sourceExcerpt, LocalDateTime updatedAt) {}

    public record ResumeContent(String name, String phone, String email, String targetJob, String summary,
                                String education, String experience, String projects, String skills, String awards) {}

    public record VersionInput(String title, Long jobId, ResumeContent content) {}
    public record GenerateInput(String title, Long jobId, String name, String phone, String email) {}
    public record VersionSummary(long id, Long jobId, String title, String sourceKind, LocalDateTime createdAt) {}
    public record VersionView(long id, Long jobId, String title, String sourceKind, ResumeContent content,
                              LocalDateTime createdAt) {}
    public record Finding(String level, String title, String detail, String moduleCode) {}
    public record Review(String status, Long versionId, Long jobId, List<Finding> findings,
                         List<String> suggestedModules, String summary) {}

    public List<ClaimView> claims() {
        return jdbc.query("SELECT * FROM resume_claim WHERE user_id=? AND deleted_at IS NULL ORDER BY id DESC",
                (rs, row) -> new ClaimView(rs.getLong("id"), rs.getString("category"), rs.getString("title"),
                        rs.getString("fact_text"), rs.getString("resume_text"), rs.getString("responsibility"),
                        rs.getString("personal_boundary"), rs.getString("verification_status"),
                        rs.getString("source_excerpt"), rs.getTimestamp("updated_at").toLocalDateTime()),
                userId());
    }

    /** Imported text remains a draft; only the candidate can confirm a factual claim. */
    public List<ClaimInput> suggestionsFromCurrentResume() {
        Resume legacy = legacyResumes.getMine();
        if (legacy == null || legacy.getRawText() == null || legacy.getRawText().isBlank())
            throw new BizException("当前账号还没有可提取的简历，请先在此页上传 PDF 或 Word 简历");
        ResumeAnalysis extracted = analyzer.analyze(legacy.getRawText());
        List<ClaimInput> suggestions = new ArrayList<>();
        for (String project : Objects.requireNonNullElse(extracted.getProjects(), List.<String>of()).stream().limit(8).toList())
            suggestions.add(new ClaimInput("PROJECT", "待核实的项目经历", project, project,
                    "PARTICIPATED", "", "PENDING", project));
        for (String skill : Objects.requireNonNullElse(extracted.getSkills(), List.<String>of()).stream().limit(20).toList())
            suggestions.add(new ClaimInput("SKILL", skill, "简历中出现技术词：" + skill, skill,
                    "PARTICIPATED", "", "PENDING", skill));
        return suggestions;
    }

    @Transactional
    public ClaimView addClaim(ClaimInput input) {
        ClaimInput valid = validateClaim(input);
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO resume_claim(user_id,category,title,fact_text,resume_text,responsibility,personal_boundary,verification_status,source_excerpt) VALUES (?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId());
            ps.setString(2, valid.category());
            ps.setString(3, valid.title());
            ps.setString(4, valid.factText());
            ps.setString(5, valid.resumeText());
            ps.setString(6, valid.responsibility());
            ps.setString(7, valid.personalBoundary());
            ps.setString(8, valid.verificationStatus());
            ps.setString(9, valid.sourceExcerpt());
            return ps;
        }, key);
        return requireClaim(key.getKey().longValue());
    }

    @Transactional
    public ClaimView updateClaim(long id, ClaimInput input) {
        requireClaim(id);
        ClaimInput valid = validateClaim(input);
        jdbc.update("UPDATE resume_claim SET category=?,title=?,fact_text=?,resume_text=?,responsibility=?,personal_boundary=?,verification_status=?,source_excerpt=? WHERE id=? AND user_id=? AND deleted_at IS NULL",
                valid.category(), valid.title(), valid.factText(), valid.resumeText(), valid.responsibility(),
                valid.personalBoundary(), valid.verificationStatus(), valid.sourceExcerpt(), id, userId());
        return requireClaim(id);
    }

    public void deleteClaim(long id) {
        if (jdbc.update("UPDATE resume_claim SET deleted_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND deleted_at IS NULL", id, userId()) == 0)
            throw new BizException("事实条目不存在");
    }

    private ClaimView requireClaim(long id) {
        return claims().stream().filter(item -> item.id() == id).findFirst()
                .orElseThrow(() -> new BizException("事实条目不存在"));
    }

    private ClaimInput validateClaim(ClaimInput input) {
        if (input == null || !CATEGORIES.contains(input.category()) || !STATUSES.contains(input.verificationStatus())
                || !RESPONSIBILITIES.contains(input.responsibility()))
            throw new BizException("事实条目的分类、核实状态或承担程度无效");
        String title = limited(input.title(), 120);
        String fact = limited(input.factText(), 4000);
        String wording = limited(input.resumeText(), 4000);
        if (title.isBlank() || fact.isBlank() || wording.isBlank())
            throw new BizException("标题、原始事实和简历表述不能为空");
        String boundary = limited(input.personalBoundary(), 2000);
        if (("LED".equals(input.responsibility()) || "OWNER".equals(input.responsibility())) && boundary.isBlank())
            throw new BizException("主导或负责项目时，请写明个人与团队的工作边界");
        return new ClaimInput(input.category(), title, fact, wording, input.responsibility(), boundary,
                input.verificationStatus(), limited(input.sourceExcerpt(), 2000));
    }

    public List<VersionSummary> versions() {
        return jdbc.query("SELECT id,job_id,title,source_kind,created_at FROM resume_version WHERE user_id=? ORDER BY id DESC",
                (rs, row) -> new VersionSummary(rs.getLong("id"), (Long) rs.getObject("job_id"),
                        rs.getString("title"), rs.getString("source_kind"), rs.getTimestamp("created_at").toLocalDateTime()),
                userId());
    }

    public VersionView version(long id) {
        List<VersionView> found = jdbc.query(
                "SELECT id,job_id,title,source_kind,content_json,created_at FROM resume_version WHERE id=? AND user_id=?",
                (rs, row) -> new VersionView(rs.getLong("id"), (Long) rs.getObject("job_id"),
                        rs.getString("title"), rs.getString("source_kind"), parseContent(rs.getString("content_json")),
                        rs.getTimestamp("created_at").toLocalDateTime()), id, userId());
        if (found.isEmpty()) throw new BizException("简历版本不存在");
        return found.get(0);
    }

    @Transactional
    public VersionView saveVersion(VersionInput input) {
        if (input == null) throw new BizException("请填写简历内容");
        return insertVersion(input.title(), input.jobId(), input.content(), "MANUAL");
    }

    public VersionView generate(GenerateInput input) {
        if (input == null || input.jobId() == null) throw new BizException("请先选择目标岗位");
        JobPosition job = requireJob(input.jobId());
        List<ClaimView> confirmed = claims().stream()
                .filter(item -> "CONFIRMED".equals(item.verificationStatus())).toList();
        if (confirmed.isEmpty()) throw new BizException("请先核实至少一条事实，再生成简历");
        ResumeContent content = aiComposer.compose(job, confirmed, input);
        return insertVersion(input.title() == null || input.title().isBlank() ? job.getName() + "定制简历" : input.title(),
                input.jobId(), content, "AI_GENERATED");
    }

    private VersionView insertVersion(String title, Long jobId, ResumeContent content, String source) {
        String safeTitle = limited(title, 120);
        if (safeTitle.isBlank()) safeTitle = "我的简历";
        if (jobId != null) requireJob(jobId);
        ResumeContent safeContent = normalize(content);
        String body = String.join("", safeContent.summary(), safeContent.education(), safeContent.experience(),
                safeContent.projects(), safeContent.skills(), safeContent.awards()).replaceAll("\\s", "");
        if (body.length() < 4)
            throw new BizException("简历内容过少，请补充至少一段经历或技能");
        String serialized;
        try { serialized = json.writeValueAsString(safeContent); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
        String finalTitle = safeTitle;
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO resume_version(user_id,job_id,title,source_kind,content_json) VALUES (?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId());
            if (jobId == null) ps.setNull(2, java.sql.Types.BIGINT); else ps.setLong(2, jobId);
            ps.setString(3, finalTitle);
            ps.setString(4, source);
            ps.setString(5, serialized);
            return ps;
        }, key);
        return version(key.getKey().longValue());
    }

    public Review review(Long versionId, Long jobId) {
        if (jobId == null) throw new BizException("请先选择目标岗位");
        JobPosition job = requireJob(jobId);
        ResumeContent content;
        boolean legacy = versionId == null;
        if (versionId != null) {
            VersionView selected = version(versionId);
            if (selected.jobId() != null && !selected.jobId().equals(jobId))
                throw new BizException("简历版本与目标岗位不匹配");
            content = selected.content();
        }
        else {
            Resume resume = legacyResumes.getMine();
            if (resume == null || resume.getRawText() == null || resume.getRawText().isBlank())
                return new Review("NO_RESUME", null, jobId, List.of(), DEFAULT_MODULES, "暂无简历，可跳过评价并自行选择训练目标");
            String raw = resume.getRawText();
            content = new ResumeContent("", "", "", job.getName(), "", "", "", raw, "", "");
        }
        String text = rawText(content);
        ResumeAnalysis analyzed = analyzer.analyze(text);
        List<Finding> findings = new ArrayList<>();
        LinkedHashSet<String> suggested = new LinkedHashSet<>();
        if ((legacy || content.projects().isBlank()) && (analyzed.getProjects() == null || analyzed.getProjects().isEmpty())) {
            findings.add(new Finding("ACTION", "项目证据不足", "补充你负责的任务、技术选择与实际结果，再练习结构化项目表达。", "project_expression"));
            suggested.add("project_expression"); suggested.add("engineering_practice");
        }
        if (analyzed.getSkills() == null || analyzed.getSkills().isEmpty()) {
            findings.add(new Finding("ACTION", "技能信息较少", "补充确实掌握的技术及使用场景，训练基础知识与技术深度。", "technical_base"));
            suggested.add("technical_base"); suggested.add("technical_depth");
        }
        List<String> jobTerms = parseStringList(job.getKeywords());
        List<String> hits = jobTerms.stream().filter(term -> term != null && term.length() > 1
                && text.toLowerCase().contains(term.toLowerCase())).limit(5).toList();
        if (!jobTerms.isEmpty() && hits.isEmpty()) {
            findings.add(new Finding("ACTION", "岗位关联信息不足", "当前简历未体现岗位关键词；请核对真实经历，并优先练习岗位认知。", "position_cognition"));
            suggested.add("position_cognition");
        } else if (!hits.isEmpty()) {
            findings.add(new Finding("INFO", "找到岗位相关证据", "简历中出现：" + String.join("、", hits) + "。关键词出现不等于已熟练掌握。", "position_cognition"));
        }
        if (!legacy && content.experience().isBlank() && content.education().isBlank())
            findings.add(new Finding("ACTION", "基本经历不完整", "补充教育或实习经历，使简历更容易核对。", "logical_structure"));
        if (text.contains("【待补") || text.contains("【待确认"))
            findings.add(new Finding("BLOCKING", "仍有待补内容", "正式导出前请补齐并核实占位内容。", "project_review"));
        if (findings.isEmpty()) findings.add(new Finding("INFO", "基础信息已具备", "可继续按目标岗位选择训练重点；这不是招聘录用评估。", "project_expression"));
        suggested.add("project_review"); suggested.add("problem_analysis");
        suggested.addAll(DEFAULT_MODULES);
        return new Review("READY", versionId, jobId, findings, suggested.stream().limit(5).toList(),
                "根据简历与岗位信息给出的训练建议，最终目标由你确认");
    }

    public Resume interviewSnapshot(long versionId) {
        VersionView selected = version(versionId);
        String text = rawText(selected.content());
        ResumeAnalysis analysis = analyzer.analyze(text);
        Resume result = new Resume();
        result.setUserId(userId());
        result.setRawText(text);
        try {
            result.setSkills(json.writeValueAsString(analysis.getSkills()));
            result.setKeywords(json.writeValueAsString(analysis.getKeywords()));
            result.setProjects(json.writeValueAsString(analysis.getProjects()));
        } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
        return result;
    }

    public String rawText(ResumeContent content) {
        StringBuilder text = new StringBuilder();
        append(text, "姓名", content.name()); append(text, "电话", content.phone());
        append(text, "邮箱", content.email()); append(text, "求职意向", content.targetJob());
        append(text, "个人简介", content.summary()); append(text, "教育经历", content.education());
        append(text, "实习经历", content.experience()); append(text, "项目经历", content.projects());
        append(text, "专业技能", content.skills()); append(text, "荣誉奖项", content.awards());
        return text.toString().trim();
    }

    private void append(StringBuilder text, String title, String value) {
        if (value != null && !value.isBlank()) text.append(title).append("\n").append(value).append("\n\n");
    }

    public static boolean hasPlaceholder(ResumeContent content) {
        return List.of(content.name(), content.phone(), content.email(), content.targetJob(), content.summary(),
                        content.education(), content.experience(), content.projects(), content.skills(), content.awards()).stream()
                .anyMatch(value -> value != null && (value.contains("【待补") || value.contains("【待确认")));
    }

    private ResumeContent normalize(ResumeContent content) {
        if (content == null) throw new BizException("请填写简历内容");
        ResumeContent safe = new ResumeContent(limited(content.name(), 100), limited(content.phone(), 40),
                limited(content.email(), 160), limited(content.targetJob(), 120), limited(content.summary(), 2500),
                limited(content.education(), 6000), limited(content.experience(), 8000),
                limited(content.projects(), 10000), limited(content.skills(), 4000), limited(content.awards(), 4000));
        if (rawText(safe).length() > 30000) throw new BizException("简历内容不能超过 30000 字符");
        return safe;
    }

    private String limited(String value, int limit) {
        String result = value == null ? "" : value.trim();
        if (result.length() > limit) throw new BizException("字段内容过长，请精简后保存");
        return result;
    }

    private ResumeContent parseContent(String raw) {
        try { return json.readValue(raw, ResumeContent.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("简历版本内容损坏", e); }
    }

    private List<String> parseStringList(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return json.readValue(value, new TypeReference<List<String>>() {}); }
        catch (JsonProcessingException e) { return List.of(); }
    }

    private JobPosition requireJob(long id) {
        JobPosition job = jobs.selectById(id);
        if (job == null || !Integer.valueOf(1).equals(job.getStatus())) throw new BizException("岗位不存在");
        return job;
    }

    private long userId() {
        Long id = UserContext.getUserId();
        if (id == null) throw new BizException("请先登录");
        return id;
    }
}
