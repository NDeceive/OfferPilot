package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.BizException;
import com.zhimian.dto.StartInterviewRequest;
import com.zhimian.entity.JobPosition;
import com.zhimian.entity.Resume;
import com.zhimian.mapper.JobPositionMapper;
import com.zhimian.mapper.ResumeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@DependsOn("studentBackendSchema")
@RequiredArgsConstructor
public class InterviewSnapshotService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final JobPositionMapper jobs;
    private final ResumeMapper resumes;

    /** Caller holds the account row lock until the new session commits. */
    public Long existing(long userId,StartInterviewRequest request) {
        if(request.getRequestId()==null || request.getRequestId().isBlank())return null;
        jdbc.queryForList("SELECT id FROM sys_user WHERE id=? FOR UPDATE",userId);
        var rows=jdbc.queryForList("SELECT session_id,request_hash FROM student_interview_snapshot WHERE user_id=? AND start_request_id=?",userId,request.getRequestId());
        if(rows.isEmpty())return null;
        if(!hash(request).equals(rows.get(0).get("request_hash")))throw new BizException("请求编号已用于另一项训练配置");
        return ((Number)rows.get(0).get("session_id")).longValue();
    }

    public void capture(long sessionId,long userId,StartInterviewRequest request,JobPosition job,Resume resume) {
        try {
            jdbc.update("INSERT INTO student_interview_snapshot(session_id,user_id,start_request_id,request_hash,job_json,resume_json) VALUES (?,?,?,?,?,?)",
                    sessionId,userId,request.getRequestId()==null||request.getRequestId().isBlank()?null:request.getRequestId(),
                    hash(request),json.writeValueAsString(job),resume==null?null:json.writeValueAsString(resume));
        }catch(com.fasterxml.jackson.core.JsonProcessingException failure){throw new IllegalStateException(failure);}
    }

    public JobPosition job(long sessionId,long jobId) {
        var rows=jdbc.queryForList("SELECT job_json FROM student_interview_snapshot WHERE session_id=?",String.class,sessionId);
        if(rows.isEmpty())return jobs.selectById(jobId);
        try{return json.readValue(rows.get(0),JobPosition.class);}catch(Exception failure){throw new IllegalStateException(failure);}
    }

    public Resume resume(long sessionId,Long legacyResumeId) {
        var rows=jdbc.query("SELECT resume_json FROM student_interview_snapshot WHERE session_id=?",(rs,n)->rs.getString(1),sessionId);
        if(rows.isEmpty())return legacyResumeId==null?null:resumes.selectById(legacyResumeId);
        if(rows.get(0)==null)return null;
        try{return json.readValue(rows.get(0),Resume.class);}catch(Exception failure){throw new IllegalStateException(failure);}
    }

    private String hash(Object value) {
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(value)));}
        catch(Exception failure){throw new IllegalStateException(failure);}
    }
}
