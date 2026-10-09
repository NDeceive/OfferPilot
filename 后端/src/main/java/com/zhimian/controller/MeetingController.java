package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.config.RequireRole;
import com.zhimian.service.MeetingService;
import com.zhimian.ws.MeetingSignalHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 企业端会议接口：建会 / 进会预检 / 参会记录 / 建议。
 * 实时音视频信令走 /ws/meeting（见 {@link MeetingSignalHandler}），本类只做落库与推送触发。
 * 细粒度越权（是否会议发起人 / 参会者）在 MeetingService 内按登录用户判断。
 */
@Slf4j
@RestController
@RequestMapping("/api/meeting")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;
    private final MeetingSignalHandler signalHandler;

    /** 开启会议（企业/教师/管理员）→ 返回会议号 */
    @PostMapping
    @RequireRole({"ENTERPRISE", "TEACHER", "ADMIN"})
    public Result<Map<String, Object>> create(@Valid @RequestBody MeetingService.CreateInput input) {
        return Result.success(meetingService.create(input));
    }

    /** 进会预检：任意登录用户凭会议号确认可进入性（不含建议内容） */
    @GetMapping("/code/{code}")
    public Result<Map<String, Object>> preview(@PathVariable String code) {
        return Result.success(meetingService.preview(code));
    }

    /** 我发起 / 我参与的会议 */
    @GetMapping("/mine")
    public Result<Map<String, Object>> mine() {
        return Result.success(meetingService.mine());
    }

    /** 会议详情（含建议，学生端已按定向过滤） */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable long id) {
        return Result.success(meetingService.detail(id));
    }

    /** 结束会议：置 ENDED 并实时通知房内所有人 */
    @PostMapping("/{id}/end")
    public Result<Void> end(@PathVariable long id) {
        meetingService.end(id);
        signalHandler.pushMeetingEnded(id);
        return Result.success();
    }

    /** 建议列表 */
    @GetMapping("/{id}/advice")
    public Result<List<Map<String, Object>>> adviceList(@PathVariable long id) {
        return Result.success(meetingService.adviceList(id));
    }

    /** 写建议（企业/教师/管理员）：落库 + WS 广播 */
    @PostMapping("/{id}/advice")
    @RequireRole({"ENTERPRISE", "TEACHER", "ADMIN"})
    public Result<Map<String, Object>> postAdvice(@PathVariable long id,
                                                  @Valid @RequestBody MeetingService.AdviceInput input) {
        Map<String, Object> advice = meetingService.postAdvice(id, input);
        signalHandler.pushAdvice(id, advice);
        return Result.success(advice);
    }
}
