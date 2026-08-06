package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.dto.ResumeSaveRequest;
import com.zhimian.entity.Resume;
import com.zhimian.service.ResumeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 简历接口：保存并分析、查询当前用户简历
 */
@RestController
@RequestMapping("/api/resume")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    /** 保存简历并返回分析后的个人画像 */
    @PostMapping
    public Result<Resume> save(@Valid @RequestBody ResumeSaveRequest req) {
        return Result.success(resumeService.saveAndAnalyze(req.getRawText()));
    }

    /** 查询当前用户简历 */
    @GetMapping("/mine")
    public Result<Resume> mine() {
        return Result.success(resumeService.getMine());
    }

    /** 更新当前用户的技能标签（前端增删标签后同步回简历画像） */
    @PutMapping("/tags")
    public Result<Void> updateTags(@RequestBody Map<String, List<String>> body) {
        List<String> tags = body.get("tags");
        resumeService.updateTags(tags != null ? tags : List.of());
        return Result.success(null);
    }
}
