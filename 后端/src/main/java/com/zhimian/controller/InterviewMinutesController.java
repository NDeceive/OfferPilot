package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.service.InterviewMinutesService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/interview/{sessionId}/minutes")
@RequiredArgsConstructor
public class InterviewMinutesController {
    private final InterviewMinutesService minutes;
    public record Control(@NotNull Boolean enabled,@NotNull @Min(0) Long expectedVersion,@NotBlank @Size(max=80) String requestId){}
    public record Generate(@NotBlank @Size(max=80) String requestId){}
    @GetMapping public Result<?> state(@PathVariable long sessionId){return Result.success(minutes.state(sessionId));}
    @PutMapping("/control") public Result<?> control(@PathVariable long sessionId,@Valid @RequestBody Control input){return Result.success(minutes.control(sessionId,input.enabled(),input.expectedVersion(),input.requestId()));}
    @GetMapping("/entries") public Result<?> entries(@PathVariable long sessionId,@RequestParam(defaultValue="0") long afterSequence,@RequestParam(defaultValue="50") int limit){return Result.success(minutes.entries(sessionId,afterSequence,limit));}
    @GetMapping("/summary") public Result<?> summary(@PathVariable long sessionId){return Result.success(minutes.summary(sessionId));}
    @PostMapping("/summary-jobs") public Result<?> generate(@PathVariable long sessionId,@Valid @RequestBody Generate input){return Result.success(minutes.regenerate(sessionId,input.requestId()));}
    @DeleteMapping public Result<?> delete(@PathVariable long sessionId){minutes.delete(sessionId);return Result.success();}
    @GetMapping("/export") public ResponseEntity<byte[]> export(@PathVariable long sessionId,@RequestParam String kind,@RequestParam(defaultValue="md") String format){
        String content=minutes.export(sessionId,kind,format);
        return ResponseEntity.ok().contentType(new MediaType("text","plain",StandardCharsets.UTF_8))
                .header(HttpHeaders.CACHE_CONTROL,"no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("面试"+sessionId+"-"+kind+"."+format,StandardCharsets.UTF_8).build().toString())
                .body(content.getBytes(StandardCharsets.UTF_8));
    }
}
