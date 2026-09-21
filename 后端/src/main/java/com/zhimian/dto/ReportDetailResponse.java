package com.zhimian.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 报告详情响应（GET /api/report/{reportId}）。
 * strengths/weaknesses/suggestions 在库里以 JSON 数组字符串存储，
 * 出参时解析为字符串列表，前端可直接渲染。
 */
@Data
public class ReportDetailResponse {
    private Long reportId;
    private Long sessionId;
    private Long jobId;
    private String jobName;
    private BigDecimal totalScore;
    private String summary;
    private java.time.LocalDateTime startTime;
    private java.time.LocalDateTime endTime;
    private Integer durationSeconds;
    private Long actualDurationSeconds;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> suggestions;
    private String weakTags;
    private List<ReportDimensionView> dimensions;

    // 评分系统改造新增
    /** 整体匹配度(0-120+) */
    private BigDecimal overallMatchScore;
    /** 匹配度等级 */
    private String displayLevel;
    /** 匹配画像标签 */
    private String profileLabel;
    /** 模块评分明细（新版才有） */
    private List<ModuleScoreView> moduleScores;
}
