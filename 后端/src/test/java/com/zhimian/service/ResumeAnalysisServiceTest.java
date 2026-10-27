package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.config.UserContext;
import com.zhimian.common.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.zhimian.service.ai.ResumeAiGateway;
import com.zhimian.service.ai.DeepSeekClient;

class ResumeAnalysisServiceTest {
    private final ObjectMapper json=new ObjectMapper();
    private ResumeAnalysisService.Snapshot snapshot() { return new ResumeAnalysisService.Snapshot(1L,"测试岗位","Java",2L,"VERSION",2L,"验收材料",Map.of("name","","phone","","email","","targetJob","","summary","","education","","experience","","projects","参与接口测试。参与接口测试。","skills","Java","awards",""),"hash"); }
    @AfterEach void clear() { UserContext.clear(); }
    @Test void invalidEvidenceGetsOneRepairAndRepairedEvidenceIsStillValidated() throws Exception {
        var ai=mock(ResumeAiGateway.class);
        var service=new ResumeAnalysisService(null,json,null,null,null,null,null,ai);
        var candidate=json.readTree("""
            {"items":[
              {"kind":"IMPROVEMENT","priority":"HIGH","field":"skills","quote":"Java","title":"说明Java使用场景","reason":"原文仅列出技术","action":"准备一个真实案例","requiresFacts":true},
              {"kind":"INTERVIEW","priority":"MEDIUM","field":"skills","quote":"Java","title":"Java技术选择","reason":"核实实际使用","action":"准备技术约束、本人行动和验证方法","question":"你在项目中如何使用Java？","requiresFacts":true}
            ]}
            """);
        var invalid=candidate.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)invalid.path("items").get(0)).put("quote","虚构原文");
        when(ai.chatJsonDetailed(anyString(),contains("ORIGINAL_QUOTE")))
                .thenReturn(new DeepSeekClient.JsonCallResult(candidate,DeepSeekClient.JsonFailure.NONE));
        assertEquals(2,service.validateWithRepair(invalid,snapshot(),Set.of(),"原始输入").size());
        verify(ai,times(1)).chatJsonDetailed(anyString(),contains("ORIGINAL_QUOTE"));
        when(ai.chatJsonDetailed(anyString(),anyString()))
                .thenReturn(new DeepSeekClient.JsonCallResult(invalid,DeepSeekClient.JsonFailure.NONE));
        assertThrows(IllegalArgumentException.class,()->service.validateWithRepair(invalid,snapshot(),Set.of(),"原始输入"));
        verify(ai,times(2)).chatJsonDetailed(anyString(),anyString());
    }
    @Test void duplicateDeletionNeverAddsOrReordersFacts() {
        assertTrue(ResumeAnalysisService.safeDeletion("参与测试。参与测试。","参与测试。"));
        assertFalse(ResumeAnalysisService.safeDeletion("参与测试。","主导测试并提升50%。"));
        assertFalse(ResumeAnalysisService.safeDeletion("参与项目，负责测试。","负责测试。"));
    }
    @Test void invalidQuotesModulesAndInventedSuggestionsAreRejected() throws Exception {
        String valid="""
          {"items":[{"kind":"IMPROVEMENT","priority":"HIGH","field":"education","quote":"","title":"补充教育","reason":"未体现","action":"核实","requiresFacts":true,"suggestion":"","jobEvidence":"","question":"","moduleCode":"project_expression"},
          {"kind":"INTERVIEW","priority":"MEDIUM","field":"skills","quote":"Java","title":"准备追问","reason":"核实场景","action":"准备证据","requiresFacts":true,"suggestion":"","jobEvidence":"","question":"如何使用？","moduleCode":"project_expression"}]}
          """;
        assertEquals(2,ResumeAnalysisService.validate(json.readTree(valid),snapshot(),Set.of("project_expression")).size());
        assertThrows(IllegalArgumentException.class,()->ResumeAnalysisService.validate(json.readTree(valid.replace("\"quote\":\"Java\"","\"quote\":\"虚构经历\"")),snapshot(),Set.of("project_expression")));
        assertThrows(IllegalArgumentException.class,()->ResumeAnalysisService.validate(json.readTree(valid),snapshot(),Set.of("other")));
        assertThrows(IllegalArgumentException.class,()->ResumeAnalysisService.validate(json.readTree(valid.replace("\"suggestion\":\"\"","\"suggestion\":\"提升50%\"")),snapshot(),Set.of("project_expression")));
    }
    @Test void rulesQuotesAreAlwaysTraceableAndDoNotInventSkills() {
        var s=snapshot();var items=ResumeAnalysisService.rules(s,Set.of("project_expression"));
        assertTrue(items.stream().allMatch(i->s.fields().get(i.field()).contains(i.quote())));
        assertTrue(items.stream().allMatch(i->i.moduleCode().isBlank()||i.moduleCode().equals("project_expression")));
        assertTrue(items.stream().anyMatch(i->i.kind().equals("SUGGESTION")));
    }
    @Test void jobGapsUseActualJobKeywordsAndStaySeparateFromGeneralMissingFields() {
        var original=snapshot();
        var s=new ResumeAnalysisService.Snapshot(1L,"测试岗位","测试岗位\n[\"Java\",\"SQL\"]",2L,"VERSION",2L,"验收材料",original.fields(),"hash");
        var items=ResumeAnalysisService.rules(s,Set.of("position_cognition"));
        var gaps=items.stream().filter(i->i.kind().equals("JOB_GAP")).toList();
        assertEquals(1,gaps.size());
        assertEquals("SQL",gaps.get(0).jobEvidence());
        assertTrue(gaps.get(0).requiresFacts());
        assertTrue(items.stream().anyMatch(i->i.kind().equals("STRENGTH") && i.jobEvidence().equals("Java")));
        assertTrue(items.stream().anyMatch(i->i.kind().equals("IMPROVEMENT") && i.field().equals("phone")));
    }
    @Test void interviewPredictionsDescribeQuestionTypesAndFollowResumeEvidence() {
        var original=snapshot();
        var project=ResumeAnalysisService.rules(original,Set.of("project_expression")).stream()
                .filter(i->i.kind().equals("INTERVIEW")).findFirst().orElseThrow();
        assertEquals("经历深挖题 · 个人贡献与结果验证",project.title());
        assertEquals("projects",project.field());
        assertTrue(original.fields().get(project.field()).contains(project.quote()));
        assertTrue(project.reason().contains("可能"));
        assertFalse(project.action().contains("准备"));
        assertFalse(project.question().isBlank());
        var fields=new HashMap<>(original.fields()); fields.put("projects","");
        var skills=new ResumeAnalysisService.Snapshot(original.jobId(),original.jobName(),original.jobEvidence(),original.versionId(),
                original.sourceKind(),original.sourceId(),original.title(),fields,original.fingerprint());
        var technical=ResumeAnalysisService.rules(skills,Set.of("technical_base")).stream()
                .filter(i->i.kind().equals("INTERVIEW")).findFirst().orElseThrow();
        assertEquals("技术原理题 · 技能理解与应用",technical.title());
        assertEquals("skills",technical.field());
        assertEquals("Java",technical.quote());
        assertEquals("technical_base",technical.moduleCode());
        assertFalse(technical.question().contains("项目"));
    }
    @Test void reportOwnershipIsCheckedInDatabaseQuery() {
        UserContext.set(7L,"STUDENT");
        var jdbc=new JdbcTemplate() { @Override public <T> List<T> query(String sql,RowMapper<T> mapper,Object...args) {
            assertTrue(sql.contains("id=? AND user_id=?"));assertEquals(9L,args[0]);assertEquals(7L,args[1]);return List.of();
        }};
        var service=new ResumeAnalysisService(jdbc,json,null,null,null,null,null,null);
        assertThrows(BizException.class,()->service.report(9));
    }
    @Test void fingerprintsChangeWithMutableTextAndJob() {
        assertNotEquals(ResumeAnalysisService.hash("job1:text1"),ResumeAnalysisService.hash("job1:text2"));
        assertNotEquals(ResumeAnalysisService.hash("job1:text1"),ResumeAnalysisService.hash("job2:text1"));
    }
}
