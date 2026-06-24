package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.config.UserContext;
import com.zhimian.dto.ResumeAnalysis;
import com.zhimian.entity.Resume;
import com.zhimian.mapper.ResumeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 简历服务：保存简历并分析生成个人画像，查询当前用户简历。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeMapper resumeMapper;
    private final ResumeAnalyzer resumeAnalyzer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 保存/更新当前用户简历（一人一份，存在则更新），并返回分析后的画像。
     */
    public Resume saveAndAnalyze(String rawText) {
        Long userId = UserContext.getUserId();
        ResumeAnalysis analysis = resumeAnalyzer.analyze(rawText);

        Resume resume = resumeMapper.selectOne(
                new LambdaQueryWrapper<Resume>().eq(Resume::getUserId, userId).last("LIMIT 1"));
        boolean isNew = (resume == null);
        if (isNew) {
            resume = new Resume();
            resume.setUserId(userId);
        }
        resume.setRawText(rawText);
        resume.setSkills(toJson(analysis.getSkills()));
        resume.setKeywords(toJson(analysis.getKeywords()));
        resume.setProjects(toJson(analysis.getProjects()));

        if (isNew) {
            resumeMapper.insert(resume);
        } else {
            resumeMapper.updateById(resume);
        }
        return resume;
    }

    /** 查询当前用户简历，没有则返回 null */
    public Resume getMine() {
        return resumeMapper.selectOne(
                new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getUserId, UserContext.getUserId())
                        .last("LIMIT 1"));
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("简历字段序列化失败", e);
            return "[]";
        }
    }
}
