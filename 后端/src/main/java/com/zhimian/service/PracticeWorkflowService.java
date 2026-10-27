package com.zhimian.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.BizException;
import com.zhimian.config.UserContext;
import jakarta.validation.constraints.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.BadSqlGrammarException;
import java.sql.Statement;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class PracticeWorkflowService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final PracticeService practice;
    private final Judge0Client judge;
    private final TransactionTemplate transaction;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r -> { var thread=new Thread(r,"practice-judge-worker");thread.setDaemon(true);return thread; });
    @PostConstruct void startWorker() { worker.scheduleWithFixedDelay(() -> { try { work(); } catch(Exception e) { log.warn("练习判题调度暂不可用，将在下一轮重试"); } },1,1,TimeUnit.SECONDS); }
    @PreDestroy void stopWorker() { worker.shutdownNow(); }
    public record TaskInput(@NotBlank @Size(max=80) String requestKey,@NotBlank String operation,@NotBlank @Size(max=50000) String sourceCode,@NotBlank String language) {}
    public record ReviewInput(@NotBlank String state) {}

    public Map<String,Object> capability() { return judge.capability(); }
    public Map<String,Object> createTask(long sessionId,long questionId,TaskInput input) {
        long owner=user();
        if(!Set.of("run","submit").contains(input.operation())) throw new BizException("不支持的判题操作");
        return transaction.execute(tx -> {
            var s=ownedSession(sessionId,true);
            var previous=db.queryForList("SELECT * FROM practice_judge_task WHERE user_id=? AND request_key=?",owner,input.requestKey());
            if(!previous.isEmpty()) {
                var old=previous.get(0);
                if(number(old,"session_id")!=sessionId || number(old,"session_question_id")!=questionId || !input.sourceCode().equals(old.get("source_code")) || !input.operation().equals(old.get("operation")) || !input.language().equals(old.get("language"))) throw new BizException("请求标识已用于其他内容");
                return task(number(old,"id"));
            }
            if(!"ONGOING".equals(s.get("status"))) throw new BizException("练习已结束或正在结算");
            var qs=db.queryForList("SELECT id,question_type,language FROM practice_session_question WHERE id=? AND session_id=?",questionId,sessionId);
            if(qs.isEmpty() || !"CODING".equals(qs.get(0).get("question_type")) || !input.language().equals(qs.get(0).get("language"))) throw new BizException("题目或语言不匹配");
            if(db.queryForObject("SELECT COUNT(*) FROM practice_judge_task WHERE user_id=? AND status IN ('QUEUED','RUNNING')",Integer.class,owner)>=3) throw new BizException("已有判题任务进行中，请稍后再试");
            var capability=judge.capability();
            String status=Boolean.TRUE.equals(capability.get("ready"))?"QUEUED":"SERVICE_UNAVAILABLE";
            Map<String,Object> initial=new LinkedHashMap<>();initial.put("status",status);initial.put("compilerVersion",((Map)capability.getOrDefault("languages",Map.of())).getOrDefault(input.language(),"未验证"));
            initial.put("message",capability.getOrDefault("notice",""));initial.put("submitted",false);
            var details=db.queryForList("SELECT detail_json FROM practice_question_detail WHERE session_question_id=?",questionId);
            var detail=details.isEmpty()?Map.<String,Object>of():decode(details.get(0).get("detail_json"));
            initial.put("questionVersion",detail.getOrDefault("questionVersion","snapshot-"+questionId));initial.put("ruleVersion",detail.getOrDefault("ruleVersion","line-ending-v1"));initial.put("judgeConfig",detail.getOrDefault("judgeConfig",Map.of()));
            long id=insert("INSERT INTO practice_judge_task(user_id,session_id,session_question_id,request_key,operation,source_code,language,status,result_json) VALUES(?,?,?,?,?,?,?,?,?)",owner,sessionId,questionId,input.requestKey(),input.operation(),input.sourceCode(),input.language(),status,encode(initial));
            return task(id);
        });
    }
    public Map<String,Object> task(long id) {
        var values=db.queryForList("SELECT * FROM practice_judge_task WHERE id=? AND user_id=?",id,user());
        if(values.isEmpty()) throw new BizException("任务不存在或无访问权限");
        return presentTask(values.get(0));
    }
    public List<Map<String,Object>> tasks(long sessionId) {
        ownedSession(sessionId,false);
        return db.queryForList("SELECT t.* FROM practice_judge_task t WHERE t.session_id=? AND t.user_id=? AND (t.status IN ('QUEUED','RUNNING') OR t.id=(SELECT MAX(t2.id) FROM practice_judge_task t2 WHERE t2.session_question_id=t.session_question_id)) ORDER BY t.id",sessionId,user()).stream().map(this::presentTask).toList();
    }
    private Map<String,Object> presentTask(Map<String,Object> row) {
        var out=new LinkedHashMap<String,Object>();out.put("id",row.get("id"));out.put("questionId",row.get("session_question_id"));out.put("status",row.get("status"));out.put("operation",row.get("operation"));out.put("createdAt",String.valueOf(row.get("created_at")));out.put("result",decode(row.get("result_json")));return out;
    }

    // One worker per application instance. SQL claim and terminal update protect against duplicate workers.
    public void work() {
        try {
            db.update("UPDATE practice_judge_task SET status='SYSTEM_ERROR',result_json=?,finished_at=NOW() WHERE status='RUNNING' AND started_at<DATE_SUB(NOW(),INTERVAL 15 MINUTE)",encode(Map.of("status","SYSTEM_ERROR","submitted",false,"message","任务执行中断，请重新提交；未计入答错。")));
            db.update("UPDATE practice_judge_task SET status='SERVICE_UNAVAILABLE',result_json=?,finished_at=NOW() WHERE status='QUEUED' AND created_at<DATE_SUB(NOW(),INTERVAL 15 MINUTE)",encode(Map.of("status","SERVICE_UNAVAILABLE","submitted",false,"message","任务排队超时，请重试；未计入答错。")));
            var values=db.queryForList("SELECT * FROM practice_judge_task WHERE status='QUEUED' ORDER BY id LIMIT 1");
            if(values.isEmpty()) return;
            var t=values.get(0);long id=number(t,"id");
            if(db.update("UPDATE practice_judge_task SET status='RUNNING',started_at=NOW() WHERE id=? AND status='QUEUED'",id)!=1) return;
            UserContext.set(number(t,"user_id"),"STUDENT");
            Map<String,Object> result;
            try {
                result=practice.evaluateTask(number(t,"session_id"),number(t,"session_question_id"),new PracticeService.CodeInput(String.valueOf(t.get("source_code")),String.valueOf(t.get("language"))),"submit".equals(t.get("operation")));
                var initial=decode(t.get("result_json"));result.put("compilerVersion",initial.getOrDefault("compilerVersion","未验证"));result.put("judgeConfig",initial.getOrDefault("judgeConfig",Map.of()));
            } catch(Exception e) {
                result=new LinkedHashMap<>(Map.of("status",e instanceof Judge0Client.UnavailableException?"SERVICE_UNAVAILABLE":"SYSTEM_ERROR","submitted",false,"message","判题服务异常或任务未完成，请重试；未计入答错。"));
            }
            var finalResult=result;
            transaction.executeWithoutResult(tx -> {
                var locked=db.queryForList("SELECT status FROM practice_judge_task WHERE id=? FOR UPDATE",id).get(0);
                if(!"RUNNING".equals(locked.get("status"))) return;
                ownedSession(number(t,"session_id"),true);
                boolean valid=!PracticeJudging.INFRASTRUCTURE.contains(String.valueOf(finalResult.get("status")));
                finalResult.put("submitted",valid && "submit".equals(t.get("operation")));
                if(Boolean.TRUE.equals(finalResult.get("submitted"))) {
                    long submissionId=insert("INSERT INTO practice_submission(session_id,session_question_id,user_id,submission_type,source_code,language,status,is_correct,stdout,stderr,compile_output,time_seconds,memory_kb) VALUES(?,?,?,'CODE',?,?,?,?,?,?,?,?,?)",t.get("session_id"),t.get("session_question_id"),t.get("user_id"),t.get("source_code"),t.get("language"),finalResult.get("status"),finalResult.get("passed"),finalResult.get("stdout"),finalResult.get("stderr"),finalResult.get("compileOutput"),finalResult.get("time"),finalResult.get("memory"));
                    finalResult.put("submissionId",submissionId);
                    db.update("INSERT INTO practice_submission_detail(submission_id,result_json) VALUES(?,?)",submissionId,encode(finalResult));
                    db.update("UPDATE practice_session_question SET status='ANSWERED' WHERE id=?",t.get("session_question_id"));
                    db.update("UPDATE practice_session SET updated_at=NOW() WHERE id=?",t.get("session_id"));
                    if(Boolean.FALSE.equals(finalResult.get("passed"))) db.update("INSERT INTO practice_review(user_id,session_question_id,state) VALUES(?,?,'PENDING') ON DUPLICATE KEY UPDATE state='PENDING'",t.get("user_id"),t.get("session_question_id"));
                    if(Boolean.TRUE.equals(finalResult.get("passed"))) {
                        var details=db.queryForList("SELECT detail_json FROM practice_question_detail WHERE session_question_id=?",t.get("session_question_id"));
                        Object origin=details.isEmpty()?null:decode(details.get(0).get("detail_json")).get("originalQuestionId");
                        if(origin instanceof Number) db.update("INSERT INTO practice_review(user_id,session_question_id,state) SELECT ?,q.id,'RESOLVED' FROM practice_session_question q JOIN practice_session s ON s.id=q.session_id WHERE q.id=? AND s.user_id=? ON DUPLICATE KEY UPDATE state='RESOLVED'",t.get("user_id"),origin,t.get("user_id"));
                        db.update("UPDATE practice_review SET state='RESOLVED' WHERE user_id=? AND session_question_id=?",t.get("user_id"),t.get("session_question_id"));
                    }
                }
                db.update("UPDATE practice_judge_task SET status=?,result_json=?,finished_at=NOW() WHERE id=? AND status='RUNNING'",finalResult.get("status"),encode(finalResult),id);
            });
        } catch(BadSqlGrammarException e) { /* V7 not applied yet: existing pages stay accessible. */ }
        finally { UserContext.clear(); }
    }

    public Map<String,Object> finish(long sessionId) {
        return transaction.execute(tx -> {
            var s=ownedSession(sessionId,true);
            if("ONGOING".equals(s.get("status"))) db.update("UPDATE practice_session SET status='CLOSING',finished_at=COALESCE(finished_at,NOW()) WHERE id=?",sessionId);
            return report(sessionId);
        });
    }
    public Map<String,Object> report(long sessionId) {
        return transaction.execute(tx -> {
            var s=ownedSession(sessionId,true);
            var saved=db.queryForList("SELECT report_json FROM practice_settlement WHERE session_id=?",sessionId);
            if(!saved.isEmpty()) return withPersonalStates(sessionId,decode(saved.get(0).get("report_json")));
            var data=practice.session(sessionId);
            var tasks=db.queryForList("SELECT t.* FROM practice_judge_task t WHERE t.session_id=? AND t.user_id=? AND (t.status IN ('QUEUED','RUNNING') OR (t.operation='submit' AND t.id=(SELECT MAX(t2.id) FROM practice_judge_task t2 WHERE t2.session_question_id=t.session_question_id AND t2.operation='submit'))) ORDER BY t.id",sessionId,user()).stream().map(this::presentTask).toList();
            boolean pending=tasks.stream().anyMatch(t -> Set.of("QUEUED","RUNNING").contains(t.get("status")));
            Map<String,Object> report=buildReport(data,tasks);
            report.put("settlementStatus",pending?"PROCESSING":"READY");report.put("historical", "FINISHED".equals(s.get("status")));
            if(!pending && !"ONGOING".equals(s.get("status"))) {
                db.update("UPDATE practice_session SET status='FINISHED' WHERE id=?",sessionId);
                report.put("status","FINISHED");
                db.update("INSERT IGNORE INTO practice_settlement(session_id,report_json) VALUES(?,?)",sessionId,encode(report));
            }
            return report;
        });
    }
    private Map<String,Object> withPersonalStates(long sessionId,Map<String,Object> report) {
        var states=db.queryForList("SELECT q.id,r.state,IF(f.session_question_id IS NULL,0,1) AS favorite FROM practice_session_question q LEFT JOIN practice_review r ON r.session_question_id=q.id AND r.user_id=? LEFT JOIN practice_favorite f ON f.session_question_id=q.id AND f.user_id=? WHERE q.session_id=?",user(),user(),sessionId);
        var byId=new HashMap<Long,Map<String,Object>>();for(var state:states)byId.put(number(state,"id"),state);
        for(var q:(List<Map<String,Object>>)report.getOrDefault("questionList",List.of())) {
            var state=byId.get(number(q,"id"));if(state!=null){q.put("reviewState",state.get("state"));q.put("favorite",number(state,"favorite")==1);}
        }
        return report;
    }
    static Map<String,Object> buildReport(Map<String,Object> session,List<Map<String,Object>> tasks) {
        var out=new LinkedHashMap<>(session);var questions=(List<Map<String,Object>>)session.getOrDefault("questionList",List.of());
        int submitted=0,passed=0,failed=0,self=0,objectiveCorrect=0,objectiveWrong=0,legacy=0;
        var entries=new ArrayList<Map<String,Object>>();
        for(var q:questions) {
            var item=new LinkedHashMap<>(q);var latest=(Map<String,Object>)q.get("latestSubmission");
            if(latest!=null && !Set.of("SYSTEM_ERROR","SERVICE_UNAVAILABLE","Internal Error","Exec Format Error").contains(latest.get("status"))) {
                submitted++;
                if("coding".equals(q.get("type"))) { if(Boolean.TRUE.equals(latest.get("isCorrect")) || latest.get("isCorrect") instanceof Number n && n.intValue()==1) passed++;else if(Boolean.FALSE.equals(latest.get("isCorrect")) || latest.get("isCorrect") instanceof Number n && n.intValue()==0) failed++; }
                else if("SELF_REVIEW".equals(latest.get("status"))) self++;
                else if("OBJECTIVE_CORRECT".equals(latest.get("status"))) objectiveCorrect++;
                else if("OBJECTIVE_INCORRECT".equals(latest.get("status"))) objectiveWrong++;
                else legacy++;
            }
            item.put("submissionId",latest==null?null:latest.get("id"));entries.add(item);
        }
        out.put("questionList",entries);
        int infrastructure=(int)tasks.stream().filter(t -> "submit".equals(t.get("operation")) && PracticeJudging.INFRASTRUCTURE.contains(String.valueOf(t.get("status")))).count();
        int pending=(int)tasks.stream().filter(t -> "submit".equals(t.get("operation")) && Set.of("QUEUED","RUNNING").contains(t.get("status"))).count();
        out.put("statistics",Map.of("total",questions.size(),"submitted",submitted,"unsubmitted",questions.size()-submitted,"codingPassed",passed,"codingFailed",failed,"codingEvaluated",passed+failed,"selfReview",self,"objectiveCorrect",objectiveCorrect,"objectiveIncorrect",objectiveWrong,"legacyReview",legacy));
        out.put("pendingCount",pending);out.put("systemFailureCount",infrastructure);out.put("tasks",tasks);
        return out;
    }
    public Map<String,Object> review(long questionId,String state) {
        if(!Set.of("PENDING","RESOLVED").contains(state)) throw new BizException("复习状态无效");
        var owned=db.queryForList("SELECT q.id FROM practice_session_question q JOIN practice_session s ON s.id=q.session_id WHERE q.id=? AND s.user_id=?",questionId,user());
        if(owned.isEmpty()) throw new BizException("题目不存在或无访问权限");
        db.update("INSERT INTO practice_review(user_id,session_question_id,state) VALUES(?,?,?) ON DUPLICATE KEY UPDATE state=VALUES(state)",user(),questionId,state);return Map.of("state",state);
    }
    public Map<String,Object> submissions(long sessionId,long questionId,int page) {
        ownedSession(sessionId,false);
        if(page<1 || page>100000) throw new BizException("分页参数无效");
        var values=db.queryForList("SELECT id,status,answer_text,source_code,score,created_at FROM practice_submission WHERE session_id=? AND session_question_id=? AND user_id=? ORDER BY id DESC LIMIT 20 OFFSET ?",sessionId,questionId,user(),(page-1)*20);
        var items=values.stream().map(v -> {
            var item=new LinkedHashMap<String,Object>();item.put("id",v.get("id"));item.put("status",v.get("status"));item.put("answerText",v.get("answer_text"));item.put("sourceCode",v.get("source_code"));item.put("score",v.get("score"));item.put("createdAt",String.valueOf(v.get("created_at")));
            var saved=db.queryForList("SELECT result_json FROM practice_submission_detail WHERE submission_id=?",v.get("id"));
            if(!saved.isEmpty()) item.put("result",decode(saved.get(0).get("result_json")));
            return item;
        }).toList();
        return Map.of("items",items,"total",db.queryForObject("SELECT COUNT(*) FROM practice_submission WHERE session_id=? AND session_question_id=? AND user_id=?",Integer.class,sessionId,questionId,user()),"page",page);
    }
    public Map<String,Object> retry(long sessionId,boolean failedOnly) {
        return transaction.execute(tx -> {
            var s=ownedSession(sessionId,true);var report=report(sessionId);
            if(!"FINISHED".equals(s.get("status")) && !"FINISHED".equals(report.get("status"))) throw new BizException("请先结束并结算当前练习");
            var questions=db.queryForList("SELECT q.* FROM practice_session_question q LEFT JOIN practice_submission a ON a.id=(SELECT MAX(a2.id) FROM practice_submission a2 WHERE a2.session_question_id=q.id AND a2.user_id=?) LEFT JOIN practice_review r ON r.user_id=? AND r.session_question_id=q.id WHERE q.session_id=?"+(failedOnly?" AND (r.state='PENDING' OR (a.is_correct=0 AND COALESCE(r.state,'PENDING')<>'RESOLVED'))":"")+" ORDER BY q.sequence_no",user(),user(),sessionId);
            if(questions.isEmpty()) throw new BizException("没有可复练的题目");
            long id=insert("INSERT INTO practice_session(user_id,job_id,company,topic,training_mode,question_type,difficulty,requested_count) VALUES(?,?,?,?,?,?,?,?)",user(),s.get("job_id"),s.get("company"),s.get("topic"),"normal",s.get("question_type"),s.get("difficulty"),questions.size());
            int seq=0;
            for(var q:questions) {
                long qid=insert("INSERT INTO practice_session_question(session_id,source_question_id,sequence_no,question_type,title,content,reference_answer,answer_keywords,difficulty,language,starter_code,tests_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",id,q.get("source_question_id"),++seq,q.get("question_type"),q.get("title"),q.get("content"),q.get("reference_answer"),q.get("answer_keywords"),q.get("difficulty"),q.get("language"),q.get("starter_code"),q.get("tests_json"));
                db.update("INSERT INTO practice_question_detail(session_question_id,detail_json) SELECT ?,JSON_SET(detail_json,'$.originalQuestionId',?) FROM practice_question_detail WHERE session_question_id=?",qid,q.get("id"),q.get("id"));
            }
            db.update("INSERT INTO practice_session_detail(session_id,detail_json) VALUES(?,?)",id,encode(Map.of("notice","按原题序复练，原记录保留。","originalSessionId",sessionId)));
            return Map.of("id",id);
        });
    }
    private Map<String,Object> ownedSession(long id,boolean lock) { var rows=db.queryForList("SELECT * FROM practice_session WHERE id=? AND user_id=?"+(lock?" FOR UPDATE":""),id,user());if(rows.isEmpty())throw new BizException("练习不存在或无访问权限");return rows.get(0); }
    private long user() { var id=UserContext.getUserId();if(id==null)throw new BizException("请先登录");return id; }
    private static long number(Map<String,Object> r,String k) { return ((Number)r.get(k)).longValue(); }
    private String encode(Object v) {try{return json.writeValueAsString(v);}catch(Exception e){throw new BizException("练习数据格式无效");} }
    private Map<String,Object> decode(Object v) {try{return v==null?new LinkedHashMap<>():json.readValue(String.valueOf(v),new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw new BizException("练习数据损坏");} }
    private long insert(String sql,Object... args) { var keys=new GeneratedKeyHolder();db.update(c -> {var p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p;},keys);return Objects.requireNonNull(keys.getKey()).longValue(); }
}
