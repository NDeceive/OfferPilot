package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.dto.AnswerRequest;
import com.zhimian.dto.InterviewRecord;
import com.zhimian.dto.InterviewStartResponse;
import com.zhimian.dto.InterviewStep;
import com.zhimian.dto.StartInterviewRequest;
import com.zhimian.service.InterviewFlowService;
import com.zhimian.service.InterviewRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 面试接口（会话 / 记录 / 规则化面试流程）。
 */
@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewRecordService recordService;
    private final InterviewFlowService flowService;

    /** 当前用户的面试记录列表（真实数据，无记录则为空数组） */
    @GetMapping("/records")
    public Result<List<InterviewRecord>> records() {
        return Result.success(recordService.myRecords());
    }

    /** 开始一次面试：创建会话并返回第一题 */
    @PostMapping("/start")
    public Result<InterviewStartResponse> start(@Valid @RequestBody StartInterviewRequest req) {
        return Result.success(flowService.start(req));
    }

    /** 提交回答：保存回答并按规则决定是否追问 */
    @PostMapping("/{sessionId}/answer")
    public Result<InterviewStep> answer(@PathVariable Long sessionId,
                                        @Valid @RequestBody AnswerRequest req) {
        return Result.success(flowService.answer(sessionId, req));
    }

    /** 获取下一题：没有更多主问题时返回 FINISHABLE */
    @GetMapping("/{sessionId}/next")
    public Result<InterviewStep> next(@PathVariable Long sessionId) {
        return Result.success(flowService.next(sessionId));
    }

    /** 结束面试：会话置为 FINISHED（幂等），报告生成留待 Phase 2 */
    @PostMapping("/{sessionId}/finish")
    public Result<Long> finish(@PathVariable Long sessionId) {
        return Result.success(flowService.finish(sessionId));
    }
}
