package com.zhimian.export;

import com.zhimian.controller.ReportController;
import com.zhimian.dto.ReportDetailResponse;
import com.zhimian.service.ImprovementPlanService;
import com.zhimian.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReportExportEndpointTest {
    @Test
    void downloadPassesAppendixSelectionAndReturnsAttachment() throws Exception {
        var reports=mock(ReportService.class);var exports=mock(ExportService.class);
        var report=new ReportDetailResponse();report.setReportId(42L);report.setJobName("Java 开发");
        when(reports.getDetail(42L)).thenReturn(report);
        when(exports.export(42L,"docx",true)).thenReturn(new byte[]{80,75,3,4});
        var mvc=MockMvcBuilders.standaloneSetup(new ReportController(reports,exports,mock(ImprovementPlanService.class))).build();
        mvc.perform(get("/api/report/42/export").param("format","docx").param("includeTranscript","true"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type","application/vnd.openxmlformats-officedocument.wordprocessingml.document;charset=UTF-8"))
                .andExpect(header().exists("Content-Disposition")).andExpect(content().bytes(new byte[]{80,75,3,4}));
        verify(exports).export(42L,"docx",true);
        mvc.perform(get("/api/report/42/export").param("format","html")).andExpect(status().isBadRequest());
        verifyNoMoreInteractions(exports);
    }
    @Test
    void deniedReportNeverReadsTranscriptOrRenders() {
        var reports=mock(ReportService.class);
        when(reports.getDetail(42L)).thenThrow(new com.zhimian.common.BizException("无权访问"));
        org.junit.jupiter.api.Assertions.assertThrows(com.zhimian.common.BizException.class,()->new ExportService(reports).export(42L,"pdf",true));
        verify(reports,never()).getExportMessages(anyLong());
    }
}
