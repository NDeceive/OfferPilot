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
    private String jobName;
    private BigDecimal totalScore;
    private String summary;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> suggestions;
    private String weakTags;
    private List<ReportDimensionView> dimensions;
}
