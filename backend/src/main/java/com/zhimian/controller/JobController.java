package com.zhimian.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhimian.common.Result;
import com.zhimian.entity.JobPosition;
import com.zhimian.mapper.JobPositionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 岗位接口
 */
@RestController
@RequestMapping("/api/job")
@RequiredArgsConstructor
public class JobController {

    private final JobPositionMapper jobMapper;

    /** 岗位列表（仅启用的） */
    @GetMapping("/list")
    public Result<List<JobPosition>> list() {
        List<JobPosition> jobs = jobMapper.selectList(
                new LambdaQueryWrapper<JobPosition>().eq(JobPosition::getStatus, 1));
        return Result.success(jobs);
    }

    /** 岗位详情 */
    @GetMapping("/{id}")
    public Result<JobPosition> detail(@PathVariable Long id) {
        return Result.success(jobMapper.selectById(id));
    }
}
