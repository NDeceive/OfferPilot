package com.zhimian.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhimian.common.Result;
import com.zhimian.entity.SkillTag;
import com.zhimian.mapper.SkillTagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 技能标签接口：返回所有可用标签供前端选择。
 */
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class SkillTagController {

    private final SkillTagMapper skillTagMapper;

    /** 获取所有可用技能标签（按分类 + 排序号排列） */
    @GetMapping
    public Result<List<SkillTag>> list() {
        List<SkillTag> tags = skillTagMapper.selectList(
                new LambdaQueryWrapper<SkillTag>()
                        .orderByAsc(SkillTag::getSortOrder)
                        .orderByAsc(SkillTag::getName));
        return Result.success(tags);
    }
}
