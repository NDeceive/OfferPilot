package com.zhimian.service;

import com.zhimian.common.BizException;
import com.zhimian.config.UserContext;
import com.zhimian.entity.InterviewMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/** Durable projection of committed messages onto explicit recording windows.
 * Control never locks the interview session: ASR/LLM work cannot block it.
 * Source timestamps are persisted in the original message transaction, so projection
 * can be retried after a crash without an in-memory event or backfilling old sessions. */
@Service
@RequiredArgsConstructor
public class InterviewMinutesService {
    private final JdbcTemplate db;

    public LocalDateTime clock() { return db.queryForObject("SELECT CURRENT_TIMESTAMP(6)", LocalDateTime.class); }
    public void stamp(InterviewMessage message) { message.setMinutesCapturedAt(clock()); }

    public void initialize(long sid, boolean enabled) {
        if(db.update("INSERT IGNORE INTO minutes_document(session_id,enabled,coverage) VALUES (?,?,?)",sid,enabled,enabled?"FULL":"PARTIAL")!=1)return;
        if(enabled) {
            db.update("INSERT INTO minutes_window(session_id,opened_at) VALUES (?,CURRENT_TIMESTAMP(6))",sid);
            event(sid,"纪要已开启",null,true,0L);
        }
    }
    private Map<String,Object> own(long sid) {
        var rows=db.queryForList("SELECT s.user_id,s.status,COALESCE(NULLIF(TRIM(u.nickname),''),NULLIF(u.username,''),'考生') AS candidate_name FROM interview_session s JOIN sys_user u ON u.id=s.user_id WHERE s.id=?",sid);
        if(rows.isEmpty() || !Objects.equals(number(rows.get(0).get("user_id")),UserContext.getUserId()))
            throw new BizException("无权访问该面试纪要");
        return rows.get(0);
    }
    private Map<String,Object> doc(long sid,boolean lock) {
        var rows=db.queryForList("SELECT * FROM minutes_document WHERE session_id=?"+(lock?" FOR UPDATE":""),sid);
        return rows.isEmpty()?null:rows.get(0);
    }
    public static long number(Object n) { return n instanceof Number ? ((Number)n).longValue() : 0; }
    public Map<String,Object> reportContext(long reportId) {
        var rows=db.queryForList("SELECT r.session_id AS sessionId FROM interview_report r JOIN interview_session s ON s.id=r.session_id WHERE r.id=? AND s.user_id=?",reportId,UserContext.getUserId());
        if(rows.isEmpty())throw new BizException("无权访问该面试纪要");
        return rows.get(0);
    }
    public Map<Long,String> badges() {
        var rows=db.queryForList("SELECT d.session_id,d.enabled,d.lifecycle,(SELECT j.state FROM minutes_summary_job j WHERE j.session_id=d.session_id ORDER BY j.id DESC LIMIT 1) AS summary_state,EXISTS(SELECT 1 FROM minutes_entry e WHERE e.session_id=d.session_id AND e.source_message_id IS NOT NULL) AS has_entries FROM minutes_document d JOIN interview_session s ON s.id=d.session_id WHERE s.user_id=? AND d.lifecycle<>'DELETED'",UserContext.getUserId());
        var result=new HashMap<Long,String>();
        for(var row:rows) {
            String state=String.valueOf(row.get("summary_state"));
            String badge=Set.of("QUEUED","RUNNING").contains(state)?"总结生成中":"FAILED".equals(state)?"总结生成失败":yes(row.get("has_entries"))?"有纪要":yes(row.get("enabled"))?"纪要已开启":null;
            if(badge!=null)result.put(number(row.get("session_id")),badge);
        }
        return result;
    }
    private static boolean yes(Object n) { return Boolean.TRUE.equals(n)||number(n)==1; }
    private static Timestamp time(Object v) { return v instanceof Timestamp t?t:Timestamp.valueOf((LocalDateTime)v); }
    private void event(long sid,String text,String request,Boolean value,Long version) {
        db.update("INSERT INTO minutes_entry(session_id,role,entry_type,original_text,submitted_at,request_id,control_value,control_version) VALUES (?,'SYSTEM','EVENT',?,CURRENT_TIMESTAMP(6),?,?,?)",sid,text,request,value,version);
    }
    private void closeWindow(long sid) {
        db.update("UPDATE minutes_window SET closed_at=CURRENT_TIMESTAMP(6) WHERE session_id=? AND closed_at IS NULL",sid);
    }
    @Transactional
    public Map<String,Object> control(long sid,boolean enabled,long version,String request) {
        var source=own(sid);var d=doc(sid,true);
        if(d==null)throw new BizException("旧面试不支持补开纪要，请在新面试中开启");
        if("DELETED".equals(d.get("lifecycle")))throw new BizException("本场纪要已删除，不能重新开启");
        var receipts=db.queryForList("SELECT control_value,control_version FROM minutes_entry WHERE session_id=? AND request_id=?",sid,request);
        if(!receipts.isEmpty()) {
            if(yes(receipts.get(0).get("control_value"))!=enabled)throw new BizException("请求编号已用于其他开关操作");
            return metadata(sid,d,source);
        }
        if(!"ACTIVE".equals(d.get("lifecycle")) || !"ONGOING".equals(source.get("status")))throw new BizException("面试已结束，不能改变记录范围");
        if(number(d.get("control_version"))!=version)throw new BizException("纪要状态已更新，请刷新后重试");
        if(yes(d.get("enabled"))!=enabled) {
            closeWindow(sid);
            if(enabled&&!yes(d.get("paused"))) db.update("INSERT INTO minutes_window(session_id,opened_at) VALUES (?,CURRENT_TIMESTAMP(6))",sid);
            db.update("UPDATE minutes_document SET enabled=?,control_version=control_version+1,coverage='PARTIAL' WHERE session_id=?",enabled,sid);
        }
        var updated=doc(sid,false);
        event(sid,enabled?"纪要已开启；从下一次完整提问开始记录":"纪要已关闭；此后内容未记录",request,enabled,number(updated.get("control_version")));
        return metadata(sid,updated,source);
    }
    public void paused(long sid,boolean paused) {
        var d=doc(sid,true);if(d==null||!"ACTIVE".equals(d.get("lifecycle"))||yes(d.get("paused"))==paused)return;
        closeWindow(sid);
        if(!paused&&yes(d.get("enabled")))db.update("INSERT INTO minutes_window(session_id,opened_at) VALUES (?,CURRENT_TIMESTAMP(6))",sid);
        db.update("UPDATE minutes_document SET paused=?,control_version=control_version+1,coverage='PARTIAL' WHERE session_id=?",paused,sid);
        if(yes(d.get("enabled")))event(sid,paused?"纪要随面试暂停":"纪要已恢复；从下一次完整提问开始记录",null,null,null);
    }
    /** Caller has finished all source writes. No external calls after locking document. */
    public void finish(long sid) {
        var d=doc(sid,true);if(d==null||!"ACTIVE".equals(d.get("lifecycle")))return;
        closeWindow(sid);project(sid);
        long count=count(sid);
        db.update("UPDATE minutes_document SET enabled=FALSE,lifecycle='FINALIZED',finalized_at=CURRENT_TIMESTAMP(6),coverage=? WHERE session_id=?",count==0?"EMPTY":d.get("coverage"),sid);
        if(count>0)db.update("INSERT IGNORE INTO minutes_summary_job(session_id,request_id) VALUES (?,'automatic-v1')",sid);
    }
    private long count(long sid) { return db.queryForObject("SELECT COUNT(*) FROM minutes_entry WHERE session_id=? AND source_message_id IS NOT NULL",Long.class,sid); }

