package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.export.ResumeDocumentExport;
import com.zhimian.service.ResumeClaimAiService;
import com.zhimian.service.ResumeWorkbenchService;
import com.zhimian.service.ai.ResumeAiGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resume-workbench")
@RequiredArgsConstructor
public class ResumeWorkbenchController {
    private final ResumeWorkbenchService workbench;
    private final ResumeDocumentExport exporter;
    private final ResumeClaimAiService claimAi;
    private final ResumeAiGateway resumeAi;

    @GetMapping("/ai-status")
    public Result<ResumeAiGateway.Status> aiStatus() { return Result.success(resumeAi.status()); }

    @GetMapping("/claims")
    public Result<List<ResumeWorkbenchService.ClaimView>> claims() { return Result.success(workbench.claims()); }

    @GetMapping("/claim-suggestions")
    public Result<List<ResumeWorkbenchService.ClaimInput>> claimSuggestions() {
        return Result.success(workbench.suggestionsFromCurrentResume());
    }

    @PostMapping("/claim-example/ai")
    public Result<ResumeClaimAiService.ClaimExample> aiClaimExample(
            @RequestBody ResumeClaimAiService.ExampleInput input) {
        return Result.success(claimAi.generateExample(input));
    }

    @PostMapping("/claim-wording/ai")
    public Result<ResumeClaimAiService.WordingSuggestion> aiClaimWording(
            @RequestBody ResumeClaimAiService.WordingInput input) {
        return Result.success(claimAi.suggestWording(input));
    }

    @PostMapping("/claims")
    public Result<ResumeWorkbenchService.ClaimView> addClaim(@RequestBody ResumeWorkbenchService.ClaimInput input) {
        return Result.success(workbench.addClaim(input));
    }

    @PutMapping("/claims/{id}")
    public Result<ResumeWorkbenchService.ClaimView> updateClaim(@PathVariable long id,
            @RequestBody ResumeWorkbenchService.ClaimInput input) {
        return Result.success(workbench.updateClaim(id, input));
    }

    @DeleteMapping("/claims/{id}")
    public Result<Void> deleteClaim(@PathVariable long id) { workbench.deleteClaim(id); return Result.success(); }

    @GetMapping("/versions")
    public Result<List<ResumeWorkbenchService.VersionSummary>> versions() { return Result.success(workbench.versions()); }

    @GetMapping("/versions/{id}")
    public Result<ResumeWorkbenchService.VersionView> version(@PathVariable long id) {
        return Result.success(workbench.version(id));
    }

    @PostMapping("/versions")
    public Result<ResumeWorkbenchService.VersionView> saveVersion(@RequestBody ResumeWorkbenchService.VersionInput input) {
        return Result.success(workbench.saveVersion(input));
    }

    @PostMapping("/versions/generate")
    public Result<ResumeWorkbenchService.VersionView> generate(@RequestBody ResumeWorkbenchService.GenerateInput input) {
        return Result.success(workbench.generate(input));
    }

    @GetMapping("/review")
    public Result<ResumeWorkbenchService.Review> review(@RequestParam(required = false) Long versionId,
                                                         @RequestParam Long jobId) {
        return Result.success(workbench.review(versionId, jobId));
    }

    @GetMapping("/versions/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable long id, @RequestParam String format) {
        byte[] bytes = exporter.export(id, format);
        MediaType type = "pdf".equals(format) ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resume-" + id + "." + format)
                .body(bytes);
    }
}
