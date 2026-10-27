package com.zhimian.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/** Selection only; content updates happen through the audited incremental importer. */
@Service
@RequiredArgsConstructor
public class PracticeBankService {
    private final JdbcTemplate db;
    private final ObjectMapper json;

    public record Selection(List<Map<String,Object>> knowledge, List<Map<String,Object>> coding,
                            Map<String,Object> company, int cultureCount, String notice) {}

    public Map<String,Object> catalog() {
        try {
            var companies = db.queryForList("SELECT id,code,name,aliases_json,evidence_json FROM practice_company WHERE review_status='APPROVED' ORDER BY id");
            var publicCompanies = companies.stream().map(c -> Map.of("id", c.get("id"), "code", c.get("code"), "name", c.get("name"),
                    "aliases", list(c.get("aliases_json")), "evidence", object(c.get("evidence_json")))).toList();
            return Map.of("ready", true, "companies", publicCompanies,
                    "codingCoverage", db.queryForList("SELECT job_codes_json,topics_json FROM practice_coding_question WHERE review_status='APPROVED'").stream().map(q -> Map.of("jobCodes",list(q.get("job_codes_json")),"topics",list(q.get("topics_json")))).distinct().toList(),
                    "knowledgeCount", count("SELECT COUNT(*) FROM practice_question_meta WHERE review_status='APPROVED' AND subtype='KNOWLEDGE'"),
                    "objectiveCoverage", objectiveRows(3).stream().map(q -> Map.of("jobCodes",list(q.get("job_codes_json")),"topics",list(q.get("topics_json")),"type",q.get("subtype"),"difficulty",q.get("difficulty"))).toList(),
                    "cultureCount", count("SELECT COUNT(*) FROM practice_question_meta WHERE review_status='APPROVED' AND subtype='CULTURE'"),
                    "codingCount", count("SELECT COUNT(*) FROM practice_coding_question WHERE review_status='APPROVED'"));
        } catch (BadSqlGrammarException e) {
            return Map.of("ready", false, "companies", List.of(), "notice", "题库扩充尚未初始化，请执行 V6 增量迁移和题目导入。");
        }
    }

    public Selection select(Map<String,Object> job, String companyName, String topic, int difficulty, int knowledgeCount, int codingCount, int total) {
        return select(job,companyName,topic,difficulty,knowledgeCount,codingCount,total,"KNOWLEDGE");
    }

    public Selection select(Map<String,Object> job, String companyName, String topic, int difficulty, int knowledgeCount, int codingCount, int total, String type) {
        if (!Boolean.TRUE.equals(catalog().get("ready"))) throw new BizException("题库扩充尚未初始化，请执行 V6 增量迁移和题目导入；不要重跑清库脚本。");
        String code = String.valueOf(job.get("code"));
        Map<String,Object> company = company(companyName);
        List<Map<String,Object>> culture = new ArrayList<>(), knowledge = new ArrayList<>();
        boolean objectiveOnly = Set.of("CHOICE","MULTIPLE_CHOICE","TRUE_FALSE").contains(type);
        if (objectiveOnly || "ALL".equals(type)) for (var q : objectiveRows(difficulty)) {
            if (matches(q,code,topic) && (!objectiveOnly || type.equals(q.get("subtype")))) knowledge.add(q);
        }
        var curated = db.queryForList("SELECT q.*,m.canonical_id,m.title,m.subtype,m.company_id,m.job_codes_json,m.topics_json,m.tags_json,m.source_json FROM skill_question q JOIN practice_question_meta m ON m.question_id=q.id WHERE m.review_status='APPROVED' AND q.difficulty<=?", difficulty);
        for (var q : curated) {
            if (objectiveOnly) continue;
            if (!matches(q, code, topic)) continue;
            if ("CULTURE".equals(q.get("subtype"))) {
                if (!company.isEmpty() && Objects.equals(((Number)company.get("id")).longValue(), number(q.get("company_id")))) culture.add(q);
            } else knowledge.add(q);
        }
        Collections.shuffle(culture);
        int targetCulture = knowledgeCount == 0 ? 0 : "企业情境".equals(topic) ? knowledgeCount : Math.min(knowledgeCount, (int)Math.round(total * .20));
        int actualCulture = Math.min(targetCulture, culture.size());
        if ("企业情境".equals(topic) && actualCulture < knowledgeCount) throw new BizException("该企业、岗位和难度的情境题不足，请减少题量或选择综合练习。");
        // Legacy questions stay available, scoped using the same word-boundary principle as interview selection.
        if (!objectiveOnly && knowledgeCount > actualCulture && !"企业情境".equals(topic)) knowledge.addAll(legacy(job, topic, difficulty));
        Collections.shuffle(knowledge);
        if (knowledge.size() < knowledgeCount - actualCulture) throw new BizException("当前岗位、专项与难度的" + (objectiveOnly ? "客观题" : "知识题") + "不足（可用 " + knowledge.size() + " 道），请减少题量或选择综合练习。");
        var selected = new ArrayList<Map<String,Object>>(culture.subList(0, actualCulture));
        var concepts = new HashSet<String>();
        if ("ALL".equals(type)) for (String subtype : List.of("CHOICE","MULTIPLE_CHOICE","TRUE_FALSE")) {
            if (selected.size() >= knowledgeCount) break;
            knowledge.stream().filter(q -> subtype.equals(q.get("subtype")) && !concepts.contains(concept(q))).findFirst().ifPresent(q -> { selected.add(q); concepts.add(concept(q)); });
        }
        for (var q : knowledge) {
            if (selected.size() >= knowledgeCount) break;
            if (selected.contains(q) || q.containsKey("options_json") && !concepts.add(concept(q))) continue;
            selected.add(q);
        }
        if (selected.size() < knowledgeCount) throw new BizException("当前专项去重后的题目不足，请减少题量或选择综合练习。");
        Collections.shuffle(selected);
        var coding = new ArrayList<Map<String,Object>>();
        if (codingCount > 0) for (var q : db.queryForList("SELECT * FROM practice_coding_question WHERE review_status='APPROVED' AND difficulty<=?", difficulty)) {
            if (list(q.get("job_codes_json")).contains(code) && ("综合高频".equals(topic) || "项目场景".equals(topic) || "企业情境".equals(topic)
                    || "编程与算法".equals(topic) || list(q.get("topics_json")).contains(topic))) coding.add(q);
        }
        Collections.shuffle(coding);
        if (coding.size() < codingCount) throw new BizException("当前岗位、专项与难度的编程题不足，无法满足题量或 20%–30% 配额；请减少题量或选择综合练习。");
        String notice = "";
        if (companyName != null && !companyName.isBlank() && !"不限公司".equals(companyName) && knowledgeCount > 0) {
            notice = company.isEmpty() ? "暂无该企业已审核的专项资料，本次按岗位与专项组题。"
                    : "本次包含 " + actualCulture + " 道依据企业公开资料改编的情境题，不代表企业真实面试题。";
            if (!company.isEmpty() && actualCulture < targetCulture) notice += "相关企业情境题不足，已使用符合岗位与专项的知识题补齐。";
        }
        return new Selection(selected, coding.subList(0,codingCount), company, actualCulture, notice);
    }

