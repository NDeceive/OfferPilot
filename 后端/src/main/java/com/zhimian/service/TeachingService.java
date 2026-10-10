package com.zhimian.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.BizException;
import com.zhimian.common.ForbiddenException;
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

/** Persistent teaching relationships. All access checks use authenticated IDs. */
@Service
@RequiredArgsConstructor
public class TeachingService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final ModulePreferenceService modulePreferences;
    public record ClassInput(@NotBlank @Size(max=80) String name,@NotBlank @Size(max=80) String semester) {}
    public record JoinInput(@NotBlank @Size(max=32) String inviteCode) {}
    public record ReviewInput(@NotBlank @Size(max=5000) String text) {}
    public record DeadlineInput(@NotNull LocalDateTime deadline) {}
    public record TaskInput(@NotNull Long classId,@NotBlank @Size(max=100) String title,
        @Size(max=2000) String description,@NotNull Long jobId,
        @NotEmpty @Size(max=20) List<@NotBlank @Size(max=2000) String> questions,
        @Min(300) @Max(3600) int durationSeconds,@Min(1) @Max(3) int difficulty,
        @Min(1) @Max(10) int minAttempts,@Min(1) @Max(20) int maxAttempts,
        boolean allowLate,@NotNull LocalDateTime startTime,@NotNull LocalDateTime deadline,
        @Size(max=200) List<Long> studentIds,@Size(max=5) List<@NotNull @jakarta.validation.Valid ScoreInput> scoreModules,
        String recipientMode) {
        @AssertTrue(message="请选择全班或至少一名指定学生，指定模式零人不能分配给全班")
        public boolean isRecipientSelectionValid(){return recipientMode==null||"ALL".equals(recipientMode)||"SELECTED".equals(recipientMode)&&studentIds!=null&&!studentIds.isEmpty();}
    }
    public record ScoreInput(@NotBlank String code,@Min(1) @Max(5) int rank,@Min(1) @Max(3) int level) {}
    public record SupplementInput(@NotEmpty @Size(max=200) List<Long> studentIds,@NotBlank @Size(max=500) String reason) {}
    public record OverrideInput(@Min(0) @Max(20) int extraAttempts,LocalDateTime deadline,boolean exempt,@NotBlank @Size(max=500) String reason) {}
    private static final List<String> DEFAULT_MODULES=List.of("technical_base","project_expression","logical_structure","position_cognition","followup_adaptability");
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new BizException("配置格式无效");}}
    private Map<String,Object> decode(Object value){try{return json.readValue(String.valueOf(value),new TypeReference<Map<String,Object>>(){});}catch(Exception e){throw new BizException("评分配置损坏，请联系教师");}}
    public Map<String,Object> buildScorePlan(List<ScoreInput> input,int version){
        var items=input==null||input.isEmpty()?DEFAULT_MODULES.stream().map(c->new ScoreInput(c,1,2)).toList():input;
        if(items.size()!=5||items.stream().map(ScoreInput::code).distinct().count()!=5)throw new BizException("请选择五个不同的评分维度");
        var names=new LinkedHashMap<String,String>();modulePreferences.listModules().forEach(m->names.put(m.getCode(),m.getName()));
        var preferences=new ArrayList<ModulePreferenceService.PreferenceItem>();
        for(var item:items){if(!names.containsKey(item.code())||item.level()<1||item.level()>3||item.rank()<1||item.rank()>5)throw new BizException("评分维度或目标等级无效");preferences.add(new ModulePreferenceService.PreferenceItem(item.code(),item.rank(),item.level()));}
        var ranks=items.stream().collect(java.util.stream.Collectors.groupingBy(ScoreInput::rank,TreeMap::new,java.util.stream.Collectors.counting()));int expected=1;
        for(var group:ranks.entrySet()){if(group.getKey()!=expected)throw new BizException("维度优先级须连续，并列名次需占位");expected+=group.getValue().intValue();}
        var weights=modulePreferences.calculateWeights(preferences);
        var modules=items.stream().sorted(Comparator.comparingInt(ScoreInput::rank)).map(m->Map.of("code",m.code(),"name",names.get(m.code()),"rank",m.rank(),"level",m.level(),"weight",weights.get(m.code()),"target",modulePreferences.getTargetScore(m.level()))).toList();
        return Map.of("source","TEACHER_TASK","version",version,"modules",modules);
    }
    public Map<String,Object> scorePlan(long taskId){var rows=rows("SELECT config_json FROM teaching_score_plan WHERE task_id=?",taskId);return rows.isEmpty()?Map.of("source","LEGACY_DEFAULT","modules",List.of()):decode(rows.get(0).get("configJson"));}
    private void saveScorePlan(long taskId,List<ScoreInput> input){int version=db.queryForObject("SELECT COALESCE(MAX(config_version),0)+1 FROM teaching_score_plan WHERE task_id=?",Integer.class,taskId);var plan=buildScorePlan(input,version);db.update("INSERT INTO teaching_score_plan(task_id,config_json,config_version) VALUES(?,?,?) ON DUPLICATE KEY UPDATE config_json=VALUES(config_json),config_version=VALUES(config_version),updated_at=NOW()",taskId,encode(plan),version);}
    public void snapshotTeaching(long sessionId,Map<String,Object> task){
        long taskId=id(task,"id");var plan=scorePlan(taskId);
        // Historical tasks keep their legacy origin; new sessions receive an explicit default snapshot.
        if("LEGACY_DEFAULT".equals(plan.get("source"))){var defaults=new LinkedHashMap<>(buildScorePlan(null,0));defaults.put("source","LEGACY_DEFAULT");plan=defaults;}
        var modules=(List<Map<String,Object>>)plan.get("modules");var pref=modules.stream().map(m->new ModulePreferenceService.PreferenceItem(String.valueOf(m.get("code")),((Number)m.get("rank")).intValue(),((Number)m.get("level")).intValue())).toList();
        modulePreferences.savePreference(sessionId,user(),pref);
        db.update("INSERT INTO interview_training_context(session_id,context_json) VALUES(?,?)",sessionId,encode(Map.of("source","TEACHING","taskId",taskId,"taskTitle",task.get("title"),"classId",task.get("classId"),"className",task.get("className"),"taskContentVersion",task.get("version"),"scorePlan",plan)));
    }
    public Map<String,Object> reportContext(long sessionId,long reportId){
        var context=new LinkedHashMap<String,Object>();var saved=rows("SELECT context_json FROM interview_training_context WHERE session_id=?",sessionId);
        if(!saved.isEmpty())context.putAll(decode(saved.get(0).get("contextJson")));
        var task=rows("SELECT t.id task_id,t.title task_title,c.id class_id,c.name class_name,a.id assignment_id,a.student_id,COALESCE(NULLIF(u.nickname,''),u.username) student_name,p.state,p.valid,p.failure_reason FROM teaching_attempt p JOIN teaching_assignment a ON a.id=p.assignment_id JOIN teaching_task t ON t.id=a.task_id JOIN teaching_class c ON c.id=t.class_id JOIN sys_user u ON u.id=a.student_id WHERE p.session_id=?",sessionId);
        if(!task.isEmpty()){task.get(0).forEach(context::putIfAbsent);context.put("source","TEACHING");context.putIfAbsent("scorePlan",Map.of("source","LEGACY_DEFAULT","modules",List.of()));}
        else {context.put("source","SELF");context.put("scorePlan",Map.of("source",modulePreferences.getPreference(sessionId).isEmpty()?"LEGACY_DEFAULT":"STUDENT_SELECTED"));}
        var provenance=rows("SELECT * FROM interview_score_provenance WHERE report_id=?",reportId);context.put("provenance",provenance.isEmpty()?Map.of("ruleVersion","LEGACY_UNKNOWN"):provenance.get(0));return context;
    }
    public void scoreProvenance(long reportId,String source,String model){db.update("INSERT INTO interview_score_provenance(report_id,rule_version,model_version,prompt_version,score_source) VALUES(?,'selected-modules-v2',?,'module-evidence-v1',?) ON DUPLICATE KEY UPDATE rule_version=VALUES(rule_version),model_version=VALUES(model_version),prompt_version=VALUES(prompt_version),score_source=VALUES(score_source)",reportId,model,source);}
    @Transactional public void supplement(long taskId,SupplementInput input){
        var task=ownTask(taskId);activeClass(task);if(task.get("publishedAt")==null||task.get("endedAt")!=null)throw new BizException("只能补充分配尚未手动结束的已发布任务");
        var eligible=members(id(task,"classId")).stream().filter(m->"JOINED".equals(m.get("state"))&&id(m,"accountStatus")==1).map(m->id(m,"studentId")).toList();
        if(!eligible.containsAll(input.studentIds()))throw new BizException("包含未加入或已停用的学生");
        for(long studentId:new HashSet<>(input.studentIds())){int added=db.update("INSERT IGNORE INTO teaching_assignment(task_id,student_id) VALUES(?,?)",taskId,studentId);if(added>0){long assignmentId=id(one("SELECT id FROM teaching_assignment WHERE task_id=? AND student_id=?",taskId,studentId),"id");message(studentId,"新的教学任务",String.valueOf(task.get("title")),"/my/tasks/"+assignmentId);}}
        change(taskId,null,"SUPPLEMENT",input);
    }
    @Transactional public void override(long taskId,long assignmentId,OverrideInput input){
        var task=ownTask(taskId);activeClass(task);if(task.get("publishedAt")==null||task.get("endedAt")!=null)throw new BizException("已结束或未发布任务不能安排补练");
        var assignment=one("SELECT * FROM teaching_assignment WHERE id=? AND task_id=? FOR UPDATE",assignmentId,taskId);
        if(input.deadline()!=null&&(!input.deadline().isAfter(LocalDateTime.now())||!input.deadline().isAfter(date(task,"startTime"))))throw new BizException("个人截止时间必须晚于当前时间和任务开始时间");
        db.update("INSERT INTO teaching_assignment_override(assignment_id,extra_attempts,deadline,exempt,reason) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE extra_attempts=VALUES(extra_attempts),deadline=VALUES(deadline),exempt=VALUES(exempt),reason=VALUES(reason),updated_at=NOW()",assignmentId,input.extraAttempts(),input.deadline(),input.exempt(),input.reason().trim());
        change(taskId,assignmentId,"OVERRIDE",input);message(id(assignment,"studentId"),"教学任务要求调整",input.reason().trim(),"/my/tasks/"+assignmentId);
    }
    private void change(long taskId,Long assignmentId,String action,Object detail){db.update("INSERT INTO teaching_change_log(teacher_id,task_id,assignment_id,action,detail_json) VALUES(?,?,?,?,?)",user(),taskId,assignmentId,action,encode(detail));}
    private static final String TASK_SELECT="SELECT t.*,c.teacher_id,c.name class_name,c.archived,j.name job_name FROM teaching_task t JOIN teaching_class c ON c.id=t.class_id JOIN job_position j ON j.id=t.job_id ";
    public long user(){return UserContext.getUserId();}
    public void teacher(){if(!Set.of("TEACHER","ADMIN").contains(String.valueOf(UserContext.getRole())))throw new BizException("需要教师身份");}
    public void student(){if(!"STUDENT".equals(UserContext.getRole()))throw new BizException("需要学生身份");}
    public List<Map<String,Object>> rows(String sql,Object... args){return db.queryForList(sql,args).stream().map(this::camel).toList();}
    private Map<String,Object> camel(Map<String,Object> raw){var out=new LinkedHashMap<String,Object>();raw.forEach((k,v)->{StringBuilder key=new StringBuilder();boolean upper=false;for(char c:k.toCharArray()){if(c=='_')upper=true;else{key.append(upper?Character.toUpperCase(c):c);upper=false;}}out.put(key.toString(),v instanceof Timestamp ts?ts.toLocalDateTime():v);});return out;}
    private Map<String,Object> one(String sql,Object... args){var list=rows(sql,args);if(list.isEmpty())throw new BizException("记录不存在或无访问权限");return list.get(0);}
    private Map<String,Object> owned(String sql,Object... args){var list=rows(sql,args);if(list.isEmpty())throw new ForbiddenException("记录不存在或无访问权限");return list.get(0);}
    public static long id(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    public static boolean flag(Map<String,Object> row,String key){Object value=row.get(key);return Boolean.TRUE.equals(value)||value instanceof Number n&&n.intValue()!=0;}
    public static LocalDateTime date(Map<String,Object> row,String key){return (LocalDateTime)row.get(key);}
    private long insert(String sql,Object... args){var keys=new GeneratedKeyHolder();db.update(connection->{var stmt=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)stmt.setObject(i+1,args[i]);return stmt;},keys);return Objects.requireNonNull(keys.getKey()).longValue();}
    private Map<String,Object> ownClass(long classId){teacher();return owned("SELECT * FROM teaching_class WHERE id=? AND teacher_id=?",classId,user());}
    private Map<String,Object> ownTask(long taskId){teacher();return owned(TASK_SELECT+"WHERE t.id=? AND c.teacher_id=?",taskId,user());}
    private void activeClass(Map<String,Object> c){if(flag(c,"archived"))throw new BizException("班级已归档，不能修改成员或任务");}
    public List<Map<String,Object>> classes(){
        if("STUDENT".equals(UserContext.getRole()))return rows("SELECT c.id,c.name,c.semester,c.archived,m.state,COALESCE(NULLIF(u.nickname,''),u.username) teacher_name FROM teaching_class c JOIN teaching_member m ON m.class_id=c.id JOIN sys_user u ON u.id=c.teacher_id WHERE m.student_id=? ORDER BY c.id DESC",user());
        teacher();return rows("SELECT c.*,(SELECT COUNT(*) FROM teaching_member m WHERE m.class_id=c.id AND m.state='JOINED') joined_count,(SELECT COUNT(*) FROM teaching_member m WHERE m.class_id=c.id AND m.state='PENDING') pending_count FROM teaching_class c WHERE c.teacher_id=? ORDER BY c.id DESC",user());
    }
    @Transactional public long createClass(ClassInput input){teacher();return insert("INSERT INTO teaching_class(teacher_id,name,semester,invite_code) VALUES(?,?,?,?)",user(),input.name().trim(),input.semester().trim(),code());}
    @Transactional public void editClass(long id,ClassInput input){activeClass(ownClass(id));db.update("UPDATE teaching_class SET name=?,semester=? WHERE id=?",input.name().trim(),input.semester().trim(),id);}
    private String code(){return UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT);}
    @Transactional public void resetInvite(long classId){activeClass(ownClass(classId));db.update("UPDATE teaching_class SET invite_code=? WHERE id=?",code(),classId);}
    @Transactional public void archive(long classId){ownClass(classId);db.update("UPDATE teaching_class SET archived=TRUE WHERE id=?",classId);db.update("UPDATE teaching_task SET ended_at=COALESCE(ended_at,NOW()),version=version+1 WHERE class_id=? AND published_at IS NOT NULL",classId);}
    @Transactional public void join(JoinInput input){student();var c=one("SELECT * FROM teaching_class WHERE invite_code=? FOR UPDATE",input.inviteCode().trim().toUpperCase(Locale.ROOT));activeClass(c);long classId=id(c,"id");var existing=rows("SELECT * FROM teaching_member WHERE class_id=? AND student_id=?",classId,user());if(!existing.isEmpty()&&Set.of("JOINED","PENDING").contains(existing.get(0).get("state")))return;
        db.update("INSERT INTO teaching_member(class_id,student_id,state) VALUES(?,?,'PENDING') ON DUPLICATE KEY UPDATE state='PENDING',joined_at=NULL",classId,user());
        message(id(c,"teacherId"),"新的班级加入申请",c.get("name")+"收到学生加入申请","/teacher/classes/"+classId+"/members");
    }
    public List<Map<String,Object>> members(long classId){ownClass(classId);return rows("SELECT m.*,u.username,COALESCE(NULLIF(u.nickname,''),u.username) name,u.status account_status FROM teaching_member m JOIN sys_user u ON u.id=m.student_id WHERE m.class_id=? ORDER BY m.id",classId);}
    @Transactional public void membership(long classId,long memberId,String state){activeClass(ownClass(classId));if(!Set.of("JOINED","REJECTED","REMOVED").contains(state))throw new BizException("成员状态无效");var m=one("SELECT * FROM teaching_member WHERE id=? AND class_id=? FOR UPDATE",memberId,classId);if("JOINED".equals(state)&&!"PENDING".equals(m.get("state")))throw new BizException("只能审核待加入申请");if("REJECTED".equals(state)&&!"PENDING".equals(m.get("state")))throw new BizException("只能拒绝待审核申请");if("REMOVED".equals(state)&&!"JOINED".equals(m.get("state")))throw new BizException("只能移出已加入成员");db.update("UPDATE teaching_member SET state=?,joined_at=CASE WHEN ?='JOINED' THEN NOW() ELSE joined_at END WHERE id=?",state,state,memberId);message(id(m,"studentId"),"班级成员状态更新","当前状态："+state,"/my/classes");}
    public List<Map<String,Object>> people(){teacher();return rows("SELECT m.student_id id,COALESCE(NULLIF(u.nickname,''),u.username) name,u.username,c.id class_id,c.name class_name FROM teaching_member m JOIN teaching_class c ON c.id=m.class_id JOIN sys_user u ON u.id=m.student_id WHERE c.teacher_id=? AND m.state='JOINED' ORDER BY c.id,m.id",user());}
    private void validate(TaskInput r){if(!r.isRecipientSelectionValid())throw new BizException("指定学生模式至少选择一人");activeClass(ownClass(r.classId()));if(r.maxAttempts()<r.minAttempts())throw new BizException("最大次数不能小于有效完成次数");if(!r.deadline().isAfter(r.startTime())||!r.deadline().isAfter(LocalDateTime.now()))throw new BizException("截止时间必须晚于开始时间和当前时间");if(db.queryForObject("SELECT COUNT(*) FROM job_position WHERE id=?",Integer.class,r.jobId())==0)throw new BizException("岗位不存在");}
    @Transactional public long createTask(TaskInput input){validate(input);String questions;try{questions=json.writeValueAsString(input.questions().stream().map(String::trim).toList());}catch(Exception e){throw new BizException("题目格式无效");}
        long taskId=insert("INSERT INTO teaching_task(class_id,title,description,job_id,questions_json,duration_seconds,difficulty,min_attempts,max_attempts,allow_late,start_time,deadline) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",input.classId(),input.title().trim(),input.description(),input.jobId(),questions,input.durationSeconds(),input.difficulty(),input.minAttempts(),input.maxAttempts(),input.allowLate(),input.startTime(),input.deadline());
        allocate(taskId,input);saveScorePlan(taskId,input.scoreModules());return taskId;
    }
    private void allocate(long taskId,TaskInput input){
        var members=members(input.classId()).stream().filter(m->"JOINED".equals(m.get("state"))&&id(m,"accountStatus")==1).toList();Set<Long> selected="ALL".equals(input.recipientMode())||input.studentIds()==null||input.studentIds().isEmpty()?null:new HashSet<>(input.studentIds());
        if(selected!=null&&!members.stream().map(m->id(m,"studentId")).toList().containsAll(selected))throw new BizException("包含未加入或已停用的学生");
        for(var m:members)if(selected==null||selected.contains(id(m,"studentId")))db.update("INSERT INTO teaching_assignment(task_id,student_id) VALUES(?,?)",taskId,id(m,"studentId"));
        if(db.queryForObject("SELECT COUNT(*) FROM teaching_assignment WHERE task_id=?",Integer.class,taskId)==0)throw new BizException("请先加入至少一名正常学生");
    }
    @Transactional public void editTask(long taskId,TaskInput input){
        teacher();var t=owned(TASK_SELECT+"WHERE t.id=? AND c.teacher_id=? FOR UPDATE",taskId,user());
        if(t.get("publishedAt")!=null)throw new BizException("已发布任务的内容与次数要求已冻结，只能延期或结束");
        if(id(t,"classId")!=input.classId())throw new BizException("草稿不能更换所属班级");
        validate(input);String snapshot;try{snapshot=json.writeValueAsString(input.questions().stream().map(String::trim).toList());}catch(Exception e){throw new BizException("题目格式无效");}
        db.update("UPDATE teaching_task SET title=?,description=?,job_id=?,questions_json=?,duration_seconds=?,difficulty=?,min_attempts=?,max_attempts=?,allow_late=?,start_time=?,deadline=?,version=version+1 WHERE id=?",input.title().trim(),input.description(),input.jobId(),snapshot,input.durationSeconds(),input.difficulty(),input.minAttempts(),input.maxAttempts(),input.allowLate(),input.startTime(),input.deadline(),taskId);
        db.update("DELETE FROM teaching_assignment WHERE task_id=?",taskId);allocate(taskId,input);saveScorePlan(taskId,input.scoreModules());
    }
    @Transactional public void discard(long taskId){teacher();var t=owned(TASK_SELECT+"WHERE t.id=? AND c.teacher_id=? FOR UPDATE",taskId,user());if(t.get("publishedAt")!=null)throw new BizException("只能删除未发布草稿，已发布任务与历史记录需保留");db.update("DELETE FROM teaching_assignment WHERE task_id=?",taskId);db.update("DELETE FROM teaching_score_plan WHERE task_id=?",taskId);db.update("DELETE FROM teaching_task WHERE id=?",taskId);}
    public List<String> questions(Map<String,Object> task){try{return json.readValue(String.valueOf(task.get("questionsJson")),new TypeReference<List<String>>(){});}catch(Exception e){throw new BizException("任务题目快照损坏");}}
    private Map<String,Object> enrichTask(Map<String,Object> task){task.put("scorePlan",scorePlan(id(task,"id")));task.put("questions",questions(task));task.remove("questionsJson");LocalDateTime now=LocalDateTime.now();task.put("lifecycle",task.get("publishedAt")==null?"DRAFT":task.get("endedAt")!=null?"ENDED":!date(task,"deadline").isAfter(now)?"CLOSED":date(task,"startTime").isAfter(now)?"SCHEDULED":"ACTIVE");return task;}
    public List<Map<String,Object>> tasks(){teacher();return rows(TASK_SELECT+"WHERE c.teacher_id=? ORDER BY t.id DESC",user()).stream().map(this::enrichTask).toList();}
    public List<Map<String,Object>> tasks(boolean includeAssignments){var all=tasks();if(includeAssignments){var published=all.stream().filter(t->t.get("publishedAt")!=null).toList();var grouped=batchAssignments(published);for(var t:published)t.put("assignments",grouped.getOrDefault(id(t,"id"),List.of()));}return all;}
    public Map<String,Object> task(long taskId){var t=enrichTask(ownTask(taskId));t.put("assignments",assignmentsForTask(taskId));return t;}
    public List<Map<String,Object>> assignmentsForTask(long taskId){return batchAssignments(List.of(ownTask(taskId))).getOrDefault(taskId,List.of());}

    /** Batch the list view; individual student operations still use locking and their own resource checks. */
    private Map<Long,List<Map<String,Object>>> batchAssignments(List<Map<String,Object>> taskRows){
        var result=new HashMap<Long,List<Map<String,Object>>>();
        var rules=new HashMap<Long,Map<String,Object>>();
        for(var task:taskRows){long taskId=id(task,"id");rules.put(taskId,task);result.put(taskId,new ArrayList<>());}
        var ids=new ArrayList<>(rules.keySet());
        for(int offset=0;offset<ids.size();offset+=100){
            var chunk=ids.subList(offset,Math.min(offset+100,ids.size()));
            String placeholders=String.join(",",Collections.nCopies(chunk.size(),"?"));
            Object[] ownerArgs=new Object[chunk.size()+1];ownerArgs[0]=user();for(int i=0;i<chunk.size();i++)ownerArgs[i+1]=chunk.get(i);
            var assignments=rows("SELECT a.*,COALESCE(NULLIF(u.nickname,''),u.username) name,u.username FROM teaching_assignment a JOIN teaching_task t ON t.id=a.task_id JOIN teaching_class c ON c.id=t.class_id JOIN sys_user u ON u.id=a.student_id WHERE c.teacher_id=? AND a.task_id IN ("+placeholders+") ORDER BY a.id",ownerArgs);
            if(assignments.isEmpty())continue;
            var assignmentIds=assignments.stream().map(a->id(a,"id")).toList();
            var attemptByAssignment=new HashMap<Long,List<Map<String,Object>>>();
            for(int start=0;start<assignmentIds.size();start+=200){
                var part=assignmentIds.subList(start,Math.min(start+200,assignmentIds.size()));
                String marks=String.join(",",Collections.nCopies(part.size(),"?"));
                for(var attempt:rows("SELECT * FROM teaching_attempt WHERE assignment_id IN ("+marks+") ORDER BY id DESC",part.toArray()))
                    attemptByAssignment.computeIfAbsent(id(attempt,"assignmentId"),ignored->new ArrayList<>()).add(attempt);
            }
            var overrideByAssignment=new HashMap<Long,Map<String,Object>>();
            for(int start=0;start<assignmentIds.size();start+=200){
                var part=assignmentIds.subList(start,Math.min(start+200,assignmentIds.size()));
                String marks=String.join(",",Collections.nCopies(part.size(),"?"));
                for(var override:rows("SELECT * FROM teaching_assignment_override WHERE assignment_id IN ("+marks+")",part.toArray()))
                    overrideByAssignment.put(id(override,"assignmentId"),override);
            }
            var memberStates=new HashMap<Long,String>();
            for(var member:rows("SELECT a.id assignment_id,m.state FROM teaching_assignment a JOIN teaching_task t ON t.id=a.task_id JOIN teaching_class c ON c.id=t.class_id LEFT JOIN teaching_member m ON m.class_id=c.id AND m.student_id=a.student_id WHERE c.teacher_id=? AND a.task_id IN ("+placeholders+")",ownerArgs))
                memberStates.put(id(member,"assignmentId"),(String)member.get("state"));
            for(var assignment:assignments){long assignmentId=id(assignment,"id"),taskId=id(assignment,"taskId");
                result.get(taskId).add(progressFrom(assignment,rules.get(taskId),attemptByAssignment.getOrDefault(assignmentId,List.of()),memberStates.get(assignmentId),overrideByAssignment.getOrDefault(assignmentId,Map.of())));
            }
        }
        return result;
    }

    private Map<String,Object> progress(Map<String,Object> a){
        var attempts=rows("SELECT * FROM teaching_attempt WHERE assignment_id=? ORDER BY id DESC",id(a,"id"));
        var rule=one(TASK_SELECT+"WHERE t.id=?",id(a,"taskId"));
        var member=rows("SELECT state FROM teaching_member WHERE class_id=? AND student_id=?",id(rule,"classId"),id(a,"studentId"));
        var overrides=rows("SELECT * FROM teaching_assignment_override WHERE assignment_id=?",id(a,"id"));
        var override=overrides.isEmpty()?Map.<String,Object>of():overrides.get(0);
        return progressFrom(a,rule,attempts,member.isEmpty()?null:(String)member.get(0).get("state"),override);
    }
    private Map<String,Object> progressFrom(Map<String,Object> a,Map<String,Object> rule,List<Map<String,Object>> attempts,String memberState,Map<String,Object> override){
        long valid=attempts.stream().filter(r->flag(r,"valid")&&"READY".equals(r.get("state"))).count();
        boolean exempt=flag(override,"exempt");long extra=override.get("extraAttempts")==null?0:id(override,"extraAttempts");
        LocalDateTime deadline=override.get("deadline")==null?date(rule,"deadline"):date(override,"deadline");
        a.put("memberState",memberState==null?"REMOVED":memberState);a.put("attempts",attempts);a.put("validCount",valid);
        a.put("exempt",exempt);a.put("extraAttempts",extra);a.put("overrideReason",override.getOrDefault("reason",""));a.put("effectiveMaxAttempts",id(rule,"maxAttempts")+extra);a.put("effectiveDeadline",deadline);
        a.put("completionStatus",exempt?"EXEMPT":valid>=id(rule,"minAttempts")?"COMPLETED":attempts.stream().anyMatch(r->"GENERATING".equals(r.get("state")))?"PENDING_VALIDATION":attempts.isEmpty()?"NOT_STARTED":"IN_PROGRESS");
        String reason=null;
        if(exempt)reason="教师已减免本任务："+override.get("reason");
        else if(!"JOINED".equals(a.get("memberState")))reason="已不属于该班级，历史训练与报告保留";
        else if(flag(rule,"archived")||rule.get("endedAt")!=null)reason="任务已结束，不再允许继续训练，历史结果保留";
        else if(date(rule,"startTime").isAfter(LocalDateTime.now()))reason="尚未到任务开始时间";
        else if(attempts.stream().anyMatch(r->"ONGOING".equals(r.get("state"))))reason=null;
        else if(!deadline.isAfter(LocalDateTime.now())&&!flag(rule,"allowLate"))reason="任务已截止，不允许补交";
        else if(attempts.stream().anyMatch(r->"GENERATING".equals(r.get("state"))))reason="上一份报告正在生成，请稍后刷新";
        else if(attempts.size()>=id(rule,"maxAttempts")+extra)reason="训练次数已耗尽，请联系教师安排补练";
        a.put("startEligibility",Map.of("allowed",reason==null,"reason",reason==null?"":reason));return a;
    }
    public List<Map<String,Object>> mine(){student();return rows("SELECT a.id,a.task_id,a.student_id FROM teaching_assignment a JOIN teaching_task t ON t.id=a.task_id WHERE a.student_id=? AND t.published_at IS NOT NULL ORDER BY a.id DESC",user()).stream().map(a->{progress(a);a.put("task",enrichTask(one(TASK_SELECT+"WHERE t.id=?",id(a,"taskId"))));return a;}).toList();}
    public Map<String,Object> assignment(long assignmentId){student();var a=one("SELECT * FROM teaching_assignment WHERE id=? AND student_id=?",assignmentId,user());progress(a);a.put("task",enrichTask(one(TASK_SELECT+"WHERE t.id=? AND t.published_at IS NOT NULL",id(a,"taskId"))));return a;}
    @Transactional public void publish(long taskId){teacher();var t=owned(TASK_SELECT+"WHERE t.id=? AND c.teacher_id=? FOR UPDATE",taskId,user());activeClass(t);if(t.get("publishedAt")!=null)return;if(!date(t,"deadline").isAfter(LocalDateTime.now()))throw new BizException("任务已过截止时间，请重新创建");var assigned=rows("SELECT a.student_id FROM teaching_assignment a JOIN teaching_member m ON m.student_id=a.student_id AND m.class_id=? JOIN sys_user u ON u.id=a.student_id WHERE a.task_id=? AND m.state='JOINED' AND u.status=1",id(t,"classId"),taskId);int total=db.queryForObject("SELECT COUNT(*) FROM teaching_assignment WHERE task_id=?",Integer.class,taskId);if(total==0||assigned.size()!=total)throw new BizException("成员名单发生变化，请重新创建任务");db.update("UPDATE teaching_task SET published_at=NOW(),version=version+1 WHERE id=?",taskId);for(var a:assigned){long studentId=id(a,"studentId"),assignmentId=id(one("SELECT id FROM teaching_assignment WHERE task_id=? AND student_id=?",taskId,studentId),"id");message(studentId,"新的教学任务",String.valueOf(t.get("title")),"/my/tasks/"+assignmentId);}}
    @Transactional public void extend(long taskId,DeadlineInput input){var t=ownTask(taskId);activeClass(t);if(t.get("publishedAt")==null||t.get("endedAt")!=null||!input.deadline().isAfter(date(t,"deadline"))||!input.deadline().isAfter(LocalDateTime.now()))throw new BizException("只能延长已发布且未手动结束任务的截止时间");db.update("UPDATE teaching_task SET deadline=?,version=version+1 WHERE id=?",input.deadline(),taskId);notifyTask(taskId,"任务截止时间延长",input.deadline().toString());}
    @Transactional public void end(long taskId){var t=ownTask(taskId);if(t.get("publishedAt")==null)throw new BizException("草稿不能结束");db.update("UPDATE teaching_task SET ended_at=COALESCE(ended_at,NOW()),version=version+1 WHERE id=?",taskId);notifyTask(taskId,"任务已结束","不再允许开始新的训练，历史结果保留。");}
    @Transactional public void remind(long taskId){var t=ownTask(taskId);activeClass(t);if(t.get("publishedAt")==null||t.get("endedAt")!=null)throw new BizException("只能提醒已发布且未手动结束的任务");for(var a:assignmentsForTask(taskId))if(!flag(a,"exempt")&&!"COMPLETED".equals(a.get("completionStatus")))message(id(a,"studentId"),"教学任务提醒","请查看任务要求并完成训练。","/my/tasks/"+id(a,"id"));}
    private void notifyTask(long taskId,String title,String text){for(var a:rows("SELECT * FROM teaching_assignment WHERE task_id=?",taskId))message(id(a,"studentId"),title,text,"/my/tasks/"+id(a,"id"));}
    private void message(long userId,String title,String body,String link){insert("INSERT INTO teaching_message(user_id,title,body,link) VALUES(?,?,?,?)",userId,title,body,link);}
    public List<Map<String,Object>> messages(){return rows("SELECT * FROM teaching_message WHERE user_id=? ORDER BY id DESC LIMIT 200",user());}
    public void readMessage(long messageId){db.update("UPDATE teaching_message SET is_read=TRUE WHERE id=? AND user_id=?",messageId,user());}

    /** Called inside InterviewFlowService.start transaction; serializes attempts per allocation. */

    public Map<String,Object> prepareStart(long assignmentId){
        student();var a=progress(one("SELECT * FROM teaching_assignment WHERE id=? AND student_id=? FOR UPDATE",assignmentId,user()));
        var t=one(TASK_SELECT+"WHERE t.id=?",id(a,"taskId"));if(t.get("publishedAt")==null)throw new BizException("任务尚未发布");
        var eligibility=(Map<String,Object>)a.get("startEligibility");if(!Boolean.TRUE.equals(eligibility.get("allowed")))throw new BizException(String.valueOf(eligibility.get("reason")));
        t.put("effectiveDeadline",a.get("effectiveDeadline"));
        for(var attempt:(List<Map<String,Object>>)a.get("attempts"))if("ONGOING".equals(attempt.get("state"))){t.put("resumeSessionId",id(attempt,"sessionId"));break;}
        return t;
    }
    public void attach(long assignmentId,long sessionId,Map<String,Object> task){db.update("INSERT INTO teaching_attempt(assignment_id,session_id,deadline_snapshot) VALUES(?,?,?)",assignmentId,sessionId,task.get("effectiveDeadline"));}
    public Map<String,Object> sessionTask(long sessionId){var list=rows(TASK_SELECT+"JOIN teaching_assignment a ON a.task_id=t.id JOIN teaching_attempt p ON p.assignment_id=a.id WHERE p.session_id=?",sessionId);return list.isEmpty()?null:list.get(0);}
    public boolean teachingSession(long sessionId){return db.queryForObject("SELECT COUNT(*) FROM teaching_attempt WHERE session_id=?",Integer.class,sessionId)>0;}
    public void lockSession(long sessionId){var a=rows("SELECT state FROM teaching_attempt WHERE session_id=? FOR UPDATE",sessionId);if(!a.isEmpty()&&!"ONGOING".equals(a.get(0).get("state")))throw new BizException("训练已提交，不能再修改回答");}
    public Map<String,Object> reportState(long sessionId){var states=rows("SELECT p.state,p.report_id,p.failure_reason FROM teaching_attempt p JOIN teaching_assignment a ON a.id=p.assignment_id WHERE p.session_id=? AND a.student_id=?",sessionId,user());return states.isEmpty()?null:states.get(0);}
    @Transactional public boolean submitted(long sessionId){var t=sessionTask(sessionId);if(t==null)return true;var p=one("SELECT * FROM teaching_attempt WHERE session_id=? FOR UPDATE",sessionId);if(!"ONGOING".equals(p.get("state")))return false;var allocation=progress(one("SELECT * FROM teaching_assignment WHERE id=?",id(p,"assignmentId")));LocalDateTime effectiveDeadline=date(allocation,"effectiveDeadline");int answers=db.queryForObject("SELECT COUNT(DISTINCT round_no) FROM interview_message WHERE session_id=? AND role='CANDIDATE' AND msg_type='ANSWER' AND LENGTH(TRIM(content))>0",Integer.class,sessionId);boolean valid=answers>=questions(t).size()&&t.get("endedAt")==null&&!flag(t,"archived")&&!flag(allocation,"exempt")&&"JOINED".equals(allocation.get("memberState"))&&(LocalDateTime.now().isBefore(effectiveDeadline)||flag(t,"allowLate"));db.update("UPDATE teaching_attempt SET state='GENERATING',valid=?,submitted_at=NOW(),deadline_snapshot=? WHERE session_id=?",valid,effectiveDeadline,sessionId);return true;}
    @Transactional public void ready(long sessionId,long reportId){var attempts=rows("SELECT p.*,a.student_id,a.task_id,c.teacher_id FROM teaching_attempt p JOIN teaching_assignment a ON a.id=p.assignment_id JOIN teaching_task t ON t.id=a.task_id JOIN teaching_class c ON c.id=t.class_id WHERE p.session_id=? FOR UPDATE",sessionId);if(attempts.isEmpty()||!"GENERATING".equals(attempts.get(0).get("state")))return;var a=attempts.get(0);db.update("UPDATE teaching_attempt SET state=?,report_id=?,failure_reason=? WHERE session_id=?",flag(a,"valid")?"READY":"INVALID",reportId,flag(a,"valid")?null:"未完整回答指定问题，或提交时已截止/结束",sessionId);message(id(a,"studentId"),"任务报告已生成",flag(a,"valid")?"本次训练已计入有效次数。":"本次结果保留，但不计入有效次数。","/my/tasks/"+id(a,"assignmentId"));message(id(a,"teacherId"),"学生任务报告已生成","请查看原始报告并点评。","/teacher/reports/"+reportId);}
    public void failed(long sessionId){db.update("UPDATE teaching_attempt SET state='FAILED',failure_reason='报告生成失败，请重试' WHERE session_id=? AND state='GENERATING'",sessionId);}
    public void retry(long sessionId){student();one("SELECT p.id FROM teaching_attempt p JOIN teaching_assignment a ON a.id=p.assignment_id WHERE p.session_id=? AND a.student_id=? AND p.state='FAILED'",sessionId,user());if(db.update("UPDATE teaching_attempt SET state='GENERATING',failure_reason=NULL WHERE session_id=? AND state='FAILED'",sessionId)!=1)throw new BizException("报告状态已更新，请刷新");}
    public boolean canReadReport(long reportId){if(!Set.of("TEACHER","ADMIN").contains(String.valueOf(UserContext.getRole())))return false;return db.queryForObject("SELECT COUNT(*) FROM teaching_attempt p JOIN teaching_assignment a ON a.id=p.assignment_id JOIN teaching_task t ON t.id=a.task_id JOIN teaching_class c ON c.id=t.class_id WHERE p.report_id=? AND c.teacher_id=?",Integer.class,reportId,user())>0;}
    public void reportAccess(long reportId){if(!canReadReport(reportId)&&db.queryForObject("SELECT COUNT(*) FROM interview_report WHERE id=? AND user_id=?",Integer.class,reportId,user())==0)throw new ForbiddenException("无权查看该报告");}
    public List<Map<String,Object>> reviews(long reportId){reportAccess(reportId);return rows("SELECT r.*,COALESCE(NULLIF(u.nickname,''),u.username) teacher_name FROM teaching_review r JOIN sys_user u ON u.id=r.teacher_id WHERE r.report_id=? ORDER BY r.id",reportId);}
    @Transactional public void review(long reportId,ReviewInput input){teacher();if(!canReadReport(reportId))throw new ForbiddenException("无权点评该报告");insert("INSERT INTO teaching_review(report_id,teacher_id,text) VALUES(?,?,?)",reportId,user(),input.text().trim());var a=one("SELECT a.student_id,a.id FROM teaching_attempt p JOIN teaching_assignment a ON a.id=p.assignment_id WHERE p.report_id=?",reportId);message(id(a,"studentId"),"收到教师点评","请在任务报告中查看教师反馈。","/my/tasks/"+id(a,"id"));}
    public List<Map<String,Object>> reportMessages(long reportId){reportAccess(reportId);return rows("SELECT m.id,m.role,m.msg_type,m.round_no,m.content,m.ability_tag FROM interview_message m JOIN interview_report r ON r.session_id=m.session_id WHERE r.id=? ORDER BY m.id",reportId);}
    public Map<String,Object> summary(){
        teacher();
        long studentTotal=((Number)rows("SELECT COUNT(DISTINCT m.student_id) student_total FROM teaching_member m " +
                "JOIN teaching_class c ON c.id=m.class_id WHERE c.teacher_id=? AND m.state='JOINED'",user()).get(0).get("studentTotal")).longValue();
        long taskTotal=((Number)rows("SELECT COUNT(*) task_total FROM teaching_task t " +
                "JOIN teaching_class c ON c.id=t.class_id WHERE c.teacher_id=?",user()).get(0).get("taskTotal")).longValue();
        var counts=rows("SELECT COALESCE(SUM(CASE WHEN exempt=FALSE THEN 1 ELSE 0 END),0) assignment_total," +
                "COALESCE(SUM(CASE WHEN exempt=FALSE AND valid_count>=min_attempts THEN 1 ELSE 0 END),0) completed_total," +
                "COALESCE(SUM(valid_count),0) valid_attempt_total FROM (" +
                "SELECT a.id,t.min_attempts,COALESCE(o.exempt,FALSE) exempt," +
                "SUM(CASE WHEN p.state='READY' AND p.valid=TRUE THEN 1 ELSE 0 END) valid_count " +
                "FROM teaching_assignment a JOIN teaching_task t ON t.id=a.task_id " +
                "JOIN teaching_class c ON c.id=t.class_id " +
                "LEFT JOIN teaching_assignment_override o ON o.assignment_id=a.id " +
                "LEFT JOIN teaching_attempt p ON p.assignment_id=a.id " +
                "WHERE c.teacher_id=? AND t.published_at IS NOT NULL " +
                "GROUP BY a.id,t.min_attempts,o.exempt) scoped",user()).get(0);
        var pendingRows=rows("SELECT COUNT(DISTINCT p.report_id) pending_total FROM teaching_attempt p " +
                "JOIN teaching_assignment a ON a.id=p.assignment_id JOIN teaching_task t ON t.id=a.task_id " +
                "JOIN teaching_class c ON c.id=t.class_id WHERE c.teacher_id=? AND t.published_at IS NOT NULL " +
                "AND p.report_id IS NOT NULL AND NOT EXISTS " +
                "(SELECT 1 FROM teaching_review r WHERE r.report_id=p.report_id)",user());
        long pending=pendingRows.isEmpty()?0:((Number)pendingRows.get(0).get("pendingTotal")).longValue();
        return Map.of("studentTotal",studentTotal,"taskTotal",taskTotal,
                "assignmentTotal",((Number)counts.get("assignmentTotal")).longValue(),
                "completedTotal",((Number)counts.get("completedTotal")).longValue(),
                "validAttemptTotal",((Number)counts.get("validAttemptTotal")).longValue(),
                "pendingReviewTotal",pending);
    }
}
