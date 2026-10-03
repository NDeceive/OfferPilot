package com.zhimian.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhimian.config.UserContext;
import com.zhimian.dto.ResumeAnalysis;
import com.zhimian.dto.ResumeFileProfileResponse;
import com.zhimian.entity.ResumeFile;
import com.zhimian.entity.ResumeParagraph;
import com.zhimian.entity.ResumeSkill;
import com.zhimian.mapper.ResumeFileMapper;
import com.zhimian.mapper.ResumeParagraphMapper;
import com.zhimian.mapper.ResumeSkillMapper;
import com.zhimian.service.impl.RuleBasedResumeAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeFileService {

    private final ResumeFileMapper resumeFileMapper;
    private final ResumeParagraphMapper resumeParagraphMapper;
    private final ResumeSkillMapper resumeSkillMapper;
    private final ResumeAnalyzer resumeAnalyzer;
    private final ResumeService resumeService;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    @Transactional
    public ResumeFileProfileResponse uploadAndAnalyze(MultipartFile file) {
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new com.zhimian.common.BizException("简历文件不能超过 10 MB");
        }
        if (file.isEmpty()) {
            throw new RuntimeException("文件不能为空");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new RuntimeException("仅支持 PDF、DOC、DOCX 格式");
        }

        Long userId = UserContext.getUserId();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败", e);
        }

        // Extract text using Apache Tika
        String rawText;
        try (InputStream in = new java.io.ByteArrayInputStream(bytes)) {
            rawText = new Tika().parseToString(in);
        } catch (Exception e) {
            throw new RuntimeException("文件解析失败，请确认文件未加密且为文字版本", e);
        }

        if (rawText == null || rawText.trim().length() < 30) {
            throw new RuntimeException("无法识别文件中的文字，请确保简历为文字版本而非扫描图片");
        }

        // Normalize text to avoid excessive paragraph breaks
        rawText = normalizeText(rawText);

        // Delete old data for this user
        LambdaQueryWrapper<ResumeFile> fileW = new LambdaQueryWrapper<ResumeFile>().eq(ResumeFile::getUserId, userId);
        resumeFileMapper.delete(fileW);

        LambdaQueryWrapper<ResumeParagraph> paraW = new LambdaQueryWrapper<ResumeParagraph>().eq(ResumeParagraph::getUserId, userId);
        resumeParagraphMapper.delete(paraW);

        LambdaQueryWrapper<ResumeSkill> skillW = new LambdaQueryWrapper<ResumeSkill>().eq(ResumeSkill::getUserId, userId);
        resumeSkillMapper.delete(skillW);

        // Save file to table 1
        ResumeFile rf = new ResumeFile();
        rf.setUserId(userId);
        rf.setFilename(file.getOriginalFilename());
        rf.setFileSize(file.getSize());
        rf.setContentType(contentType);
        rf.setFileData(bytes);
        resumeFileMapper.insert(rf);

        // Segment text and save to table 2
        List<ResumeParagraph> paragraphs = segmentAndClassify(rawText, userId, rf.getId());
        for (ResumeParagraph p : paragraphs) {
            resumeParagraphMapper.insert(p);
        }

        // Extract skills and save to table 3
        ResumeAnalysis analysis = resumeAnalyzer.analyze(rawText);
        List<ResumeSkill> skills = new ArrayList<>();
        for (String skillName : analysis.getSkills()) {
            ResumeSkill s = new ResumeSkill();
            s.setUserId(userId);
            s.setFileId(rf.getId());
            s.setSkillName(skillName);
            skills.add(s);
        }
        try {
            for (ResumeSkill s : skills) {
                resumeSkillMapper.insert(s);
            }
        } catch (Exception e) {
            // duplicate key is fine — skip and continue
            log.debug("Duplicate skill skipped", e);
        }

        // Also populate the existing resume table so the text-paste flow has data
        resumeService.saveAndAnalyze(rawText);

        // Build response — normalize newlines to avoid segmented look
        ResumeFileProfileResponse resp = new ResumeFileProfileResponse();
        resp.setRawText(normalizeText(rawText));
        resp.setSkills(analysis.getSkills());
        resp.setProjects(analysis.getProjects());
        resp.setFilename(file.getOriginalFilename());
        resp.setFileId(rf.getId());
        return resp;
    }

    public ResumeFileProfileResponse getFileProfile() {
        Long userId = UserContext.getUserId();
        ResumeFile file = resumeFileMapper.selectOne(new LambdaQueryWrapper<ResumeFile>()
                .eq(ResumeFile::getUserId, userId).last("LIMIT 1"));
        var resume = resumeService.getMine();
        ResumeFileProfileResponse response = new ResumeFileProfileResponse();
        response.setRawText(resume == null ? "" : resume.getRawText());
        response.setFilename(file != null ? file.getFilename() : resume != null ? "在线简历" : null);
        response.setFileId(file == null ? null : file.getId());
        response.setSkills(resume == null ? List.of() : readList(resume.getSkills()));
        response.setProjects(resume == null ? List.of() : readList(resume.getProjects()));
        return response;
    }

    private List<String> readList(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(value,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (IOException e) { return List.of(); }
    }

    private List<ResumeParagraph> segmentAndClassify(String fullText, Long userId, Long fileId) {
        String[] rawParagraphs = fullText.split("\\n\\s*\\n");
        List<ResumeParagraph> result = new ArrayList<>();
        int seq = 1;
        for (String raw : rawParagraphs) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) continue;

            ResumeParagraph p = new ResumeParagraph();
            p.setUserId(userId);
            p.setFileId(fileId);
            p.setSeqNo(seq++);
            p.setContent(trimmed.length() > 500 ? trimmed.substring(0, 500) + "…" : trimmed);

            boolean isProject = false;
            Matcher m = RuleBasedResumeAnalyzer.PROJECT_SPLIT.matcher(trimmed);
            if (m.find()) {
                isProject = true;
            }
            p.setParagraphType(isProject ? "PROJECT" : "GENERAL");
            result.add(p);
        }
        return result;
    }

    private static String normalizeText(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s*\\n\\s*\\n\\s*", "\n").replace('\r', '\n').replaceAll("\\n{3,}", "\n\n");
    }
}
