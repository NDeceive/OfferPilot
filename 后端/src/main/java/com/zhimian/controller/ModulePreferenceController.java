package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.entity.ScoreModule;
import com.zhimian.service.ModulePreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模块偏好接口：模块字典查询、偏好存取。
 */
@RestController
@RequestMapping("/api/modules")
@RequiredArgsConstructor
public class ModulePreferenceController {

    private final ModulePreferenceService preferenceService;

    /** 获取全部10个评价模块字典 */
    @GetMapping
    public Result<List<ScoreModule>> listModules() {
        return Result.success(preferenceService.listModules());
    }
}
