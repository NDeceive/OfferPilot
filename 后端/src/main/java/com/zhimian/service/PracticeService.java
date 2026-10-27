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
import java.util.concurrent.ThreadLocalRandom;

/** 专项训练：题目在会话创建时快照，所有作答和判题结果均归属当前用户。 */
@Service
@RequiredArgsConstructor
public class PracticeService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final Judge0Client judge;
    private final PracticeBankService bank;

    public record StartInput(@NotNull Long jobId, @Size(max = 100) String company, @NotBlank @Size(max = 100) String topic,
                             @NotBlank String trainingMode, @NotBlank String questionType, @Min(1) @Max(50) int questionCount,
                             String difficulty) {}
    public record AnswerInput(@NotBlank @Size(max = 10000) String answer) {}
    public record CodeInput(@NotBlank @Size(max = 50000) String sourceCode, @Size(max = 30) String language) {}

    @Transactional
    public Map<String, Object> create(StartInput input) {
        String type = normalizeType(input.questionType());
        int difficulty = normalizeDifficulty(input.difficulty());
        Map<String, Object> job = one("SELECT id,name,code,family,abilities,keywords FROM job_position WHERE id=? AND status=1", input.jobId());
        int actualCount = input.questionCount();
        int codingCount = "CODING".equals(type) ? actualCount : "ALL".equals(type) ? mixedCodingCount(actualCount) : 0;
        var selected = "KNOWLEDGE".equals(type) || "CODING".equals(type)
                ? bank.select(job, blank(input.company(), "不限公司"), input.topic().trim(), difficulty, actualCount-codingCount, codingCount, actualCount)
                : bank.select(job, blank(input.company(), "不限公司"), input.topic().trim(), difficulty, actualCount-codingCount, codingCount, actualCount,type);
        long sessionId = insert("INSERT INTO practice_session(user_id,job_id,company,topic,training_mode,question_type,difficulty,requested_count) VALUES(?,?,?,?,?,?,?,?)",
                user(), input.jobId(), blank(input.company(), "不限公司"), input.topic().trim(), "normal", type, difficulty, actualCount);
        var positions = new HashSet<Integer>();
        for (int i=0;i<codingCount;i++) positions.add((int)((i+.5)*actualCount/codingCount)+1);
        int knowledgeIndex=0, codeIndex=0;
        for (int sequence=1;sequence<=actualCount;sequence++) {
            boolean coding = positions.contains(sequence);
            snapshotQuestion(sessionId, sequence, coding ? selected.coding().get(codeIndex++) : selected.knowledge().get(knowledgeIndex++), coding);
        }
        Map<String,Object> matching = new LinkedHashMap<>();
        matching.put("notice",selected.notice()); matching.put("cultureCount",selected.cultureCount()); matching.put("codingCount",codingCount);
        matching.put("companyId",selected.company().get("id")); matching.put("companyName",selected.company().get("name"));
        db.update("INSERT INTO practice_session_detail(session_id,detail_json) VALUES(?,?)",sessionId,encode(matching));
        db.update("UPDATE practice_session SET requested_count=(SELECT COUNT(*) FROM practice_session_question WHERE session_id=?) WHERE id=?", sessionId, sessionId);
        return session(sessionId);
    }

    public Map<String, Object> overview() {
        int sessions = count("SELECT COUNT(*) FROM practice_session WHERE user_id=?", user());
        int favorites = count("SELECT COUNT(*) FROM practice_favorite WHERE user_id=?", user());
        int mistakes = count("SELECT COUNT(*) " + personalQuestionsFrom("mistakes"), user());
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
        var ongoing = rows("SELECT id FROM practice_session WHERE user_id=? AND status='ONGOING' ORDER BY updated_at DESC,id DESC LIMIT 1", user());
        out.put("recentSession", ongoing.isEmpty() ? null : sessionSummary(id(ongoing.get(0), "id")));
        return out;
    }

    private Map<String,Object> sessionSummary(long sessionId) {
        var s = ownSession(sessionId);
        var jobs = rows("SELECT id,name,code FROM job_position WHERE id=?", s.get("jobId"));
        s.put("role", jobs.isEmpty() ? null : jobs.get(0));
        s.put("questionCount", s.get("requestedCount"));
        s.put("completed", count("SELECT COUNT(*) FROM practice_session_question WHERE session_id=? AND status='ANSWERED'", sessionId));
        return s;
    }

    public Map<String,Object> sessions(String status, int page, int size) {
        if (!List.of("ALL", "ONGOING", "FINISHED").contains(status)) throw new BizException("不支持的练习状态");
        checkPage(page, size);
        String where = " FROM practice_session s WHERE s.user_id=?" + ("ALL".equals(status) ? "" : " AND s.status=?");
        Object[] args = "ALL".equals(status) ? new Object[]{user()} : new Object[]{user(), status};
        var queryArgs = new ArrayList<>(Arrays.asList(args)); queryArgs.add(size); queryArgs.add((page-1)*size);
        var items = rows("SELECT s.id" + where + " ORDER BY s.updated_at DESC,s.id DESC LIMIT ? OFFSET ?", queryArgs.toArray());
        return Map.of("items", items.stream().map(s -> sessionSummary(id(s,"id"))).toList(), "total", count("SELECT COUNT(*)"+where,args), "page",page,"size",size);
    }

    // 最新正式提交决定待复习状态；普通自评题和仅运行代码不被自动判错。
    private String personalQuestionsFrom(String kind) {
        String base = "FROM practice_session_question q JOIN practice_session s ON s.id=q.session_id LEFT JOIN job_position j ON j.id=s.job_id LEFT JOIN practice_review r ON r.session_question_id=q.id AND r.user_id=s.user_id LEFT JOIN practice_submission a ON a.id=(SELECT MAX(a2.id) FROM practice_submission a2 WHERE a2.session_question_id=q.id AND a2.user_id=s.user_id) ";
        if ("favorites".equals(kind)) return base + "JOIN practice_favorite f ON f.session_question_id=q.id AND f.user_id=s.user_id WHERE s.user_id=?";
        if ("mistakes".equals(kind)) return base + "WHERE s.user_id=? AND (r.state='PENDING' OR (a.is_correct=0 AND COALESCE(r.state,'PENDING')<>'RESOLVED'))";
        throw new BizException("不支持的个人题库类型");
    }

    public Map<String,Object> personalQuestions(String kind, int page, int size) {
        checkPage(page,size);
        String from = personalQuestionsFrom(kind);
        String order = "favorites".equals(kind) ? "f.created_at DESC,q.id DESC" : "a.created_at DESC,q.id DESC";
        var items = rows("SELECT q.id,q.session_id,q.sequence_no,q.title,q.question_type,s.topic,s.company,j.name AS job_name,a.status AS submission_status,a.created_at AS submitted_at,r.state AS review_state " + from + " ORDER BY " + order + " LIMIT ? OFFSET ?", user(),size,(page-1)*size);
        return Map.of("items",items,"total",count("SELECT COUNT(*) "+from,user()),"page",page,"size",size);
    }

    private void checkPage(int page,int size) { if(page<1 || page>100000 || size<1 || size>50) throw new BizException("分页参数无效"); }

    public Map<String, Object> session(long sessionId) {
        Map<String, Object> s = ownSession(sessionId);
        Map<String, Object> job = one("SELECT id,name,code,family FROM job_position WHERE id=?", id(s, "jobId"));
        List<Map<String, Object>> questions = rows("SELECT q.*, EXISTS(SELECT 1 FROM practice_favorite f WHERE f.user_id=? AND f.session_question_id=q.id) favorite FROM practice_session_question q WHERE q.session_id=? ORDER BY q.sequence_no", user(), sessionId);
        for (Map<String, Object> q : questions) presentQuestion(q, sessionId);
        List<Map<String, Object>> submissions = rows("SELECT id,session_question_id,submission_type,status,is_correct,score,answer_text,source_code,language,stdout,stderr,compile_output,time_seconds,memory_kb,created_at FROM practice_submission WHERE session_id=? AND user_id=? ORDER BY id DESC LIMIT 50", sessionId, user());
        s.put("role", job);
        s.put("questionList", questions);
        s.put("submissions", submissions);
        s.put("completed", count("SELECT COUNT(*) FROM practice_session_question WHERE session_id=? AND status='ANSWERED'", sessionId));
        var details = rows("SELECT detail_json FROM practice_session_detail WHERE session_id=?",sessionId);
        s.put("matching", details.isEmpty() ? Map.of() : bank.object(details.get(0).get("detailJson")));
        return s;
    }

    @Transactional
    public Map<String, Object> answer(long sessionId, long questionId, AnswerInput input) {
        var writable = one("SELECT * FROM practice_session WHERE id=? AND user_id=? FOR UPDATE",sessionId,user());
        if (!"ONGOING".equals(writable.getOrDefault("status","ONGOING"))) throw new BizException("练习已结束或正在结算，请新建练习作答");
        Map<String, Object> q = ownQuestion(sessionId, questionId);
        if ("CODING".equals(q.get("questionType"))) throw new BizException("该题需要提交代码");
        String answer = input.answer().trim();
        var detail = questionDetail(questionId);
        if ("OBJECTIVE".equals(detail.get("gradingMode"))) {
            String expected = String.valueOf(detail.getOrDefault("correctAnswer",""));
            if (expected.isBlank()) throw new BizException("客观题标准答案尚未配置");
            if (Set.of("CHOICE","MULTIPLE_CHOICE","TRUE_FALSE").contains(String.valueOf(q.get("questionType")))) {
                answer = objectiveAnswer(String.valueOf(q.get("questionType")),answer,detail);
            }
            boolean correct = answer.equals(expected.trim());
            long submissionId = insert("INSERT INTO practice_submission(session_id,session_question_id,user_id,submission_type,answer_text,status,is_correct) VALUES(?,?,?,?,?,?,?)",sessionId,questionId,user(),"KNOWLEDGE",answer,correct?"OBJECTIVE_CORRECT":"OBJECTIVE_INCORRECT",correct);
            db.update("UPDATE practice_session_question SET status='ANSWERED' WHERE id=?",questionId); updateProgress(sessionId);
            var result = Map.<String,Object>of("submissionId",submissionId,"submitted",true,"status",correct?"OBJECTIVE_CORRECT":"OBJECTIVE_INCORRECT","passed",correct,"correctAnswer",expected,"referenceAnswer",Objects.requireNonNullElse(q.get("referenceAnswer"),""));
            db.update("INSERT INTO practice_submission_detail(submission_id,result_json) VALUES(?,?)",submissionId,encode(result));
            if (correct) db.update("UPDATE practice_review SET state='RESOLVED' WHERE user_id=? AND session_question_id=?",user(),questionId);
            else db.update("INSERT INTO practice_review(user_id,session_question_id,state) VALUES(?,?,'PENDING') ON DUPLICATE KEY UPDATE state='PENDING'",user(),questionId);
            if (correct && detail.get("originalQuestionId") instanceof Number origin) db.update("UPDATE practice_review SET state='RESOLVED' WHERE user_id=? AND session_question_id=?",user(),origin.longValue());
            return result;
        }
        if ("SELF_REVIEW".equals(detail.get("gradingMode"))) {
            long submissionId = insert("INSERT INTO practice_submission(session_id,session_question_id,user_id,submission_type,answer_text,status,is_correct,score) VALUES(?,?,?,?,?,?,?,?)",
                    sessionId,questionId,user(),"KNOWLEDGE",answer,"SELF_REVIEW",null,null);
            db.update("UPDATE practice_session_question SET status='ANSWERED' WHERE id=?",questionId);
            updateProgress(sessionId);
            return Map.of("submissionId",submissionId,"submitted",true,"status","SELF_REVIEW","referenceAnswer",Objects.requireNonNullElse(q.get("referenceAnswer"),""),
                    "message","回答已提交，请结合左侧参考分析检查论证与取舍；本题不提供自动分数或通过结论。");
        }
        List<String> keys = Arrays.stream(String.valueOf(q.getOrDefault("answerKeywords", "")).split("[、,，;；]"))
                .map(String::trim).filter(k -> k.length() >= 2).toList();
        long hits = keys.stream().filter(answer::contains).count();
        int score = keys.isEmpty() ? (answer.length() >= 80 ? 60 : 30) : Math.min(100, (int) Math.round(hits * 100.0 / keys.size()));
        boolean correct = score >= 60;
        long submissionId = insert("INSERT INTO practice_submission(session_id,session_question_id,user_id,submission_type,answer_text,status,is_correct,score) VALUES(?,?,?,?,?,?,?,?)",
                sessionId, questionId, user(), "KNOWLEDGE", answer, correct ? "REVIEWED" : "NEEDS_REVIEW", correct, score);
        db.update("UPDATE practice_session_question SET status='ANSWERED' WHERE id=?", questionId);
        updateProgress(sessionId);
        return Map.of("submissionId", submissionId, "score", score, "passed", correct, "referenceAnswer", q.get("referenceAnswer"),
                "message", correct ? "已记录：关键词覆盖良好。" : "已记录：可补充更多关键点后再次提交。", "matchedKeywords", keys.stream().filter(answer::contains).toList());
    }

    public Map<String, Object> run(long sessionId, long questionId, CodeInput input) {
        return evaluateCode(sessionId, questionId, input, false);
    }

    @Transactional
    public Map<String, Object> submitCode(long sessionId, long questionId, CodeInput input) {
        var writable = one("SELECT * FROM practice_session WHERE id=? AND user_id=? FOR UPDATE",sessionId,user());
        if(!"ONGOING".equals(writable.getOrDefault("status","ONGOING"))) throw new BizException("练习已结束或正在结算");
        return evaluateCode(sessionId, questionId, input, true);
    }

    private void ensureJudgeReady() {
        var capability=judge.capability();
        if(capability==null || !Boolean.TRUE.equals(capability.get("ready"))) throw new BizException("判题服务尚未通过运行环境验证，请稍后重试");
    }

    private Map<String, Object> evaluateCode(long sessionId, long questionId, CodeInput input, boolean submit) {
        return evaluateCode(sessionId,questionId,input,submit,true);
    }
    Map<String,Object> evaluateTask(long sessionId,long questionId,CodeInput input,boolean submit) { return evaluateCode(sessionId,questionId,input,submit,false); }
    private Map<String,Object> evaluateCode(long sessionId,long questionId,CodeInput input,boolean submit,boolean persist) {
        var sessionStatus=ownSession(sessionId).get("status");
        if ("FINISHED".equals(sessionStatus) || persist && "CLOSING".equals(sessionStatus)) throw new BizException("练习已结束，请新建练习作答");
        Map<String, Object> q = ownQuestion(sessionId, questionId);
        if (!"CODING".equals(q.get("questionType"))) throw new BizException("知识题请直接提交文字回答");
        String language = blank(input.language(), String.valueOf(q.get("language"))).toLowerCase();
        if (!language.equals(String.valueOf(q.get("language")))) throw new BizException("请使用题目指定的编程语言");
        if(persist) ensureJudgeReady();
        var detail = questionDetail(questionId);
        List<Map<String, String>> tests = !submit ? detail.containsKey("examples") ? decodeTests(encode(detail.get("examples"))) : decodeTests(String.valueOf(q.get("testsJson"))).stream().filter(t -> "true".equals(t.get("public"))).toList() : decodeTests(String.valueOf(q.get("testsJson")));
        if (tests.isEmpty()) throw new BizException("该题尚未配置测试用例");
        List<Map<String, Object>> cases = new ArrayList<>();
        boolean passed = true;
        Judge0Client.Result last = null;
        for (int i = 0; i < tests.size(); i++) {
            Map<String, String> test = tests.get(i);
            Judge0Client.Result result = judge.judge(language, input.sourceCode(), test.get("input"));
            if (result.statusId() <= 2 || result.statusId() >= 12) throw new BizException("判题任务未完成或执行器异常；本次不计入有效提交");
            last = result;
            boolean ok = result.statusId() == 3 && PracticeJudging.matches(result.stdout(),test.get("expected"),String.valueOf(detail.getOrDefault("comparisonMode","LINE_ENDING")));
            passed &= ok;
            boolean hidden = submit && !"true".equals(test.get("public"));
            cases.add(Map.of("name", "用例 " + (i + 1), "hidden", hidden, "input", hidden ? "判题用例不公开" : PracticeJudging.log(test.get("input")), "expected", hidden ? "判题用例不公开" : PracticeJudging.log(test.get("expected")), "output", hidden ? "" : PracticeJudging.log(result.stdout()), "status", ok ? "通过" : (result.statusId() == 3 ? "输出不匹配" : result.status())));
            if (!ok) break;
        }
        String status = passed ? "ACCEPTED" : (last == null ? "SYSTEM_ERROR" : last.statusId() == 3 ? "WRONG_ANSWER" : PracticeJudging.status(last.statusId()));
        Long submissionId = submit && persist ? insert("INSERT INTO practice_submission(session_id,session_question_id,user_id,submission_type,source_code,language,status,is_correct,judge_token,stdout,stderr,compile_output,time_seconds,memory_kb) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                sessionId, questionId, user(), "CODE", input.sourceCode(), language, status, passed, last == null ? null : last.token(), "", "", last == null ? null : PracticeJudging.log(last.compileOutput()), last == null ? null : last.time(), last == null ? null : last.memory()) : null;
        if (submit && persist) { db.update("UPDATE practice_session_question SET status='ANSWERED' WHERE id=?", questionId); updateProgress(sessionId); }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("submissionId", submissionId); out.put("submitted", submit);
        boolean hiddenResult = submit && !"true".equals(tests.get(cases.size()-1).get("public"));
        out.put("stderr", hiddenResult ? "" : PracticeJudging.log(last == null ? "" : last.stderr())); out.put("compileOutput", PracticeJudging.log(last == null ? "" : last.compileOutput())); out.put("passed", passed); out.put("status", status); out.put("cases", cases);
        out.put("stdout", hiddenResult ? "" : PracticeJudging.log(last == null ? "" : last.stdout()));
        out.put("time", last == null ? "" : last.time()); out.put("memory",last == null ? null : last.memory());
        out.put("executedCount",cases.size());out.put("totalCount",tests.size());
        out.put("questionVersion",detail.getOrDefault("questionVersion","snapshot-"+questionId));out.put("ruleVersion",detail.getOrDefault("ruleVersion","line-ending-v1"));
        if (submissionId != null) db.update("INSERT INTO practice_submission_detail(submission_id,result_json) VALUES(?,?)",submissionId,encode(out));
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

    public Map<String,Object> removeFavorite(long questionId) {
        one("SELECT q.id FROM practice_session_question q JOIN practice_session s ON s.id=q.session_id WHERE q.id=? AND s.user_id=?", questionId,user());
        db.update("DELETE FROM practice_favorite WHERE user_id=? AND session_question_id=?",user(),questionId);
        return Map.of("favorite",false);
    }

    static int mixedCodingCount(int total) {
        if (total < 2) throw new BizException("混合题单至少需要 2 道题");
        if (total < 7) return 1; // 短题单优先保证两种题型，整数题量无法始终满足百分比区间。
        int min = Math.max(2, (int) Math.ceil(total * 0.20));
        int max = Math.max(min, (int) Math.floor(total * 0.30));
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private void presentQuestion(Map<String, Object> q, long sessionId) {
        var detail = questionDetail(id(q,"id"));
        q.putAll(detail);
        var review=rows("SELECT state FROM practice_review WHERE user_id=? AND session_question_id=?",user(),id(q,"id"));
        q.put("reviewState",review.isEmpty()?"":review.get(0).get("state"));
        q.put("type", String.valueOf(q.get("questionType")).toLowerCase());
        q.put("description", q.get("content"));
        q.put("starter", q.get("starterCode"));
        q.put("signature", "CODING".equals(q.get("questionType")) ? "public class Main" : "请用自己的语言作答");
        q.put("constraints", "CODING".equals(q.get("questionType")) ? List.of("从标准输入读取", "输出只包含答案", "最多 3 秒") : "OBJECTIVE".equals(detail.get("gradingMode")) ? List.of("MULTIPLE_CHOICE".equals(q.get("questionType")) ? "选择全部正确选项；少选、多选均判错" : "请选择一个答案", "提交后自动判分并展示解析") : List.of("回答请包含关键概念与因果关系", "提交后可查看参考答案"));
        q.put("examples", "CODING".equals(q.get("questionType")) ? detail.getOrDefault("examples", decodeTests(String.valueOf(q.get("testsJson"))).stream().filter(t -> "true".equals(t.get("public"))).toList()) : List.of());
        q.put("followUps", List.of(Map.of("text", "请补充说明你的设计取舍与边界条件。", "lineStart", 1, "lineEnd", 1)));
        q.put("latestSubmission", rows("SELECT id,status,is_correct,score,answer_text,source_code,language,stdout,stderr,compile_output,created_at FROM practice_submission WHERE session_question_id=? AND user_id=? ORDER BY id DESC LIMIT 1", id(q, "id"), user()).stream().findFirst().orElse(null));
        if(q.get("latestSubmission") instanceof Map latest) {
            var saved = rows("SELECT result_json FROM practice_submission_detail WHERE submission_id=?",latest.get("id"));
            if(!saved.isEmpty()) latest.put("result",bank.object(saved.get(0).get("resultJson")));
            else if("SELF_REVIEW".equals(latest.get("status"))) latest.put("result",Map.of("submitted",true,"status","SELF_REVIEW","message","回答已记录，请结合参考分析自评；不提供自动分数或通过结论。"));
        }
        if (q.get("latestSubmission") == null) q.remove("referenceAnswer");
        q.remove("answerKeywords"); q.remove("testsJson");q.remove("correctAnswer");
    }

    private Map<String,Object> questionDetail(long questionId) {
        var values = rows("SELECT detail_json FROM practice_question_detail WHERE session_question_id=?",questionId);
        return values.isEmpty() ? Map.of() : bank.object(values.get(0).get("detailJson"));
    }

    static String objectiveAnswer(String type,String answer,Map<String,Object> detail) {
        var options = (List<Map<String,Object>>)detail.getOrDefault("options",List.of());
        var allowed = new HashSet<String>(); for (var option:options) allowed.add(String.valueOf(option.get("key")));
        var values = Arrays.stream(answer.split(",",-1)).map(String::trim).toList();
        if(values.isEmpty() || values.stream().anyMatch(v -> !allowed.contains(v)) || new HashSet<>(values).size()!=values.size()
                || !"MULTIPLE_CHOICE".equals(type) && values.size()!=1) throw new BizException("请选择题目提供的有效选项");
        return String.join(",",new TreeSet<>(values));
    }

    private void snapshotQuestion(long sessionId,int sequence,Map<String,Object> q,boolean coding) {
        long questionId = insert("INSERT INTO practice_session_question(session_id,source_question_id,sequence_no,question_type,title,content,reference_answer,answer_keywords,difficulty,language,starter_code,tests_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                sessionId,q.get("id"),sequence,coding ? "CODING" : q.containsKey("options_json") ? q.get("subtype") : "KNOWLEDGE",q.getOrDefault("title","知识题 "+sequence),q.get("content"),q.get("reference_answer"),q.get("answer_keywords"),q.get("difficulty"),q.get("language"),q.get("starter_code"),q.get("tests_json"));
        Map<String,Object> detail = new LinkedHashMap<>();
        detail.put("canonicalId",q.get("canonical_id")); detail.put("subtype",q.getOrDefault("subtype","KNOWLEDGE"));
        detail.put("source",bank.object(q.get("source_json"))); detail.put("tags",bank.list(q.get("tags_json")));
        if (!coding && q.containsKey("canonical_id")) detail.put("gradingMode","SELF_REVIEW");
        if (q.containsKey("options_json")) {
            detail.put("gradingMode","OBJECTIVE"); detail.put("correctAnswer",q.get("correct_answer"));
            try { detail.put("options",json.readValue(String.valueOf(q.get("options_json")),List.class)); }
            catch (Exception e) { throw new BizException("客观题选项数据损坏"); }
        }
        if (coding) detail.put("examples",decodeTests(String.valueOf(q.get("examples_json"))));
        detail.put("questionVersion",String.valueOf(q.getOrDefault("canonical_id","legacy"))+":snapshot-"+questionId);detail.put("ruleVersion","line-ending-v1");detail.put("comparisonMode","LINE_ENDING");
        if(coding) detail.put("judgeConfig",Map.of("inputProtocol","STDIN_STDOUT","language","java","requiredJavaVersion","17","cpuSeconds",3,"wallSeconds",6,"memoryKb",128000,"outputKb",64,"network",false,"processes",40));
        db.update("INSERT INTO practice_question_detail(session_question_id,detail_json) VALUES(?,?)",questionId,encode(detail));
    }

    private Map<String, Object> ownSession(long sessionId) { return one("SELECT * FROM practice_session WHERE id=? AND user_id=?", sessionId, user()); }
    private Map<String, Object> ownQuestion(long sessionId, long questionId) { ownSession(sessionId); return one("SELECT * FROM practice_session_question WHERE id=? AND session_id=?", questionId, sessionId); }
    private void updateProgress(long sessionId) { db.update("UPDATE practice_session SET current_index=COALESCE((SELECT MIN(sequence_no)-1 FROM practice_session_question WHERE session_id=? AND status<>'ANSWERED'),requested_count),updated_at=NOW() WHERE id=?", sessionId, sessionId); }
    private int count(String sql, Object... args) { Integer value = db.queryForObject(sql, Integer.class, args); return value == null ? 0 : value; }
    private long user() { Long id = UserContext.getUserId(); if (id == null) throw new BizException("请先登录"); return id; }
    private String normalizeType(String raw) { String type = raw.trim().toUpperCase(Locale.ROOT); if (!Set.of("CODING","KNOWLEDGE","ALL","CHOICE","MULTIPLE_CHOICE","TRUE_FALSE").contains(type)) throw new BizException("不支持的训练题型"); return type; }
    /** 前端传字符串难度等级（all/basic/medium/advanced），映射为题库的 TINYINT 1-3。 */
    private int normalizeDifficulty(String raw) { String d = raw == null ? "all" : raw.trim().toLowerCase(); return switch (d) { case "basic", "easy", "1" -> 1; case "medium", "2" -> 2; case "advanced", "hard", "3", "all" -> 3; default -> throw new BizException("不支持的难度等级：" + raw); }; }
    private static String blank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new BizException("题目数据格式无效"); } }
    private List<Map<String, String>> decodeTests(String value) { try { return json.readValue(value, new TypeReference<>() {}); } catch (Exception e) { throw new BizException("编程题测试数据损坏"); } }
    private List<Map<String, Object>> rows(String sql, Object... args) { return db.queryForList(sql, args).stream().map(this::camel).toList(); }
    private Map<String, Object> one(String sql, Object... args) { List<Map<String, Object>> values = rows(sql, args); if (values.isEmpty()) throw new BizException("记录不存在或无访问权限"); return values.get(0); }
    private long insert(String sql, Object... args) { GeneratedKeyHolder keys = new GeneratedKeyHolder(); db.update(connection -> { var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS); for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]); return statement; }, keys); return Objects.requireNonNull(keys.getKey()).longValue(); }
    private Map<String, Object> camel(Map<String, Object> source) { Map<String, Object> out = new LinkedHashMap<>(); source.forEach((key, value) -> { StringBuilder name = new StringBuilder(); boolean upper = false; for (char c : key.toCharArray()) { if (c == '_') upper = true; else { name.append(upper ? Character.toUpperCase(c) : c); upper = false; } } out.put(name.toString(), value instanceof Timestamp t ? t.toLocalDateTime() : value); }); return out; }
    private static long id(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
}
