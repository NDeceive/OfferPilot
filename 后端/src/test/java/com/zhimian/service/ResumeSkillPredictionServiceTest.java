package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.service.ai.ResumeDeepSeekClient;
import com.zhimian.service.ai.DeepSeekClient;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResumeSkillPredictionServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private ResumeAnalysisService.Snapshot snapshot(String tags) {
        return new ResumeAnalysisService.Snapshot(1L,"Java 后端","Java、SQL",null,"FILE",1L,"简历",
                Map.of("raw","参与接口开发", "skillTags",tags),"hash");
    }
    private final List<ResumeSkillPredictionService.Candidate> bank = List.of(
            new ResumeSkillPredictionService.Candidate(17L,"什么是线程安全？","需要考虑共享状态、原子性与可见性。","Java"),
            new ResumeSkillPredictionService.Candidate(18L,"如何排查慢 SQL？","先看执行计划。","SQL"));

    @Test void selectionKeepsBankQuestionAndIdInsteadOfModelInventedQuestion() throws Exception {
        var response=json.readTree("""
            {"questions":[{"id":18,"question":"模型擅自改写的题目","answerOutline":"先看执行计划与索引使用情况。"},
            {"id":17,"answerOutline":"从共享状态、原子性和可见性展开。"}]}
            """);
        var items=ResumeSkillPredictionService.selectedItems(response,bank,snapshot("Java\nSQL"),Set.of("technical_base"));
        assertEquals("如何排查慢 SQL？",items.get(0).question());
        assertTrue(items.get(0).reason().contains("18"));
        assertEquals("technical_base",items.get(0).moduleCode());
        assertEquals("Java\nSQL",items.get(0).quote());
        assertTrue(items.stream().allMatch(i->i.title().startsWith("技能题")));
    }
    @Test void hallucinatedDuplicateMalformedAndEmptySelectionsAreRejected() throws Exception {
        for(String value:List.of("{\"questions\":[{\"id\":999,\"answerOutline\":\"说明原理\"}]}",
                "{\"questions\":[{\"id\":17,\"answerOutline\":\"说明原理\"},{\"id\":17,\"answerOutline\":\"重复\"}]}",
                "{\"questions\":[{\"id\":17.4,\"answerOutline\":\"说明原理\"}]}",
                "{\"questions\":[{\"id\":17,\"answerOutline\":\"\"}]}", "{\"questions\":[]}")) {
            var response=json.readTree(value);
            assertThrows(IllegalArgumentException.class,()->ResumeSkillPredictionService.selectedItems(response,bank,snapshot("Java"),Set.of()));
        }
    }
    @Test void noTagsOrCandidatesDoesNotAskDeepSeekToInventQuestions() {
        var ai=mock(ResumeDeepSeekClient.class);
        var service=spy(new ResumeSkillPredictionService(null,json,ai));
        assertTrue(service.predict(snapshot(""),Set.of()).items().isEmpty());
        doReturn(List.of()).when(service).candidates(List.of("Java"));
        assertTrue(service.predict(snapshot("Java"),Set.of()).notice().contains("题库暂无"));
        verifyNoInteractions(ai);
    }
    @Test void deepSeekReceivesConfirmedTagsAndOnlyCandidatesMatchingThoseTags() throws Exception {
        var ai=mock(ResumeDeepSeekClient.class);
        when(ai.isUsable()).thenReturn(true);
        var selection=json.readTree("{\"questions\":[{\"id\":17,\"answerOutline\":\"从共享状态、原子性和可见性展开。\"}]}");
        when(ai.chatJsonDetailed(anyString(),anyString())).thenReturn(new DeepSeekClient.JsonCallResult(selection,DeepSeekClient.JsonFailure.NONE));
        var service=spy(new ResumeSkillPredictionService(null,json,ai));
        doReturn(List.of(bank.get(0))).when(service).candidates(List.of("Java"));
        var result=service.predict(snapshot("Java"),Set.of());
        assertEquals(1,result.items().size());assertTrue(result.notice().contains("不足3道"));
        verify(ai).chatJsonDetailed(contains("不得新增题目"),argThat(input->{
            try {var data=json.readTree(input);return data.path("skillTags").get(0).asText().equals("Java")
                    && data.path("candidates").size()==1 && data.path("candidates").get(0).path("id").asLong()==17;}
            catch(Exception e){return false;}
        }));
    }
    @Test void deepSeekFailureIsVisibleAndNeverReplacedByGenericGeneratedQuestion() {
        var ai=mock(ResumeDeepSeekClient.class);when(ai.isUsable()).thenReturn(true);
        var service=spy(new ResumeSkillPredictionService(null,json,ai));
        doReturn(bank).when(service).candidates(List.of("Java"));
        var result=service.predict(snapshot("Java"),Set.of());
        assertTrue(result.items().isEmpty());assertTrue(result.notice().contains("未完成"));
    }
    @Test void exactlyThreeQuestionsAreRequiredWhenBankHasEnoughCandidates() throws Exception {
        var enough=new ArrayList<>(bank);
        enough.add(new ResumeSkillPredictionService.Candidate(19L,"Redis 如何持久化？","比较 RDB 和 AOF。","Redis"));
        enough.add(new ResumeSkillPredictionService.Candidate(20L,"如何理解索引？","解释索引结构。","SQL"));
        var response=json.readTree("{\"questions\":[{\"id\":17,\"answerOutline\":\"说明线程安全。\"},{\"id\":18,\"answerOutline\":\"说明执行计划。\"},{\"id\":19,\"answerOutline\":\"比较两种持久化方案。\"}]}");
        assertEquals(3,ResumeSkillPredictionService.selectedItems(response,enough,snapshot("Java\nSQL\nRedis"),Set.of()).size());
        ((com.fasterxml.jackson.databind.node.ArrayNode)response.path("questions")).remove(2);
        assertThrows(IllegalArgumentException.class,()->ResumeSkillPredictionService.selectedItems(response,enough,snapshot("Java"),Set.of()));
    }
}