    public Map<String,Object> company(String name) {
        if (name == null || name.isBlank() || "不限公司".equals(name)) return Map.of();
        for (var c : db.queryForList("SELECT * FROM practice_company WHERE review_status='APPROVED'")) {
            if (name.equals(c.get("name")) || list(c.get("aliases_json")).contains(name)) return c;
        }
        return Map.of();
    }

    private List<Map<String,Object>> objectiveRows(int difficulty) {
        try { return db.queryForList("SELECT * FROM practice_objective_question WHERE review_status='APPROVED' AND difficulty<=?",difficulty); }
        catch (BadSqlGrammarException e) { return List.of(); }
    }

    private String concept(Map<String,Object> q) { return String.valueOf(object(q.get("source_json")).getOrDefault("conceptId",q.get("canonical_id"))); }

    private boolean matches(Map<String,Object> q, String code, String topic) {
        if (!list(q.get("job_codes_json")).contains(code)) return false;
        return "综合高频".equals(topic) || "项目场景".equals(topic) || list(q.get("topics_json")).contains(topic);
    }

    private List<Map<String,Object>> legacy(Map<String,Object> job, String topic, int difficulty) {
        var abilities = new ArrayList<>(list(job.get("abilities"))); abilities.addAll(list(job.get("keywords")));
        var ids = new ArrayList<Long>();
        var aliases = Map.of("Java核心", "Java", "并发编程", "多线程", "浏览器原理", "浏览器", "工程化", "Webpack", "性能优化", "性能", "网络", "HTTP/HTTPS");
        String topicTag = aliases.getOrDefault(topic, topic);
        boolean broad = Set.of("综合高频", "项目场景").contains(topic);
        for (var tag : db.queryForList("SELECT id,name FROM skill_tag")) {
            String name = String.valueOf(tag.get("name"));
            boolean roleMatch = abilities.stream().anyMatch(a -> containsTag(a,name) || containsTag(name,a));
            if (roleMatch && (broad || containsTag(name,topicTag) || containsTag(topicTag,name))) ids.add(((Number)tag.get("id")).longValue());
        }
        if (ids.isEmpty()) return List.of();
        var args = new ArrayList<Object>(); args.add(difficulty); args.addAll(ids);
        return db.queryForList("SELECT DISTINCT q.* FROM skill_question q JOIN skill_question_tag_rel r ON r.question_id=q.id LEFT JOIN practice_question_meta m ON m.question_id=q.id WHERE m.question_id IS NULL AND q.difficulty<=? AND r.tag_id IN (" + String.join(",",Collections.nCopies(ids.size(),"?")) + ")", args.toArray());
    }

    static boolean containsTag(String haystack, String needle) {
        String h = haystack.toLowerCase(Locale.ROOT).trim(), n = needle.toLowerCase(Locale.ROOT).trim();
        if (n.isEmpty() || !h.contains(n)) return false;
        return n.chars().allMatch(c -> c < 128) ? Pattern.compile("(?<![a-z0-9])" + Pattern.quote(n) + "(?![a-z0-9])").matcher(h).find() : true;
    }
    public List<String> list(Object value) { try { return json.readValue(String.valueOf(value), new TypeReference<>() {}); } catch (Exception e) { return List.of(); } }
    public Map<String,Object> object(Object value) { try { return json.readValue(String.valueOf(value), new TypeReference<>() {}); } catch (Exception e) { return Map.of(); } }
    private Long number(Object value) { return value instanceof Number n ? n.longValue() : null; }
    private int count(String sql) { return Objects.requireNonNullElse(db.queryForObject(sql,Integer.class),0); }
}
