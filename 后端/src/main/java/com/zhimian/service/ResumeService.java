package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.config.UserContext;
import com.zhimian.dto.ResumeAnalysis;
import com.zhimian.entity.Resume;
import com.zhimian.mapper.ResumeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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

    /** 更新当前用户的技能标签（前端增删标签后同步回简历画像） */
    public void updateTags(List<String> tags) {
        Long userId = UserContext.getUserId();
        Resume resume = resumeMapper.selectOne(
                new LambdaQueryWrapper<Resume>().eq(Resume::getUserId, userId).last("LIMIT 1"));
        if (resume == null) return;
        List<String> safeTags = (tags == null) ? Collections.emptyList() : tags;

        resume.setSkills(toJson(safeTags));
        // keywords 必须跟着一起收窄。出题读的是 skills + keywords 的并集
        // （InterviewFlowService#extractTagsFromResume），只改 skills 的话，
        // 用户删掉的标签会从 keywords 里原样"复活"——删了等于没删。
        // 这里取交集而不是直接覆盖成 tags：万一将来分析器往 keywords 里放 skill 之外的东西，
        // 也不至于因为用户点了一下标签就把它整片抹掉。
        resume.setKeywords(toJson(intersect(parseJsonList(resume.getKeywords()), safeTags)));

        resumeMapper.updateById(resume);
    }

    /** 保留 to 里也有的项，顺序沿用 from */
    private List<String> intersect(List<String> from, List<String> to) {
        return from.stream().filter(to::contains).collect(Collectors.toList());
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
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
