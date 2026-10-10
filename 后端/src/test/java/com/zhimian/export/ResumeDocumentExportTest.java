package com.zhimian.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.BizException;
import com.zhimian.service.ResumeWorkbenchService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ResumeDocumentExportTest {
    @Test void refusesUnfinishedContentBeforeCreatingAFormalFile() {
        var content = new ResumeWorkbenchService.ResumeContent("张三", "", "", "后端开发",
                "", "", "", "【待补】项目结果", "Java", "");
        ResumeWorkbenchService workbench = new ResumeWorkbenchService(null, new ObjectMapper(), null, null, null, null) {
            @Override public VersionView version(long id) {
                return new VersionView(id, 1L, "待补版", "MANUAL", content, LocalDateTime.now());
            }
        };
        ResumeDocumentExport exporter = new ResumeDocumentExport(workbench);
        assertThrows(BizException.class, () -> exporter.export(8L, "pdf"));
        assertThrows(BizException.class, () -> exporter.export(8L, "docx"));
    }

    @Test void exportsTheSameSavedChineseResumeAsPdfAndDocx() {
        var content = new ResumeWorkbenchService.ResumeContent("张三", "13800000000", "candidate@example.com", "后端开发",
                "关注高可靠服务", "计算机科学专业", "", "负责订单服务的接口设计与测试", "Java、MySQL", "");
        ResumeWorkbenchService workbench = new ResumeWorkbenchService(null, new ObjectMapper(), null, null, null, null) {
            @Override public VersionView version(long id) {
                return new VersionView(id, 1L, "面试版", "MANUAL", content, LocalDateTime.now());
            }
        };
        ResumeDocumentExport exporter = new ResumeDocumentExport(workbench);
        byte[] pdf = exporter.export(8L, "pdf");
        byte[] docx = exporter.export(8L, "docx");
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
        assertEquals("PK", new String(docx, 0, 2, StandardCharsets.US_ASCII));
        assertTrue(pdf.length > 1000);
        assertTrue(docx.length > 1000);
    }
}
