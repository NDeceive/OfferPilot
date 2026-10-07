package com.zhimian.export;

import com.zhimian.dto.ReportDetailResponse;
import com.zhimian.dto.ReportDimensionView;
import com.zhimian.service.ReportService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExportServiceTest {

    @Test
    void exportsValidPdfAndDocxWithoutStartingWord() throws Exception {
        ReportService reportService = mock(ReportService.class);
        ExportService exportService = new ExportService(reportService);

        ReportDimensionView dimension = new ReportDimensionView();
        dimension.setDimension("专业知识");
        dimension.setScore(new BigDecimal("86.5"));
        dimension.setLevel("优秀");
        dimension.setExplanation("基础扎实，能够清晰解释核心概念。");

        ReportDetailResponse report = new ReportDetailResponse();
        report.setReportId(1L);
        report.setSessionId(1L);
        report.setJobName("Java 后端开发");
        report.setTotalScore(new BigDecimal("86.5"));
        report.setSummary("整体表现良好，回答结构清晰。");
        report.setStrengths(List.of("技术基础扎实"));
        report.setWeaknesses(List.of("项目细节可以更具体"));
        report.setSuggestions(List.of("继续补充高并发场景实践"));
        report.setDimensions(List.of(dimension));
        var stats=java.util.Map.of("sampleCount",3,"detectedCount",2,"missingCount",1,"dominantCounts",java.util.Map.of("neutral",1,"happy",1));
        report.setExpressions(java.util.Map.of("summary",stats,"questions",List.of(java.util.Map.of("roundNo",1,"question","请介绍项目经历","summary",stats))));
        when(reportService.getDetail(1L)).thenReturn(report);

        byte[] pdf = exportService.export(1L, "pdf");
        byte[] docx = exportService.export(1L, "docx");

        assertTrue(pdf.length > 1_000);
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
        assertTrue(docx.length > 1_000);
        assertEquals("PK", new String(docx, 0, 2, StandardCharsets.US_ASCII));
        try(var document=org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) {
            String text=new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertTrue(text.contains("表情回顾"));assertTrue(text.contains("占有效样本 50.0%"));assertTrue(text.contains("平静、开心"));
        }
        try(var document=new org.apache.poi.xwpf.usermodel.XWPFDocument(new java.io.ByteArrayInputStream(docx))) {
            String text=document.getParagraphs().stream().map(org.apache.poi.xwpf.usermodel.XWPFParagraph::getText).collect(java.util.stream.Collectors.joining("\n"));
            assertTrue(text.contains("表情回顾"));assertTrue(text.contains("平静、开心"));assertTrue(text.contains("不代表面试时长占比"));
        }
    }
}
