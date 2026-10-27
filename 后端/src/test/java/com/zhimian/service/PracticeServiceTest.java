package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.config.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PracticeServiceTest {
    JdbcTemplate db = mock(JdbcTemplate.class);
    Judge0Client judge = mock(Judge0Client.class);
    PracticeBankService bank = mock(PracticeBankService.class);
    PracticeService service = new PracticeService(db, new ObjectMapper(), judge, bank);
    long nextId = 100;

    @BeforeEach void setup() {
        UserContext.set(21L, "USER");
        when(judge.capability()).thenReturn(Map.of("ready",true));
        when(db.queryForList(anyString(), any(Object[].class))).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("SELECT * FROM practice_session WHERE")) return List.of(Map.of("id", 10L, "job_id", 1L));
            if (sql.contains("SELECT * FROM practice_session_question")) return List.of(Map.of("id", 11L, "question_type", "CODING", "language", "java", "tests_json", "[{\"input\":\"1 2\",\"expected\":\"3\",\"public\":\"true\"}]"));
            return List.of();
        });
        when(db.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(call -> {
            ((KeyHolder) call.getArgument(1)).getKeyList().add(Map.of("id", nextId++)); return 1;
        });
        when(judge.judge(anyString(), anyString(), anyString())).thenReturn(new Judge0Client.Result("token", 3, "Accepted", "3", "", "", "", "0.01", 100));
    }
    @AfterEach void cleanup() { UserContext.clear(); }

    @Test void personalListsValidateInputAndUseLatestSubmissionForCurrentUser() {
        assertThrows(RuntimeException.class, () -> service.sessions("anything",1,20));
        assertThrows(RuntimeException.class, () -> service.sessions("ALL",0,20));
        assertThrows(RuntimeException.class, () -> service.personalQuestions("favorites",1,51));
        assertThrows(RuntimeException.class, () -> service.personalQuestions("unknown",1,20));
        service.personalQuestions("mistakes",2,20);
        verify(db).queryForList(contains("MAX(a2.id)"),eq(21L),eq(20),eq(20));
        verify(db).queryForObject(contains("a.is_correct=0"),eq(Integer.class),eq(21L));
    }

    @Test void overviewSelectsOnlyOngoingSessionForResume() {
        service.overview();
        verify(db).queryForList(contains("status='ONGOING'"),eq(21L));
    }

    @Test void removingFavoriteChecksOwnershipAndIsIdempotent() {
        when(db.queryForList(contains("SELECT q.id FROM"),any(Object[].class))).thenReturn(List.of(Map.of("id",11L)));
        assertEquals(false,service.removeFavorite(11).get("favorite"));
        assertEquals(false,service.removeFavorite(11).get("favorite"));
        verify(db,times(2)).update("DELETE FROM practice_favorite WHERE user_id=? AND session_question_id=?",21L,11L);
        when(db.queryForList(contains("SELECT q.id FROM"),any(Object[].class))).thenReturn(List.of());
        assertThrows(RuntimeException.class, () -> service.removeFavorite(99));
    }

    @Test void runDoesNotPersistOrAdvanceButSubmitCountsEvenWhenIncorrect() {
        var input = new PracticeService.CodeInput("class Main {}", "java");
        var run = service.run(10, 11, input);
        assertEquals(true, run.get("passed"));
        assertNull(run.get("submissionId"));
        verify(db, never()).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
        verify(db, never()).update(anyString(), any(Object[].class));
        when(judge.judge(anyString(), anyString(), anyString())).thenReturn(new Judge0Client.Result("token", 6, "Compilation Error", "", "", "missing symbol", "", "", null));
        var submitted = service.submitCode(10, 11, input);
        assertEquals(false, submitted.get("passed"));
        assertEquals("missing symbol", submitted.get("compileOutput"));
        assertNotNull(submitted.get("submissionId"));
        verify(db).update("UPDATE practice_session_question SET status='ANSWERED' WHERE id=?", 11L);
        verify(db).update(contains("MIN(sequence_no)-1"), eq(10L), eq(10L));
    }

    @Test void unsupportedLanguageIsRejectedBeforeCallingJudge() {
        assertThrows(RuntimeException.class, () -> service.run(10, 11, new PracticeService.CodeInput("code", "python")));
        verifyNoInteractions(judge);
    }

    @Test void selfReviewSubmissionHasNoInventedScoreOrPassConclusion() {
        when(db.queryForList(contains("SELECT * FROM practice_session_question"),any(Object[].class))).thenReturn(List.of(Map.of("id",11L,"question_type","KNOWLEDGE","reference_answer","自评要点")));
        when(db.queryForList(contains("FROM practice_question_detail"),any(Object[].class))).thenReturn(List.of(Map.of("detail_json","self-review")));
        when(bank.object("self-review")).thenReturn(Map.of("gradingMode","SELF_REVIEW"));
        var result=service.answer(10,11,new PracticeService.AnswerInput("先验证数据再沟通行动方案"));
        assertFalse(result.containsKey("score"));assertFalse(result.containsKey("passed"));assertEquals("SELF_REVIEW",result.get("status"));
    }

    @Test void runningUsesExamplesAndOfficialSubmissionRedactsHiddenExpectedOutput() {
        when(db.queryForList(contains("FROM practice_question_detail"),any(Object[].class))).thenReturn(List.of(Map.of("detail_json","examples")));
        when(bank.object("examples")).thenReturn(Map.of("examples",List.of(Map.of("input","public","expected","3"))));
        service.run(10,11,new PracticeService.CodeInput("code","java"));verify(judge).judge("java","code","public");
        when(db.queryForList(contains("SELECT * FROM practice_session_question"),any(Object[].class))).thenReturn(List.of(Map.of("id",11L,"question_type","CODING","language","java","tests_json","[{\"input\":\"secret\",\"expected\":\"3\",\"public\":\"false\"}]")));
        var result=service.submitCode(10,11,new PracticeService.CodeInput("code","java"));
        var cases=(List<Map<String,Object>>)result.get("cases");
        assertEquals(true,cases.get(0).get("hidden"));assertEquals("判题用例不公开",cases.get(0).get("expected"));
    }

    @Test void runtimeLimitsAndLargeOutputKeepTheirActualStatus() {
        for(int id:new int[]{5,7,9}) {
            when(judge.judge(anyString(),anyString(),anyString())).thenReturn(new Judge0Client.Result("token",id,"runtime status","x".repeat(40000),"error","","","0.1",128000));
            var result=service.run(10,11,new PracticeService.CodeInput("code","java"));
            assertEquals(id==5?"TIME_LIMIT_EXCEEDED":"RUNTIME_ERROR",result.get("status"));assertEquals(false,result.get("submitted"));
            assertTrue(((String)result.get("stdout")).length()<33000);
        }
        when(judge.judge(anyString(),anyString(),anyString())).thenReturn(new Judge0Client.Result("token",12,"Internal Error","","","","","",null));
        assertThrows(RuntimeException.class,()->service.submitCode(10,11,new PracticeService.CodeInput("code","java")));
        verify(db,never()).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
    }

    @Test void unavailableJudgeDoesNotWriteSubmissionOrAdvanceProgress() {
        when(judge.judge(anyString(),anyString(),anyString())).thenThrow(new com.zhimian.common.BizException("不可用"));
        assertThrows(RuntimeException.class,()->service.submitCode(10,11,new PracticeService.CodeInput("code","java")));
        verify(db,never()).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
        verify(db,never()).update(anyString(),any(Object[].class));
    }

    @Test void mixedSessionSnapshotsExactlyTheRequestedCount() {
        when(db.queryForList(contains("FROM job_position"),any(Object[].class))).thenReturn(List.of(Map.of("id",1L,"name","Java","code","BE-JAVA")));
        when(bank.select(anyMap(),anyString(),anyString(),anyInt(),anyInt(),anyInt(),anyInt(),eq("ALL"))).thenAnswer(call -> {
            int knowledge=call.getArgument(4),coding=call.getArgument(5);
            Map<String,Object> q=Map.of("id",1L,"title","题目","content","题干","difficulty",1,"examples_json","[]","tests_json","[]");
            return new PracticeBankService.Selection(Collections.nCopies(knowledge,q),Collections.nCopies(coding,q),Map.of(),0,"");
        });
        service.create(new PracticeService.StartInput(1L,"不限公司","综合高频","normal","all",20,"basic"));
        verify(db,times(21)).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
        verify(db,times(20)).update(contains("INSERT INTO practice_question_detail"),anyLong(),anyString());
    }
    @Test void mixedQuotaAlwaysStaysInRangeAndKeepsShortSessionsMixed() {
        for (int total = 7; total <= 50; total++) {
            for (int attempt = 0; attempt < 100; attempt++) {
                int coding = PracticeService.mixedCodingCount(total);
                assertTrue(coding >= 2 && coding >= Math.ceil(total * .2));
                assertTrue(coding <= Math.floor(total * .3));

            }
        }
        for (int total = 2; total < 7; total++) assertEquals(1, PracticeService.mixedCodingCount(total));
        assertThrows(RuntimeException.class, () -> PracticeService.mixedCodingCount(1));


    }

    @Test void rejectedSelectionDoesNotCreateAnEmptySession() {
        when(db.queryForList(contains("FROM job_position"),any(Object[].class))).thenReturn(List.of(Map.of("id",1L,"code","BE-JAVA")));
        when(bank.select(anyMap(),anyString(),anyString(),anyInt(),anyInt(),anyInt(),anyInt(),eq("ALL"))).thenThrow(new com.zhimian.common.BizException("不足"));
        assertThrows(RuntimeException.class,()->service.create(new PracticeService.StartInput(1L,"腾讯","JVM","normal","all",20,"basic")));
        verify(db,never()).update(any(PreparedStatementCreator.class),any(KeyHolder.class));
    }
}
