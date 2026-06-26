package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.dto.FollowupRecordPageResponse;
import com.zhimian.dto.FollowupRecordStats;
import com.zhimian.service.InterviewFollowupRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 追问记录查询接口（Phase 3.3，实验分析 / 论文写作用）。
 * <p>
 * 仅提供只读查询，不触碰面试主流程；与 {@link InterviewController} 分离，
 * 因此不会影响 /answer 与 /follow-up 等既有接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/interview/follow-up-records")
@RequiredArgsConstructor
public class InterviewFollowupRecordController {

    private final InterviewFollowupRecordService followupRecordService;

    /**
     * 分页查询追问记录，按 id 倒序（最新在前）。
     * 所有过滤参数均可选；无记录时返回空列表 + total=0，不报错。
     *
     * @param pageNo    页码，默认 1
     * @param pageSize  每页条数，默认 10（服务层上限 100）
     * @param source    可选，AI / RULE
     * @param sessionId 可选，按会话过滤
     * @param jobId     可选，按岗位过滤
     */
    @GetMapping
    public Result<FollowupRecordPageResponse> list(
            @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "10") Long pageSize,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Long jobId) {
        log.info("[追问记录查询接口] pageNo={}, pageSize={}, source={}, sessionId={}, jobId={}",
                pageNo, pageSize, source, sessionId, jobId);
        return Result.success(
                followupRecordService.pageQuery(pageNo, pageSize, source, sessionId, jobId));
    }

    /**
     * 追问记录统计：AI / RULE 数量与占比。
     * 无记录时各计数为 0、占比为 0。
     */
    @GetMapping("/stats")
    public Result<FollowupRecordStats> stats() {
        log.info("[追问记录统计接口] 触发统计查询");
        return Result.success(followupRecordService.stats());
    }
}
