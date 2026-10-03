package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.service.TeachingService;
import com.zhimian.service.ReportService;
import com.zhimian.service.InterviewFlowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/teaching")
@RequiredArgsConstructor
public class TeachingController {
    private final TeachingService teaching;
    private final ReportService reports;
    private final InterviewFlowService flow;
    @GetMapping("/classes") public Result<?> classes(){return Result.success(teaching.classes());}
    @PostMapping("/classes") public Result<?> create(@Valid @RequestBody TeachingService.ClassInput input){return Result.success(teaching.createClass(input));}
    @PutMapping("/classes/{id}") public Result<?> edit(@PathVariable long id,@Valid @RequestBody TeachingService.ClassInput input){teaching.editClass(id,input);return Result.success(null);}
    @PostMapping("/classes/join") public Result<?> join(@Valid @RequestBody TeachingService.JoinInput input){teaching.join(input);return Result.success(null);}
    @PostMapping("/classes/{id}/invite") public Result<?> invite(@PathVariable long id){teaching.resetInvite(id);return Result.success(null);}
    @PostMapping("/classes/{id}/archive") public Result<?> archive(@PathVariable long id){teaching.archive(id);return Result.success(null);}
    @GetMapping("/classes/{id}/members") public Result<?> members(@PathVariable long id){return Result.success(teaching.members(id));}
    @PutMapping("/classes/{id}/members/{memberId}/{state}") public Result<?> membership(@PathVariable long id,@PathVariable long memberId,@PathVariable String state){teaching.membership(id,memberId,state);return Result.success(null);}
    @GetMapping("/students") public Result<?> students(){return Result.success(teaching.people());}
    @GetMapping("/summary") public Result<?> summary(){return Result.success(teaching.summary());}
    @GetMapping("/tasks") public Result<?> tasks(@RequestParam(defaultValue="false") boolean includeAssignments){return Result.success(teaching.tasks(includeAssignments));}
    @PostMapping("/tasks") public Result<?> createTask(@Valid @RequestBody TeachingService.TaskInput input){return Result.success(teaching.createTask(input));}
    @PutMapping("/tasks/{id}") public Result<?> editTask(@PathVariable long id,@Valid @RequestBody TeachingService.TaskInput input){teaching.editTask(id,input);return Result.success(null);}
    @DeleteMapping("/tasks/{id}") public Result<?> discard(@PathVariable long id){teaching.discard(id);return Result.success(null);}
    @GetMapping("/tasks/{id}") public Result<?> task(@PathVariable long id){return Result.success(teaching.task(id));}
    @PostMapping("/tasks/{id}/supplement") public Result<?> supplement(@PathVariable long id,@Valid @RequestBody TeachingService.SupplementInput input){teaching.supplement(id,input);return Result.success(null);}
    @PutMapping("/tasks/{id}/assignments/{assignmentId}") public Result<?> override(@PathVariable long id,@PathVariable long assignmentId,@Valid @RequestBody TeachingService.OverrideInput input){teaching.override(id,assignmentId,input);return Result.success(null);}
    @PostMapping("/tasks/{id}/publish") public Result<?> publish(@PathVariable long id){teaching.publish(id);return Result.success(null);}
    @PutMapping("/tasks/{id}/deadline") public Result<?> deadline(@PathVariable long id,@Valid @RequestBody TeachingService.DeadlineInput input){teaching.extend(id,input);return Result.success(null);}
    @PostMapping("/tasks/{id}/end") public Result<?> end(@PathVariable long id){teaching.end(id);return Result.success(null);}
    @PostMapping("/tasks/{id}/remind") public Result<?> remind(@PathVariable long id){teaching.remind(id);return Result.success(null);}
    @GetMapping("/assignments") public Result<?> mine(){return Result.success(teaching.mine());}
    @GetMapping("/assignments/{id}") public Result<?> assignment(@PathVariable long id){return Result.success(teaching.assignment(id));}
    @PostMapping("/sessions/{id}/retry") public Result<?> retry(@PathVariable long id){flow.retryTeachingReport(id);return Result.success(null);}
    @GetMapping("/reports/{id}") public Result<?> report(@PathVariable long id){teaching.reportAccess(id);return Result.success(Map.of("report",reports.getDetail(id),"messages",teaching.reportMessages(id),"reviews",teaching.reviews(id)));}
    @GetMapping("/reports/{id}/reviews") public Result<?> reviews(@PathVariable long id){return Result.success(teaching.reviews(id));}
    @PostMapping("/reports/{id}/reviews") public Result<?> review(@PathVariable long id,@Valid @RequestBody TeachingService.ReviewInput input){teaching.review(id,input);return Result.success(null);}
    @GetMapping("/messages") public Result<?> messages(){return Result.success(teaching.messages());}
    @PutMapping("/messages/{id}/read") public Result<?> read(@PathVariable long id){teaching.readMessage(id);return Result.success(null);}
}
