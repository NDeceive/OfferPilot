package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.config.UserContext;
import com.zhimian.service.ai.DeepSeekClient;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CompanyQuestionServiceTest {
    PracticeBankService bank=mock(PracticeBankService.class);DeepSeekClient ai=mock(DeepSeekClient.class);JdbcTemplate db=mock(JdbcTemplate.class);
    CompanyQuestionService service=new CompanyQuestionService(bank,ai,new ObjectMapper(),db);
    CompanyQuestionService.GenerateInput input=new CompanyQuestionService.GenerateInput("腾讯","BE-JAVA","系统设计",2);
    @BeforeEach void setup() {
        UserContext.set(21L,"TEACHER");when(bank.company("腾讯")).thenReturn(Map.of("id",3L,"evidence_json","source"));
        when(bank.object("source")).thenReturn(Map.of("items",List.of(Map.of("id","user","summary","用户价值","url","https://www.tencent.com/who-we-are/"))));
        when(db.queryForList(contains("FROM job_position"),any(Object[].class))).thenReturn(List.of(Map.of("code","BE-JAVA","name","Java")));
        when(ai.isUsable()).thenReturn(true);when(ai.modelName()).thenReturn("configured-model");
    }
    @AfterEach void cleanup(){UserContext.clear();}
    @Test void studentCannotGenerateReviewOrReadCandidates() {
        UserContext.set(21L,"STUDENT");assertThrows(RuntimeException.class,()->service.generate(input));assertThrows(RuntimeException.class,()->service.candidates());
        assertThrows(RuntimeException.class,()->service.review(1,new CompanyQuestionService.ReviewInput(true,"题","内容","解析")));verifyNoInteractions(ai);
    }
    @Test void unavailableAIAndMissingCompanyDoNotFabricateContent() {
        when(ai.isUsable()).thenReturn(false);assertThrows(RuntimeException.class,()->service.generate(input));
        when(bank.company("腾讯")).thenReturn(Map.of());assertThrows(RuntimeException.class,()->service.generate(input));verify(ai,never()).chat(anyString(),anyString());
    }
    @Test void validGenerationIsPendingAndNeverWritesStudentBank() {
        when(ai.chat(anyString(),anyString())).thenReturn("{\"title\":\"协作\",\"content\":\"如何处理跨团队问题\",\"referenceAnswer\":\"说明行动与取舍\",\"evidenceIds\":[\"user\"]}");
        assertEquals("PENDING",service.generate(input).get("status"));
        verify(db).update(contains("INSERT INTO practice_company_candidate"),eq(3L),eq("BE-JAVA"),eq("系统设计"),anyString(),eq(21L));
        verify(db,never()).update(contains("INSERT INTO skill_question"),any(Object[].class));
    }
    @Test void unknownEvidenceAndMalformedModelOutputAreRejected() {
        when(ai.chat(anyString(),anyString())).thenReturn("{\"title\":\"协作\",\"content\":\"问题\",\"referenceAnswer\":\"解析\",\"evidenceIds\":[\"invented\"]}");
        assertThrows(RuntimeException.class,()->service.generate(input));
        when(ai.chat(anyString(),anyString())).thenReturn("not JSON");assertThrows(RuntimeException.class,()->service.generate(input));
        verify(db,never()).update(anyString(),any(Object[].class));
    }
}
