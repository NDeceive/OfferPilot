package com.zhimian.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhimian.common.BizException;
import com.zhimian.common.Result;
import com.zhimian.config.UserContext;
import com.zhimian.dto.AnswerRequest;
import com.zhimian.dto.FollowUpRequest;
import com.zhimian.dto.FollowUpResponse;
import com.zhimian.dto.InterviewRecord;
import com.zhimian.dto.InterviewStartResponse;
import com.zhimian.dto.InterviewStep;
import com.zhimian.dto.MessageView;
import com.zhimian.dto.StartInterviewRequest;
import com.zhimian.entity.InterviewMessage;
import com.zhimian.entity.InterviewSession;
import com.zhimian.mapper.InterviewMessageMapper;
import com.zhimian.mapper.InterviewSessionMapper;
import com.zhimian.service.FollowUpService;
import com.zhimian.service.InterviewFlowService;
import com.zhimian.service.InterviewRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 面试接口（会话 / 记录 / 规则化面试流程）。
 */
@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewRecordService recordService;
    private final InterviewFlowService flowService;
    private final FollowUpService followUpService;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;

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

    /** 结束面试：会话置为 FINISHED，异步生成报告，立即返回 sessionId */
    @PostMapping("/{sessionId}/finish")
    public Result<Long> finish(@PathVariable Long sessionId) {
        return Result.success(flowService.finish(sessionId));
    }

    /** 查询报告生成状态：前端轮询此接口，ready=true 时拿到 reportId 跳转 */
    @GetMapping("/{sessionId}/report-status")
    public Result<java.util.Map<String, Object>> reportStatus(@PathVariable Long sessionId) {
        boolean ready = flowService.isReportReady(sessionId);
        Long reportId = ready ? flowService.getReadyReportId(sessionId) : null;
        return Result.success(java.util.Map.of("ready", ready, "reportId", reportId != null ? reportId : 0));
    }

    /**
     * 动态追问（Phase 3.1）：根据岗位 / 原始问题 / 回答生成一个追问。
     * 优先调用 DeepSeek（source=AI），失败或回答过短时回退规则化兜底（source=RULE）。
     * 无状态接口，不依赖会话与题库。
     */
    @PostMapping("/follow-up")
    public Result<FollowUpResponse> followUp(@Valid @RequestBody FollowUpRequest req) {
        return Result.success(followUpService.generate(req));
    }

    /**
     * 获取某一个面试会话的全部问答消息（按时间顺序）。
     * 返回 INTERVIEWER 和 CANDIDATE 的所有消息，供前端展示 Q&A 历史。
     */
    @GetMapping("/{sessionId}/messages")
    public Result<List<MessageView>> messages(@PathVariable Long sessionId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(UserContext.getUserId())) {
            throw new BizException("无权查看该会话");
        }

        List<InterviewMessage> msgs = messageMapper.selectList(
                new LambdaQueryWrapper<InterviewMessage>()
                        .eq(InterviewMessage::getSessionId, sessionId)
                        .orderByAsc(InterviewMessage::getId));

        List<MessageView> views = msgs.stream().map(m -> {
            MessageView v = new MessageView();
            v.setId(m.getId());
            v.setSessionId(m.getSessionId());
            v.setQuestionId(m.getQuestionId());
            v.setRoundNo(m.getRoundNo());
            v.setRole(m.getRole());
            v.setMsgType(m.getMsgType());
            v.setQuestionType(m.getQuestionType());
            v.setContent(m.getContent());
            v.setAbilityTag(m.getAbilityTag());
            v.setCreateTime(m.getCreateTime());
            return v;
        }).collect(Collectors.toList());
        return Result.success(views);
    }

    /** 删除面试会话及其关联数据。仅允许删除本人会话。 */
    @DeleteMapping("/{sessionId}")
    public Result<Void> delete(@PathVariable Long sessionId) {
        flowService.delete(sessionId);
        return Result.success(null);
    }
}
