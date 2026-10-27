package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PracticeBankServiceTest {
    JdbcTemplate db=mock(JdbcTemplate.class);
    ObjectMapper json=new ObjectMapper();
    PracticeBankService bank=new PracticeBankService(db,json);
    List<Map<String,Object>> knowledge=new ArrayList<>(),coding=new ArrayList<>(),companies=new ArrayList<>();
    @BeforeEach void fixture() throws Exception {
        var root=json.readTree(Files.readString(Path.of("src/main/resources/practice-bank/pilot.json")));
        long id=1;
        for(var c:root.path("companies"))companies.add(Map.of("id",id++,"name",c.path("name").asText(),"code",c.path("code").asText(),"aliases_json",c.path("aliases").toString(),"evidence_json",c.path("evidence").toString()));
        id=1;
        for(var q:root.path("questions")) {
            var row=new HashMap<String,Object>(); row.put("id",id++);row.put("difficulty",q.path("difficulty").asInt());row.put("canonical_id",q.path("canonicalId").asText());
            row.put("job_codes_json",q.path("jobCodes").toString());row.put("topics_json",q.path("topics").toString());row.put("subtype",q.path("type").asText());
            if(q.has("companyCode"))row.put("company_id",companies.stream().filter(c -> c.get("code").equals(q.path("companyCode").asText())).findFirst().orElseThrow().get("id"));
            (q.path("type").asText().equals("CODING")?coding:knowledge).add(row);
        }
        when(db.queryForList(anyString(),any(Object[].class))).thenAnswer(call -> {
            String sql=call.getArgument(0);
            if(sql.contains("FROM practice_company"))return companies;
            if(sql.contains("JOIN practice_question_meta m ON"))return knowledge;
            if(sql.contains("FROM practice_coding_question"))return coding;
            return List.of();
        });
        when(db.queryForList(anyString())).thenAnswer(call -> {
            String sql=call.getArgument(0);
            if(sql.contains("FROM practice_company"))return companies;
            if(sql.contains("SELECT id,name FROM skill_tag"))return List.of();
            return List.of();
        });
    }
    Map<String,Object> job(String code){return Map.of("code",code,"abilities","[]","keywords","[]");}
    @Test void companyRoleAndTopicFilteringUsesDistinctCanonicalQuestions() {
        var result=bank.select(job("BE-JAVA"),"Tencent","综合高频",1,8,2,10);
        assertEquals(2,result.cultureCount());assertEquals(8,result.knowledge().size());assertEquals(2,result.coding().size());
        assertTrue(result.knowledge().stream().allMatch(q -> bank.list(q.get("job_codes_json")).contains("BE-JAVA")));
        assertTrue(result.knowledge().stream().filter(q -> "CULTURE".equals(q.get("subtype"))).allMatch(q -> Objects.equals(3L,q.get("company_id"))));
        assertEquals(8,result.knowledge().stream().map(q -> q.get("canonical_id")).distinct().count());
        var specific=bank.select(job("BE-JAVA"),"腾讯","JVM",1,3,0,3);
        assertEquals(0,specific.cultureCount());
        assertTrue(specific.knowledge().stream().allMatch(q -> bank.list(q.get("topics_json")).contains("JVM")));
        assertTrue(specific.notice().contains("补齐"));
    }
    @Test void unknownCompanyFallsBackHonestlyAndUnsupportedJobCannotBorrowOtherRoles() {
        var result=bank.select(job("FE-WEB"),"未接入公司","综合高频",1,8,2,10);
        assertEquals(0,result.cultureCount());assertTrue(result.notice().contains("暂无"));
        assertThrows(RuntimeException.class,()->bank.select(job("BE-PY"),"腾讯","综合高频",1,1,0,1));
        assertThrows(RuntimeException.class,()->bank.select(job("FE-WEB"),"腾讯","JVM",1,1,0,1));
    }
    @Test void cultureOnlyAndPureCodingKeepTheirTypesAndRejectInsufficientPools() {
        var culture=bank.select(job("FE-WEB"),"阿里巴巴","企业情境",1,3,0,3);
        assertEquals(3,culture.cultureCount());assertTrue(culture.knowledge().stream().allMatch(q -> "CULTURE".equals(q.get("subtype"))));
        assertThrows(RuntimeException.class,()->bank.select(job("FE-WEB"),"不限公司","企业情境",1,3,0,3));
        var code=bank.select(job("BE-JAVA"),"腾讯","编程与算法",1,0,20,20);
        assertEquals(20,code.coding().size());assertTrue(code.knowledge().isEmpty());
        assertThrows(RuntimeException.class,()->bank.select(job("BE-JAVA"),"腾讯","编程与算法",1,0,21,21));
    }
    @Test void tagMatchingDoesNotConfuseJavaWithJavaScriptOrGoWithDjango() {
        assertFalse(PracticeBankService.containsTag("JavaScript","Java"));assertFalse(PracticeBankService.containsTag("Django","Go"));
        assertTrue(PracticeBankService.containsTag("Java核心","Java"));assertTrue(PracticeBankService.containsTag("Vue / React","React"));
    }
}
