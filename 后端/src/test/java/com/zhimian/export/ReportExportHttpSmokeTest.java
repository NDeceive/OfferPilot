package com.zhimian.export;

import com.zhimian.config.JwtUtil;
import com.zhimian.entity.SysUser;
import com.zhimian.mapper.SysUserMapper;
import com.zhimian.service.ReportJobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real 8080 download check; fixtures are scoped to a unique temporary user. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"digital-human.launcher.enabled=false","mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"})
@EnabledIfEnvironmentVariable(named="RUN_EXPORT_HTTP_SMOKE",matches="true")
class ReportExportHttpSmokeTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired SysUserMapper users;
    @Autowired JwtUtil jwt;
    @MockBean ReportJobService reportJobs;

    @Test
    void runningServerDownloadsBothFormatsWithRealStoredData() throws Exception {
        var user=new SysUser();user.setUsername("export-http-"+UUID.randomUUID().toString().substring(0,8));
        user.setPassword("not-a-login-password");user.setRole("STUDENT");user.setStatus(1);
        Long session=null,report=null;
        try {
            users.insert(user);
            Long job=jdbc.queryForObject("SELECT MIN(id) FROM job_position",Long.class);
            jdbc.update("INSERT INTO interview_session(user_id,job_id,status,start_time,end_time,duration_seconds) VALUES (?,?,'FINISHED',DATE_SUB(NOW(),INTERVAL 4 MINUTE),NOW(),240)",user.getId(),job);
            session=jdbc.queryForObject("SELECT id FROM interview_session WHERE user_id=?",Long.class,user.getId());
            jdbc.update("INSERT INTO interview_message(session_id,question_id,round_no,role,msg_type,content) VALUES (?,0,1,'INTERVIEWER','MAIN','说明 HashMap 的实现原理'),(?,0,1,'CANDIDATE','ANSWER','先算哈希定位到桶，通过链表处理冲突。')",session,session);
            jdbc.update("INSERT INTO interview_report(session_id,user_id,total_score,overall_match_score,summary,strengths,weaknesses,suggestions) VALUES (?,?,80,112.5,'本次为导出检查记录','[\"基础概念回答清晰\"]','[]','[\"补充边界条件\"]')",session,user.getId());
            report=jdbc.queryForObject("SELECT id FROM interview_report WHERE user_id=?",Long.class,user.getId());
            var modules=jdbc.queryForList("SELECT code FROM score_module ORDER BY id LIMIT 5");
            for(var module:modules)jdbc.update("INSERT INTO interview_module_score(report_id,module_code,raw_score,target_score,module_match,base_weight,gap_score,improvement_priority,evidence,suggestion) VALUES (?,?,80,75,106.67,0.2,0,0,'导出检查依据','补充边界条件')",report,module.get("code"));
            long start=jdbc.queryForObject("SELECT UNIX_TIMESTAMP(start_time)*1000 FROM interview_session WHERE id=?",Long.class,session);
            for(int i=0;i<3;i++)jdbc.update("INSERT INTO interview_expression_sample(session_id,sample_id,question_id,round_no,captured_at,face_detected,probabilities) VALUES (?,?,0,1,?,1,?)",session,"export-smoke-"+i,start+2000*(i+1),"{\"neutral\":0.76,\"happy\":0.04,\"sad\":0.04,\"angry\":0.04,\"fearful\":0.04,\"disgusted\":0.04,\"surprised\":0.04}");
            String token=jwt.generateToken(user.getId(),user.getUsername(),user.getRole());
            var client=RestClient.create("http://127.0.0.1:8080");
            for(String format:List.of("pdf","docx")) {
                var response=client.get().uri("/api/report/{id}/export?format={format}&includeTranscript=true",report,format).header("Authorization","Bearer "+token).retrieve().toEntity(byte[].class);
                assertNotNull(response.getHeaders().getFirst("Content-Disposition"));assertNotNull(response.getBody());assertTrue(response.getBody().length>1000);
                if(format.equals("pdf"))try(var doc=org.apache.pdfbox.pdmodel.PDDocument.load(response.getBody())) {String text=new org.apache.pdfbox.text.PDFTextStripper().getText(doc);assertTrue(text.contains("112.5%"));assertTrue(text.contains("完整问答复盘"));assertTrue(text.contains("逐题回顾表"));}
                else try(var doc=new org.apache.poi.xwpf.usermodel.XWPFDocument(new java.io.ByteArrayInputStream(response.getBody()))){assertTrue(doc.getAllPictures().size()>=3);assertTrue(doc.getTables().size()>=3);}
            }
        } finally {
            if(report!=null){jdbc.update("DELETE FROM interview_module_score WHERE report_id=?",report);jdbc.update("DELETE FROM interview_report WHERE id=? AND user_id=?",report,user.getId());}
            if(session!=null){jdbc.update("DELETE FROM interview_expression_sample WHERE session_id=?",session);jdbc.update("DELETE FROM interview_message WHERE session_id=?",session);jdbc.update("DELETE FROM interview_session WHERE id=? AND user_id=?",session,user.getId());}
            if(user.getId()!=null)jdbc.update("DELETE FROM sys_user WHERE id=? AND username=?",user.getId(),user.getUsername());
        }
    }
}
