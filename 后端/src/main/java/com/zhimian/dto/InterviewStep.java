package com.zhimian.dto;

import lombok.Data;

/**
 * 面试推进结果。
 * nextAction: FOLLOWUP 追问 / NEXT 可进入下一题 / FINISHABLE 可结束面试。
 *  - FOLLOWUP 时 followupQuestion 为追问内容；
 *  - NEXT 由 /next 接口返回下一题时填充 question。
 */
@Data
public class InterviewStep {

    private String nextAction;
    private QuestionView question;
    private String followupQuestion;
}
