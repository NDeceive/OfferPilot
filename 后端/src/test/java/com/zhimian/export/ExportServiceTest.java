package com.zhimian.export;

import com.zhimian.dto.ReportDetailResponse;
import com.zhimian.dto.ReportDimensionView;
import com.zhimian.dto.ModuleScoreView;
import com.zhimian.entity.InterviewMessage;
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
    void missingScoresAndExpressionsRemainExplicitInsteadOfBecomingZero() throws Exception {
        var reports=mock(ReportService.class);var report=new ReportDetailResponse();report.setReportId(7L);
        report.setSummary("缺失评分时保留原始结论。\n第二行结论也应完整显示。");
        when(reports.getDetail(7L)).thenReturn(report);
        var export=new ExportService(reports);
        try(var pdf=org.apache.pdfbox.pdmodel.PDDocument.load(export.export(7L,"pdf",true))) {
            String text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
            assertTrue(text.contains("本次暂无维度评分"));assertTrue(text.contains("本次无有效表情记录"));
            assertTrue(text.contains("第二行结论也应完整显示"));assertTrue(text.contains("-- / 100"));
            org.junit.jupiter.api.Assertions.assertFalse(text.contains("完整问答复盘"));
        }
        try(var word=new org.apache.poi.xwpf.usermodel.XWPFDocument(new java.io.ByteArrayInputStream(export.export(7L,"docx",true)))) {
            assertTrue(word.getDocument().xmlText().contains("— / 100"));
            assertTrue(word.getParagraphs().stream().anyMatch(p->p.getText().contains("本次无有效表情记录")));
        }
    }

    @Test
    void newScoringChartsAndLongAppendicesPreserveData() throws Exception {
        var service=mock(ReportService.class);
        var export=new ExportService(service);
        var report=new ReportDetailResponse();report.setReportId(42L);report.setSessionId(42L);
        report.setJobName("Java 后端开发");report.setOverallMatchScore(new BigDecimal("112.5"));report.setTotalScore(new BigDecimal("5"));
        report.setStartTime(java.time.LocalDateTime.of(2026,10,8,10,0));report.setActualDurationSeconds(240L);
        report.setSummary("技术基础较扎实；需要进一步解释边界条件，并用具体场景说明取舍。");report.setStrengths(List.of("能够准确说明哈希冲突的基本处理方式。"));
        var modules=new java.util.ArrayList<ModuleScoreView>();
        var names=List.of("专业知识","项目经验","逻辑表达","岗位匹配","追问表现");
        for(int i=0;i<5;i++){var m=new ModuleScoreView();m.setModuleName(names.get(i));m.setRawScore(BigDecimal.valueOf(70+i*4));m.setTargetScore(BigDecimal.valueOf(75));m.setBaseWeight(new BigDecimal("0.2"));m.setImprovementPriority(BigDecimal.valueOf(10-i));m.setEvidence("回答说明了基本流程，但尚未覆盖具体边界。");m.setSuggestion("结合"+names.get(i)+"整理一个具体案例，并说明边界条件与验证方法。");modules.add(m);}report.setModuleScores(modules);
        long start=1791424800000L;var samples=new java.util.ArrayList<java.util.Map<String,Object>>();
        int detectedIndex=0;var missing=java.util.Set.of(0,17,34,51,68,85,102);
        for(int i=0;i<120;i++){var probabilities=new java.util.LinkedHashMap<String,Double>();var keys=com.zhimian.service.ExpressionService.KEYS;for(var k:keys)probabilities.put(k,.04);probabilities.put(keys.get(detectedIndex/20),.76);boolean detected=!missing.contains(i);if(detected)detectedIndex++;samples.add(java.util.Map.of("capturedAt",start+i*2000,"roundNo",i/40+1,"faceDetected",detected,"probabilities",probabilities));}
        var counts=java.util.Map.of("neutral",20,"happy",20,"sad",20,"angry",20,"fearful",20,"disgusted",13,"surprised",0);
        var stats=java.util.Map.of("sampleCount",120,"detectedCount",113,"missingCount",7,"uncertainCount",0,"dominantCounts",counts);
        var qs=new java.util.ArrayList<java.util.Map<String,Object>>();for(int i=0;i<3;i++){
            var group=samples.subList(i*40,(i+1)*40);var groupCounts=new java.util.LinkedHashMap<String,Integer>();int valid=0;
            for(var sample:group)if(Boolean.TRUE.equals(sample.get("faceDetected"))){valid++;var probabilities=ExportService.map(sample.get("probabilities"));String dominant=probabilities.entrySet().stream().max(java.util.Comparator.comparingDouble(e->((Number)e.getValue()).doubleValue())).orElseThrow().getKey();groupCounts.merge(dominant,1,Integer::sum);}
            qs.add(java.util.Map.of("roundNo",i+1,"question","说明技术实现与边界条件","startedAt",start+i*80000,"endedAt",start+(i+1)*80000,"summary",java.util.Map.of("sampleCount",40,"detectedCount",valid,"uncertainCount",0,"dominantCounts",groupCounts)));
        }
        report.setExpressions(java.util.Map.of("summary",stats,"timeline",samples,"questions",qs,"startedAt",start,"endedAt",start+240000));
        var question=new InterviewMessage();question.setRole("INTERVIEWER");question.setMsgType("MAIN");question.setRoundNo(1);question.setContent("请说明 HashMap 的实现原理。");
        var answer=new InterviewMessage();answer.setRole("CANDIDATE");answer.setMsgType("ANSWER");answer.setRoundNo(1);answer.setContent("先算哈希定位到桶，通过链表处理冲突，链表过长时转为红黑树优化查询。");
        when(service.getDetail(42L)).thenReturn(report);when(service.getExportMessages(42L)).thenReturn(List.of(question,answer));
        checkAndSave(export,report,false,"standard");
        for(int i=5;i<10;i++){var m=new ModuleScoreView();m.setModuleName("非常长的评分维度名称用于验证完整显示与自动换行"+i);m.setRawScore(BigDecimal.valueOf(60+i));modules.add(m);}
        report.setSummary("长结论换行检查。".repeat(45));answer.setContent("长回答完整续页检查，不能丢失原始内容。".repeat(150)+"原始回答结束标记");
        for(int i=3;i<25;i++)qs.add(java.util.Map.of("roundNo",i+1,"startedAt",start,"endedAt",start+240000,"summary",stats));
        checkAndSave(export,report,true,"stress");
    }

    private static void checkAndSave(ExportService export,ReportDetailResponse report,boolean transcript,String name)throws Exception{
        byte[] pdf=export.export(42L,"pdf",transcript),docx=export.export(42L,"docx",transcript);
        try(var doc=org.apache.pdfbox.pdmodel.PDDocument.load(pdf)){
            String text=new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
            assertTrue(text.contains("112.5%"));assertTrue(text.contains("维度评分明细"));assertTrue(text.contains("逐题回顾表"));
            assertEquals(transcript,text.contains("完整问答复盘"));
            if(!transcript){var firstPage=new org.apache.pdfbox.text.PDFTextStripper();firstPage.setEndPage(1);assertTrue(firstPage.getText(doc).contains("下一轮训练重点"),"短报告的训练建议应接续首页内容，而不是强制另起一页");}
            if(transcript){assertTrue(text.contains("原始回答结束标记"));assertTrue(text.contains("Q25"));}
            int images=0;for(var page:doc.getPages())for(var key:page.getResources().getXObjectNames())if(page.getResources().isImageXObject(key))images++;
            assertTrue(images>=3);
        }
        try(var doc=new org.apache.poi.xwpf.usermodel.XWPFDocument(new java.io.ByteArrayInputStream(docx))){
            assertTrue(doc.getAllPictures().size()>=3);
            assertTrue(doc.getTables().stream().anyMatch(t->t.getCTTbl().xmlText().contains("目标分")));
            assertTrue(doc.getHeaderList().stream().anyMatch(h->h.getText().contains("面试能力报告")));
            assertTrue(doc.getFooterList().stream().anyMatch(f->f._getHdrFtr().xmlText().contains("NUMPAGES")));
            assertEquals("Title",doc.getParagraphs().stream().filter(p->p.getText().equals("面试能力评估报告")).findFirst().orElseThrow().getStyle());
            org.junit.jupiter.api.Assertions.assertFalse(doc.getDocument().xmlText().contains("pageBreakBefore"),"章节应按剩余空间连续排版");
            if(transcript)assertTrue(doc.getParagraphs().stream().anyMatch(p->p.getText().contains("原始回答结束标记")));
        }
        String directory=System.getenv("REPORT_EXPORT_QA_DIRECTORY");if(directory!=null){var path=java.nio.file.Path.of(directory);java.nio.file.Files.createDirectories(path);java.nio.file.Files.write(path.resolve(name+".pdf"),pdf);java.nio.file.Files.write(path.resolve(name+".docx"),docx);}
    }

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
            assertTrue(text.contains("表情回顾"));assertTrue(text.contains("50.0%"));assertTrue(text.contains("平静、开心"));
        }
        try(var document=new org.apache.poi.xwpf.usermodel.XWPFDocument(new java.io.ByteArrayInputStream(docx))) {
            String text=document.getParagraphs().stream().map(org.apache.poi.xwpf.usermodel.XWPFParagraph::getText).collect(java.util.stream.Collectors.joining("\n"));
            assertTrue(text.contains("表情回顾"));assertTrue(text.contains("不代表面试时长占比"));
            assertTrue(document.getTables().size() >= 3);
            assertTrue(document.getAllPictures().size() >= 2);
        }
    }
}
