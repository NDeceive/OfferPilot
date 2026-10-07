package com.zhimian.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.BizException;
import com.zhimian.config.UserContext;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/** 专项训练：题目在会话创建时快照，所有作答和判题结果均归属当前用户。 */
@Service
@RequiredArgsConstructor
public class PracticeService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final Judge0Client judge;

    public record StartInput(@NotNull Long jobId, @Size(max = 100) String company, @NotBlank @Size(max = 100) String topic,
                             @NotBlank String trainingMode, @NotBlank String questionType, @Min(1) @Max(50) int questionCount,
                             String difficulty) {}
    public record AnswerInput(@NotBlank @Size(max = 10000) String answer) {}
    public record CodeInput(@NotBlank @Size(max = 50000) String sourceCode, @Size(max = 30) String language) {}

    @Transactional
    public Map<String, Object> create(StartInput input) {
        String type = normalizeType(input.questionType());
        int difficulty = normalizeDifficulty(input.difficulty());
        Map<String, Object> job = one("SELECT id,name,code,family FROM job_position WHERE id=? AND status=1", input.jobId());
        int actualCount = "CODING".equals(type) ? 1 : input.questionCount();
        long sessionId = insert("INSERT INTO practice_session(user_id,job_id,company,topic,training_mode,question_type,difficulty,requested_count) VALUES(?,?,?,?,?,?,?,?)",
                user(), input.jobId(), blank(input.company(), "不限公司"), input.topic().trim(), input.trainingMode().trim(), type, difficulty, actualCount);
        if ("CODING".equals(type)) insertCodeQuestion(sessionId, 1, difficulty);
        else insertKnowledgeQuestions(sessionId, actualCount, difficulty);
        return session(sessionId);
    }

    public Map<String, Object> overview() {
        int sessions = count("SELECT COUNT(*) FROM practice_session WHERE user_id=?", user());
        int favorites = count("SELECT COUNT(*) FROM practice_favorite WHERE user_id=?", user());
        int mistakes = count("SELECT COUNT(DISTINCT session_question_id) FROM practice_submission WHERE user_id=? AND is_correct=0", user());
        List<Map<String, Object>> recent = rows("SELECT id,job_id,topic,training_mode,question_type,requested_count,current_index,status,updated_at FROM practice_session WHERE user_id=? ORDER BY updated_at DESC LIMIT 5", user());
        for (Map<String, Object> s : recent) {
            List<Map<String, Object>> jobs = rows("SELECT id,name,code,family FROM job_position WHERE id=?", id(s, "jobId"));
            s.put("role", jobs.isEmpty() ? null : jobs.get(0));
            s.put("completed", count("SELECT COUNT(*) FROM practice_session_question WHERE session_id=? AND status='ANSWERED'", id(s, "id")));
            s.put("questionCount", s.get("requestedCount"));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("libraryCounts", Map.of("mistakes", mistakes, "favorites", favorites, "recent", sessions));
        out.put("recentSessions", recent);
        out.put("recentSession", recent.isEmpty() ? null : recent.get(0));
        return out;
    }

    public Map<String, Object> session(long sessionId) {
        Map<String, Object> s = ownSession(sessionId);
        Map<String, Object> job = one("SELECT id,name,code,family FROM job_position WHERE id=?", id(s, "jobId"));
        List<Map<String, Object>> questions = rows("SELECT q.*, EXISTS(SELECT 1 FROM practice_favorite f WHERE f.user_id=? AND f.session_question_id=q.id) favorite FROM practice_session_question q WHERE q.session_id=? ORDER BY q.sequence_no", user(), sessionId);
        for (Map<String, Object> q : questions) presentQuestion(q, sessionId);
        List<Map<String, Object>> submissions = rows("SELECT id,session_question_id,submission_type,status,is_correct,score,stdout,stderr,compile_output,time_seconds,memory_kb,created_at FROM practice_submission WHERE session_id=? AND user_id=? ORDER BY id DESC LIMIT 50", sessionId, user());
        s.put("role", job);
        s.put("questionList", questions);
        s.put("submissions", submissions);
        s.put("completed", count("SELECT COUNT(*) FROM practice_session_question WHERE session_id=? AND status='ANSWERED'", sessionId));
        return s;
    }

    @Transactional
    public Map<String, Object> answer(long sessionId, long questionId, AnswerInput input) {
        Map<String, Object> q = ownQuestion(sessionId, questionId);
        if (!"KNOWLEDGE".equals(q.get("questionType"))) throw new BizException("该题需要提交代码");
        String answer = input.answer().trim();
        List<String> keys = Arrays.stream(String.valueOf(q.getOrDefault("answerKeywords", "")).split("[、,，;；]"))
                .map(String::trim).filter(k -> k.length() >= 2).toList();
        long hits = keys.stream().filter(answer::contains).count();
        int score = keys.isEmpty() ? (answer.length() >= 80 ? 60 : 30) : Math.min(100, (int) Math.round(hits * 100.0 / keys.size()));
        boolean correct = score >= 60;
        long submissionId = insert("INSERT INTO practice_submission(session_id,session_question_id,user_id,submission_type,answer_text,status,is_correct,score) VALUES(?,?,?,?,?,?,?,?)",
                sessionId, questionId, user(), "KNOWLEDGE", answer, correct ? "REVIEWED" : "NEEDS_REVIEW", correct, score);
        db.update("UPDATE practice_session_question SET status='ANSWERED' WHERE id=?", questionId);
        advance(sessionId);
        return Map.of("submissionId", submissionId, "score", score, "passed", correct, "referenceAnswer", q.get("referenceAnswer"),
                "message", correct ? "已记录：关键词覆盖良好。" : "已记录：可补充更多关键点后再次提交。", "matchedKeywords", keys.stream().filter(answer::contains).toList());
    }

    @Transactional
    public Map<String, Object> run(long sessionId, long questionId, CodeInput input) {
        Map<String, Object> q = ownQuestion(sessionId, questionId);
        if (!"CODING".equals(q.get("questionType"))) throw new BizException("知识题请直接提交文字回答");
        String language = blank(input.language(), String.valueOf(q.get("language"))).toLowerCase();
        List<Map<String, String>> tests = decodeTests(String.valueOf(q.get("testsJson")));
        List<Map<String, Object>> cases = new ArrayList<>();
        boolean passed = true;
        Judge0Client.Result last = null;
        for (int i = 0; i < tests.size(); i++) {
            Map<String, String> test = tests.get(i);
            Judge0Client.Result result = judge.judge(language, input.sourceCode(), test.get("input"));
            last = result;
            boolean ok = result.statusId() == 3 && normalizeOutput(result.stdout()).equals(normalizeOutput(test.get("expected")));
            passed &= ok;
            cases.add(Map.of("name", "用例 " + (i + 1), "input", test.get("input"), "expected", test.get("expected"), "output", result.stdout(), "status", ok ? "通过" : result.status()));
            if (!ok) break;
        }
        String status = passed ? "ACCEPTED" : (last == null ? "FAILED" : blank(last.status(), "FAILED"));
        long submissionId = insert("INSERT INTO practice_submission(session_id,session_question_id,user_id,submission_type,source_code,language,status,is_correct,judge_token,stdout,stderr,compile_output,time_seconds,memory_kb) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                sessionId, questionId, user(), "CODE", input.sourceCode(), language, status, passed, last == null ? null : last.token(), last == null ? null : last.stdout(), last == null ? null : last.stderr(), last == null ? null : last.compileOutput(), last == null ? null : last.time(), last == null ? null : last.memory());
        if (passed) { db.update("UPDATE practice_session_question SET status='ANSWERED' WHERE id=?", questionId); advance(sessionId); }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("submissionId", submissionId); out.put("passed", passed); out.put("status", status); out.put("cases", cases);
        out.put("message", passed ? "真实 Judge0 用例全部通过。" : "真实 Judge0 已返回结果，请根据输出继续修改。");
        return out;
    }

    @Transactional
    public Map<String, Object> finish(long sessionId) {
        ownSession(sessionId);
        db.update("UPDATE practice_session SET status='FINISHED',finished_at=COALESCE(finished_at,NOW()) WHERE id=?", sessionId);
        return session(sessionId);
    }

    @Transactional
    public Map<String, Object> toggleFavorite(long questionId) {
        Map<String, Object> q = one("SELECT q.id FROM practice_session_question q JOIN practice_session s ON s.id=q.session_id WHERE q.id=? AND s.user_id=?", questionId, user());
        int deleted = db.update("DELETE FROM practice_favorite WHERE user_id=? AND session_question_id=?", user(), id(q, "id"));
        boolean favorite = deleted == 0;
        if (favorite) insert("INSERT INTO practice_favorite(user_id,session_question_id) VALUES(?,?)", user(), id(q, "id"));
        return Map.of("favorite", favorite);
    }

    private void insertKnowledgeQuestions(long sessionId, int requested, int difficulty) {
        List<Map<String, Object>> bank = rows("SELECT id,content,reference_answer,answer_keywords,difficulty FROM skill_question WHERE difficulty<=? ORDER BY RAND() LIMIT ?", difficulty, requested);
        if (bank.isEmpty()) throw new BizException("知识题库尚未初始化，请先执行 seed_skill_bank.sql");
        for (int i = 0; i < bank.size(); i++) {
            Map<String, Object> q = bank.get(i);
            insert("INSERT INTO practice_session_question(session_id,source_question_id,sequence_no,question_type,title,content,reference_answer,answer_keywords,difficulty) VALUES(?,?,?,?,?,?,?,?,?)",
                    sessionId, id(q, "id"), i + 1, "KNOWLEDGE", "知识题 " + (i + 1), q.get("content"), q.get("referenceAnswer"), q.get("answerKeywords"), q.get("difficulty"));
        }
    }

    private void insertCodeQuestion(long sessionId, int sequence, int difficulty) {
        String starter = "import java.io.*;\nimport java.util.*;\n\npublic class Main {\n    public static void main(String[] args) throws Exception {\n        // 读取一行空格分隔的整数，并输出它们的和\n    }\n}";
        String tests = encode(List.of(Map.of("input", "1 2 3", "expected", "6"), Map.of("input", "10 -4 7", "expected", "13")));
        insert("INSERT INTO practice_session_question(session_id,sequence_no,question_type,title,content,difficulty,language,starter_code,tests_json) VALUES(?,?,?,?,?,?,?,?,?)",
                sessionId, sequence, "CODING", "计算一行整数之和", "从标准输入读取一行以空格分隔的整数，输出它们的总和。请使用 Java 17，并保证输出仅包含结果。", difficulty, "java", starter, tests);
    }

    private void presentQuestion(Map<String, Object> q, long sessionId) {
        q.put("type", String.valueOf(q.get("questionType")).toLowerCase());
        q.put("description", q.get("content"));
        q.put("starter", q.get("starterCode"));
        q.put("signature", "CODING".equals(q.get("questionType")) ? "public class Main" : "请用自己的语言作答");
        q.put("constraints", "CODING".equals(q.get("questionType")) ? List.of("从标准输入读取", "输出只包含答案", "最多 3 秒") : List.of("回答请包含关键概念与因果关系", "提交后可查看参考答案"));
        q.put("examples", "CODING".equals(q.get("questionType")) ? decodeTests(String.valueOf(q.get("testsJson"))) : List.of());
        q.put("followUps", List.of(Map.of("text", "请补充说明你的设计取舍与边界条件。", "lineStart", 1, "lineEnd", 1)));
        q.put("latestSubmission", rows("SELECT id,status,is_correct,score,created_at FROM practice_submission WHERE session_question_id=? AND user_id=? ORDER BY id DESC LIMIT 1", id(q, "id"), user()).stream().findFirst().orElse(null));
        q.remove("referenceAnswer"); q.remove("answerKeywords"); q.remove("testsJson");
    }

    private Map<String, Object> ownSession(long sessionId) { return one("SELECT * FROM practice_session WHERE id=? AND user_id=?", sessionId, user()); }
    private Map<String, Object> ownQuestion(long sessionId, long questionId) { ownSession(sessionId); return one("SELECT * FROM practice_session_question WHERE id=? AND session_id=?", questionId, sessionId); }
    private void advance(long sessionId) { db.update("UPDATE practice_session SET current_index=LEAST(requested_count, current_index+1) WHERE id=?", sessionId); }
    private int count(String sql, Object... args) { Integer value = db.queryForObject(sql, Integer.class, args); return value == null ? 0 : value; }
    private long user() { Long id = UserContext.getUserId(); if (id == null) throw new BizException("请先登录"); return id; }
    private String normalizeType(String raw) { String type = raw.trim().toUpperCase(); return switch (type) { case "CODING" -> "CODING"; case "KNOWLEDGE", "ALL" -> "KNOWLEDGE"; default -> throw new BizException("不支持的训练题型"); }; }
    /** 前端传字符串难度等级（all/basic/medium/advanced），映射为题库的 TINYINT 1-3。 */
    private int normalizeDifficulty(String raw) { String d = raw == null ? "all" : raw.trim().toLowerCase(); return switch (d) { case "basic", "easy", "1" -> 1; case "medium", "2" -> 2; case "advanced", "hard", "3", "all" -> 3; default -> throw new BizException("不支持的难度等级：" + raw); }; }
    private static String blank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private static String normalizeOutput(String value) { return value == null ? "" : value.trim().replaceAll("\\s+", " "); }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new BizException("题目数据格式无效"); } }
    private List<Map<String, String>> decodeTests(String value) { try { return json.readValue(value, new TypeReference<>() {}); } catch (Exception e) { throw new BizException("编程题测试数据损坏"); } }
    private List<Map<String, Object>> rows(String sql, Object... args) { return db.queryForList(sql, args).stream().map(this::camel).toList(); }
    private Map<String, Object> one(String sql, Object... args) { List<Map<String, Object>> values = rows(sql, args); if (values.isEmpty()) throw new BizException("记录不存在或无访问权限"); return values.get(0); }
    private long insert(String sql, Object... args) { GeneratedKeyHolder keys = new GeneratedKeyHolder(); db.update(connection -> { var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS); for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]); return statement; }, keys); return Objects.requireNonNull(keys.getKey()).longValue(); }
    private Map<String, Object> camel(Map<String, Object> source) { Map<String, Object> out = new LinkedHashMap<>(); source.forEach((key, value) -> { StringBuilder name = new StringBuilder(); boolean upper = false; for (char c : key.toCharArray()) { if (c == '_') upper = true; else { name.append(upper ? Character.toUpperCase(c) : c); upper = false; } } out.put(name.toString(), value instanceof Timestamp t ? t.toLocalDateTime() : value); }); return out; }
    private static long id(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
}
