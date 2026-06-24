package com.zhimian.dto;

import lombok.Data;

import java.util.List;

/**
 * 简历分析结果
 */
@Data
public class ResumeAnalysis {
    /** 技能关键词 */
    private List<String> skills;
    /** 画像关键词（技能 + 其他高频技术词） */
    private List<String> keywords;
    /** 项目经历段落 */
    private List<String> projects;
}
