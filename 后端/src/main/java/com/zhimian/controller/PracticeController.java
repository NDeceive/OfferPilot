package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.service.PracticeService;
import com.zhimian.service.PracticeBankService;
import com.zhimian.service.CompanyQuestionService;
import com.zhimian.config.RequireRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/practice")
@RequiredArgsConstructor
public class PracticeController {
    private final PracticeService practice;
    private final PracticeBankService bank;
    private final CompanyQuestionService companyQuestions;
    private final com.zhimian.service.PracticeWorkflowService workflow;

    @GetMapping("/catalog") public Result<Map<String,Object>> catalog() { return Result.success(bank.catalog()); }
    @RequireRole({"TEACHER","ADMIN"})
    @PostMapping("/company-candidates") public Result<Map<String,Object>> generate(@Valid @RequestBody CompanyQuestionService.GenerateInput input) { return Result.success(companyQuestions.generate(input)); }
    @RequireRole({"TEACHER","ADMIN"})
    @GetMapping("/company-candidates") public Result<java.util.List<Map<String,Object>>> candidates() { return Result.success(companyQuestions.candidates()); }
    @RequireRole({"TEACHER","ADMIN"})
    @PostMapping("/company-candidates/{id}/review") public Result<Map<String,Object>> review(@PathVariable long id,@Valid @RequestBody CompanyQuestionService.ReviewInput input) { return Result.success(companyQuestions.review(id,input)); }

    @GetMapping("/overview") public Result<Map<String, Object>> overview() { return Result.success(practice.overview()); }
    @GetMapping("/sessions") public Result<Map<String,Object>> sessions(@RequestParam(defaultValue="ALL") String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return Result.success(practice.sessions(status,page,size)); }
    @GetMapping("/library/{kind}") public Result<Map<String,Object>> library(@PathVariable String kind,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return Result.success(practice.personalQuestions(kind,page,size)); }
    @PostMapping("/sessions") public Result<Map<String, Object>> create(@Valid @RequestBody PracticeService.StartInput input) { return Result.success(practice.create(input)); }
    @GetMapping("/sessions/{sessionId}") public Result<Map<String, Object>> session(@PathVariable long sessionId) { return Result.success(practice.session(sessionId)); }
    @PostMapping("/sessions/{sessionId}/questions/{questionId}/answers")
    public Result<Map<String, Object>> answer(@PathVariable long sessionId, @PathVariable long questionId, @Valid @RequestBody PracticeService.AnswerInput input) { return Result.success(practice.answer(sessionId, questionId, input)); }
    @PostMapping("/sessions/{sessionId}/questions/{questionId}/run")
    public Result<Map<String, Object>> run(@PathVariable long sessionId, @PathVariable long questionId, @Valid @RequestBody PracticeService.CodeInput input) { return Result.success(practice.run(sessionId, questionId, input)); }
    @PostMapping("/sessions/{sessionId}/questions/{questionId}/submit")
    public Result<Map<String, Object>> submitCode(@PathVariable long sessionId, @PathVariable long questionId, @Valid @RequestBody PracticeService.CodeInput input) { return Result.success(practice.submitCode(sessionId, questionId, input)); }
    @PostMapping("/sessions/{sessionId}/finish") public Result<Map<String, Object>> finish(@PathVariable long sessionId) { return Result.success(workflow.finish(sessionId)); }
    @GetMapping("/judge/capability") public Result<Map<String,Object>> capability() { return Result.success(workflow.capability()); }
    @PostMapping("/sessions/{sessionId}/questions/{questionId}/tasks") public Result<Map<String,Object>> createTask(@PathVariable long sessionId,@PathVariable long questionId,@Valid @RequestBody com.zhimian.service.PracticeWorkflowService.TaskInput input) { return Result.success(workflow.createTask(sessionId,questionId,input)); }
    @GetMapping("/tasks/{taskId}") public Result<Map<String,Object>> task(@PathVariable long taskId) { return Result.success(workflow.task(taskId)); }
    @GetMapping("/sessions/{sessionId}/tasks") public Result<java.util.List<Map<String,Object>>> tasks(@PathVariable long sessionId) { return Result.success(workflow.tasks(sessionId)); }
    @GetMapping("/sessions/{sessionId}/report") public Result<Map<String,Object>> report(@PathVariable long sessionId) { return Result.success(workflow.report(sessionId)); }
    @GetMapping("/sessions/{sessionId}/questions/{questionId}/submissions") public Result<Map<String,Object>> submissions(@PathVariable long sessionId,@PathVariable long questionId,@RequestParam(defaultValue="1") int page) { return Result.success(workflow.submissions(sessionId,questionId,page)); }
    @PostMapping("/sessions/{sessionId}/retry") public Result<Map<String,Object>> retry(@PathVariable long sessionId,@RequestParam(defaultValue="false") boolean failedOnly) { return Result.success(workflow.retry(sessionId,failedOnly)); }
    @PutMapping("/questions/{questionId}/review") public Result<Map<String,Object>> review(@PathVariable long questionId,@Valid @RequestBody com.zhimian.service.PracticeWorkflowService.ReviewInput input) { return Result.success(workflow.review(questionId,input.state())); }
    @PostMapping("/questions/{questionId}/favorite") public Result<Map<String, Object>> favorite(@PathVariable long questionId) { return Result.success(practice.toggleFavorite(questionId)); }
    @DeleteMapping("/questions/{questionId}/favorite") public Result<Map<String,Object>> removeFavorite(@PathVariable long questionId) { return Result.success(practice.removeFavorite(questionId)); }
}
