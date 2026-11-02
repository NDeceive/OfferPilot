package com.zhimian.service;

import com.zhimian.entity.InterviewMessage;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InterviewPlanServiceTest {
    private InterviewMessage message(int round,String role,String type,String stage) {
        var m=new InterviewMessage();m.setRoundNo(round);m.setRole(role);m.setMsgType(type);m.setQuestionType(stage);return m;
    }
    @Test void unansweredFollowupsDoNotCompleteCoreQuestionsAndSkipsStayDistinct() {
        var intro=message(1,"INTERVIEWER","MAIN",InterviewStageService.INTRO);
        var introduction=message(1,"CANDIDATE","ANSWER",InterviewStageService.INTRO);
        var main=message(2,"INTERVIEWER","MAIN",InterviewStageService.PROFESSIONAL);
        var answer=message(2,"CANDIDATE","ANSWER",InterviewStageService.PROFESSIONAL);
        var followup=message(2,"INTERVIEWER","FOLLOWUP",InterviewStageService.PROFESSIONAL);
        assertEquals(new InterviewPlanService.Counts(1,0,1,0),InterviewPlanService.count(List.of(intro,introduction,main,answer,followup)));
        var skipped=message(2,"CANDIDATE","SKIPPED",InterviewStageService.PROFESSIONAL);
        var closing=message(2,"INTERVIEWER","SUMMARY","COMPLETED");
        assertEquals(new InterviewPlanService.Counts(2,1,1,0),InterviewPlanService.count(List.of(intro,introduction,main,answer,followup,skipped,closing)));
    }
    @Test void estimateExtendsWithSlowProgressAndFinishesWithCompletedQuestions() {
        assertTrue(InterviewPlanService.estimate(600,650,2,6,1800)>650);
        assertTrue(InterviewPlanService.estimate(600,30,2,6,1800)<600);
        assertEquals(90,InterviewPlanService.estimate(600,90,6,6,1800));
        assertEquals(1800,InterviewPlanService.estimate(600,1700,1,6,1800));
    }
}