    /** Runs under document lock, reads only committed, explicitly timestamped source data.
     * SQL CASE-free Java ordering tracks prompt instances rather than reused question IDs. */
    void project(long sid) {
        var windows=db.queryForList("SELECT * FROM minutes_window WHERE session_id=? ORDER BY id",sid);
        var messages=db.queryForList("SELECT id,round_no,role,msg_type,content,minutes_captured_at FROM interview_message WHERE session_id=? ORDER BY id",sid);
        Map<String,Object> prompt=null;
        Long promptWindow=null;
        for(var m:messages) {
            String type=String.valueOf(m.get("msg_type"));
            if(!Set.of("MAIN","FOLLOWUP","ANSWER","SKIPPED").contains(type))continue;
            Long w=windowAt(windows,m.get("minutes_captured_at"));
            boolean interviewer="INTERVIEWER".equals(m.get("role"));
            if(interviewer) { prompt=m;promptWindow=w; }
            if(w==null || (!interviewer && (prompt==null||!w.equals(promptWindow))))continue;
            Long parent=null;
            if(!interviewer) {
                var parents=db.queryForList("SELECT id FROM minutes_entry WHERE session_id=? AND source_message_id=?",Long.class,sid,prompt.get("id"));
                if(parents.isEmpty())continue;parent=parents.get(0);
            }
            db.update("INSERT IGNORE INTO minutes_entry(session_id,source_message_id,window_id,parent_entry_id,round_no,role,entry_type,original_text,submitted_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    sid,m.get("id"),w,parent,m.get("round_no"),m.get("role"),type,m.get("content"),m.get("minutes_captured_at"));
        }
    }
    static Long windowAt(List<Map<String,Object>> windows,Object instant) {
        if(instant==null)return null;Timestamp t=time(instant);
        for(var w:windows)if(t.after(time(w.get("opened_at"))) && (w.get("closed_at")==null||t.before(time(w.get("closed_at")))))return number(w.get("id"));
        return null;
    }
    @Transactional
    public Map<String,Object> state(long sid) {
        var source=own(sid);var d=doc(sid,true);
        if(d!=null&&"ACTIVE".equals(d.get("lifecycle")))project(sid);
        return metadata(sid,d,source);
    }
    private Map<String,Object> metadata(long sid,Map<String,Object>d,Map<String,Object>source) {
        var out=new LinkedHashMap<String,Object>();
        out.put("sessionId",sid);out.put("enabled",d!=null&&yes(d.get("enabled")));
        out.put("candidateName",source.get("candidate_name"));
        out.put("controlVersion",d==null?0:number(d.get("control_version")));
        out.put("lifecycle",d==null?"UNAVAILABLE":d.get("lifecycle"));out.put("coverage",d==null?"EMPTY":d.get("coverage"));
        out.put("paused",d!=null&&yes(d.get("paused")));out.put("recordedEntryCount",d==null?0:count(sid));
        out.put("sourceStatus",source.get("status"));
        out.put("canControl",d!=null&&"ACTIVE".equals(d.get("lifecycle"))&&"ONGOING".equals(source.get("status")));
        var windows=db.queryForList("SELECT id,opened_at,closed_at FROM minutes_window WHERE session_id=? ORDER BY id",sid);out.put("windows",windows);
        var latest=db.queryForList("SELECT minutes_captured_at FROM interview_message WHERE session_id=? AND msg_type IN ('MAIN','FOLLOWUP') ORDER BY id DESC LIMIT 1",sid);
        Long w=latest.isEmpty()?null:windowAt(windows,latest.get(0).get("minutes_captured_at"));
        out.put("waitingForQuestion",yes(out.get("enabled"))&&(w==null||windows.stream().noneMatch(x->number(x.get("id"))==w&&x.get("closed_at")==null)));
        var jobs=db.queryForList("SELECT id,state,error_code,attempts,completed_at FROM minutes_summary_job WHERE session_id=? ORDER BY id DESC LIMIT 1",sid);
        out.put("summaryState",jobs.isEmpty()?"NONE":jobs.get(0).get("state"));
        out.put("summaryError",jobs.isEmpty()?null:jobs.get(0).get("error_code"));
        out.put("hasSummary",d!=null&&d.get("active_summary_job_id")!=null);
        return out;
    }
    @Transactional
    public List<Map<String,Object>> entries(long sid,long after,int limit) {
        var source=own(sid);var d=doc(sid,true);if(d==null||"DELETED".equals(d.get("lifecycle")))return List.of();
        if("ACTIVE".equals(d.get("lifecycle")))project(sid);
        var result=db.queryForList("SELECT id,source_message_id AS sourceMessageId,parent_entry_id AS parentEntryId,round_no AS roundNo,role,entry_type AS entryType,original_text AS text,submitted_at AS submittedAt FROM minutes_entry WHERE session_id=? AND id>? ORDER BY id LIMIT ?",sid,after,Math.max(1,Math.min(100,limit)));
        for(var entry:result) {
            entry.put("speakerName","CANDIDATE".equals(entry.get("role"))?source.get("candidate_name"):"面试官");
            if("MAIN".equals(entry.get("entryType"))||"FOLLOWUP".equals(entry.get("entryType")))
                entry.put("answerMissing",db.queryForObject("SELECT COUNT(*) FROM minutes_entry WHERE session_id=? AND parent_entry_id=?",Long.class,sid,entry.get("id"))==0 && !"ACTIVE".equals(d.get("lifecycle")));
            // Finalized, fully-covered rounds only. The recorder starts after its main
            // prompt and ends before the next round. Never link a mixed-window WAV.
            if("FINALIZED".equals(d.get("lifecycle")) && "FULL".equals(d.get("coverage")) && "ANSWER".equals(entry.get("entryType"))) {
                var stats=db.queryForMap("SELECT COUNT(*) AS n,COUNT(DISTINCT window_id) AS w,MAX(id) AS last_id FROM minutes_entry WHERE session_id=? AND round_no=? AND source_message_id IS NOT NULL",sid,entry.get("roundNo"));
                long original=db.queryForObject("SELECT COUNT(*) FROM interview_message WHERE session_id=? AND round_no=? AND msg_type IN ('MAIN','FOLLOWUP','ANSWER','SKIPPED')",Long.class,sid,entry.get("roundNo"));
                if(number(stats.get("n"))==original && number(stats.get("w"))==1 && number(stats.get("last_id"))==number(entry.get("id"))) {
                    var audio=db.queryForList("SELECT id FROM interview_round_audio WHERE session_id=? AND round_no=? AND status='OK'",Long.class,sid,entry.get("roundNo"));
                    if(!audio.isEmpty())entry.put("audioId",audio.get(0));
                }
            }
        }
        return result;
    }
    @Transactional
    public Map<String,Object> summary(long sid) {
        own(sid);var d=doc(sid,false);
        if(d==null||"DELETED".equals(d.get("lifecycle"))||d.get("active_summary_job_id")==null)return Map.of();
        var rows=db.queryForList("SELECT id,output_json AS outputJson,completed_at AS completedAt FROM minutes_summary_job WHERE id=? AND session_id=? AND state='READY'",d.get("active_summary_job_id"),sid);
        return rows.isEmpty()?Map.of():rows.get(0);
    }
    @Transactional
    public Map<String,Object> regenerate(long sid,String request) {
        own(sid);var d=doc(sid,true);
        if(d==null||!"FINALIZED".equals(d.get("lifecycle"))||count(sid)==0)throw new BizException("本场尚无可总结的已结束纪要");
        var receipt=db.queryForList("SELECT id,state FROM minutes_summary_job WHERE session_id=? AND request_id=?",sid,request);
        if(!receipt.isEmpty())return receipt.get(0);
        if(db.queryForObject("SELECT COUNT(*) FROM minutes_summary_job WHERE session_id=? AND state IN ('QUEUED','RUNNING')",Long.class,sid)>0)throw new BizException("总结正在生成，请稍候");
        db.update("INSERT INTO minutes_summary_job(session_id,request_id) VALUES (?,?)",sid,request);
        return db.queryForMap("SELECT id,state FROM minutes_summary_job WHERE session_id=? AND request_id=?",sid,request);
    }
    @Transactional
    public void delete(long sid) { own(sid);purge(sid); }
    public void purge(long sid) {
        var d=doc(sid,true);if(d==null)return;
        closeWindow(sid);
        db.update("UPDATE minutes_document SET enabled=FALSE,lifecycle='DELETED',control_version=control_version+1,active_summary_job_id=NULL WHERE session_id=?",sid);
        db.update("DELETE FROM minutes_entry WHERE session_id=?",sid);
        db.update("DELETE FROM minutes_summary_job WHERE session_id=?",sid);
        db.update("DELETE FROM minutes_window WHERE session_id=?",sid);
    }
    @Transactional
    public String export(long sid,String kind,String format) {
        if(!Set.of("record","summary").contains(kind)||!Set.of("md","txt").contains(format))throw new BizException("不支持的导出格式");
        var meta=state(sid);
        if(Set.of("DELETED","UNAVAILABLE").contains(String.valueOf(meta.get("lifecycle"))))throw new BizException("本场没有可导出的纪要");
        boolean markdown="md".equals(format);
        var result=new StringBuilder(markdown?"# ":"");
        result.append(kind.equals("record")?"面试记录\n\n":"AI 总结\n\n");
        if("PARTIAL".equals(meta.get("coverage")))result.append("仅含已记录的对话。\n\n");
        if(kind.equals("record")) {
            var all=new ArrayList<Map<String,Object>>();
            long cursor=0;while(true){var page=entries(sid,cursor,100);if(page.isEmpty())break;all.addAll(page);cursor=number(page.get(page.size()-1).get("id"));}
            all.sort(Comparator.comparing(e->time(e.get("submittedAt"))));
            for(var e:all)if(Set.of("MAIN","FOLLOWUP","ANSWER").contains(String.valueOf(e.get("entryType"))))
                result.append(markdown?"### ":"").append(e.get("speakerName")).append("\n\n").append(e.get("text")).append("\n\n");
        } else {
            var s=summary(sid);if(s.isEmpty())throw new BizException("AI 总结尚未生成");
            try {
                var root=new com.fasterxml.jackson.databind.ObjectMapper().readTree((String)s.get("outputJson"));
                result.append(MinutesSummaryJobService.storedParagraph(root)).append('\n');
            }
            catch(java.io.IOException e){throw new BizException("总结格式不可用，请重新生成");}
        }
        return result.toString();
    }
}
