package com.zhimian.service;

import com.zhimian.dto.ResumeAnalysis;

/**
 * 简历分析器：从简历原文中提取技能、关键词、项目经历。
 * <p>
 * 当前为本地词典+规则实现（无需大模型）。后续接入 DeepSeek 后，
 * 可新增一个基于 LLM 的实现类，通过配置切换，调用方无需改动。
 */
public interface ResumeAnalyzer {

    ResumeAnalysis analyze(String rawText);
}
