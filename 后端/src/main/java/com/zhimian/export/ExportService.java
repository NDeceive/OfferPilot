package com.zhimian.export;

import com.zhimian.common.BizException;
import com.zhimian.dto.ReportDetailResponse;
import com.zhimian.dto.ReportDimensionView;
import com.zhimian.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 报告导出服务：将面试报告渲染为 HTML，再通过 documents4j (MS Word) 转换为 PDF 或 DOCX。
 * <p>
 * 当前使用 programmatic HTML 构建（不依赖外部模板引擎），后续可替换为 Freemarker/Thymeleaf。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final ReportService reportService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ---------- 公开方法 ----------

    /**
     * 导出报告为 PDF 或 DOCX。
     *
     * @param reportId 报告 ID
     * @param format   "pdf" 或 "docx"
     * @return 生成文件的字节数组
     */
    public byte[] export(Long reportId, String format) {
        // 1. 获取报告数据（含归属校验）
        ReportDetailResponse report = reportService.getDetail(reportId);

        // 使用 Java 原生库生成文件，避免依赖桌面 Word 自动化进程。
        return "pdf".equals(format) ? exportPdf(report) : exportDocx(report);
    }

    private byte[] exportPdf(ReportDetailResponse report) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType0Font font = PDType0Font.load(document, findCjkFont());
            try (PdfWriter writer = new PdfWriter(document, font)) {
                writer.write("面试能力评估报告", 22, 30);
                writer.write("岗位：" + safe(report.getJobName())
                        + "    报告编号：" + report.getReportId()
                        + "    生成时间：" + LocalDateTime.now().format(DATE_FMT), 10, 18);
                writer.write("综合得分：" + safe(report.getTotalScore()), 18, 26);

                writePdfSection(writer, "一、综合评价",
                        report.getSummary() == null ? List.of("暂无评价") : List.of(report.getSummary()));

                writer.write("二、五维能力得分", 15, 22);
                if (report.getDimensions() != null) {
                    for (ReportDimensionView dimension : report.getDimensions()) {
                        writer.write("- " + safe(dimension.getDimension())
                                + "：" + safe(dimension.getScore()) + " 分"
                                + "（" + safe(dimension.getLevel()) + "）", 11, 17);
                        if (dimension.getExplanation() != null && !dimension.getExplanation().isBlank()) {
                            writer.write("  " + dimension.getExplanation(), 10, 16);
                        }
                    }
                }

                writePdfSection(writer, "三、表现优势", report.getStrengths());
                writePdfSection(writer, "四、待改进项", report.getWeaknesses());
                writePdfSection(writer, "五、提升建议", report.getSuggestions());
                writePdfSection(writer, "六、表情回顾", expressionSummary(report));
            }
            document.save(output);
            return output.toByteArray();
        } catch (Exception e) {
            log.error("PDF 原生导出失败 reportId={}", report.getReportId(), e);
            throw new ExportException("PDF 生成失败：" + e.getMessage());
        }
    }

    private byte[] exportDocx(ReportDetailResponse report) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addDocxParagraph(document, "面试能力评估报告", 22, true, ParagraphAlignment.CENTER);
            addDocxParagraph(document,
                    "岗位：" + safe(report.getJobName())
                            + "    报告编号：" + report.getReportId()
                            + "    生成时间：" + LocalDateTime.now().format(DATE_FMT),
                    10, false, ParagraphAlignment.CENTER);
            addDocxParagraph(document, "综合得分：" + safe(report.getTotalScore()) + " 分",
                    18, true, ParagraphAlignment.CENTER);

            addDocxSection(document, "一、综合评价",
                    report.getSummary() == null ? List.of("暂无评价") : List.of(report.getSummary()));

            addDocxParagraph(document, "二、五维能力得分", 15, true, ParagraphAlignment.LEFT);
            if (report.getDimensions() != null) {
                for (ReportDimensionView dimension : report.getDimensions()) {
                    addDocxParagraph(document,
                            safe(dimension.getDimension()) + "：" + safe(dimension.getScore())
                                    + " 分（" + safe(dimension.getLevel()) + "）",
                            11, true, ParagraphAlignment.LEFT);
                    if (dimension.getExplanation() != null && !dimension.getExplanation().isBlank()) {
                        addDocxParagraph(document, dimension.getExplanation(),
                                10, false, ParagraphAlignment.LEFT);
                    }
                }
            }

            addDocxSection(document, "三、表现优势", report.getStrengths());
            addDocxSection(document, "四、待改进项", report.getWeaknesses());
            addDocxSection(document, "五、提升建议", report.getSuggestions());
            addDocxSection(document, "六、表情回顾", expressionSummary(report));

            document.write(output);
            return output.toByteArray();
        } catch (Exception e) {
            log.error("DOCX 原生导出失败 reportId={}", report.getReportId(), e);
            throw new ExportException("Word 生成失败：" + e.getMessage());
        }
    }

    private static List<String> expressionSummary(ReportDetailResponse report) {
        var lines = new java.util.ArrayList<String>();
        var data=report.getExpressions();
        if(data==null || !(data.get("summary") instanceof java.util.Map<?,?> summary)) {
            return List.of("本次未记录表情数据，不补造历史记录。");
        }
        int total=number(summary.get("sampleCount")), valid=number(summary.get("detectedCount"));
        lines.add("有效人脸采样："+valid+" / "+total+" 次；未检测到人脸："+number(summary.get("missingCount"))+" 次。");
        if(valid>0) lines.add("分类不明确："+number(summary.get("uncertainCount"))+" 次，占检出人脸样本 "+String.format(java.util.Locale.ROOT,"%.1f%%",number(summary.get("uncertainCount"))*100.0/valid));
        if(valid>0 && summary.get("dominantCounts") instanceof java.util.Map<?,?> counts) {
            String[] keys={"neutral","happy","sad","angry","fearful","disgusted","surprised"};
            String[] labels={"平静","开心","低落","生气","害怕","厌恶","惊讶"};
            for(int index=0;index<keys.length;index++) {
                int count=number(counts.get(keys[index]));
                lines.add(labels[index]+"："+count+" 次，占有效样本 "+String.format(java.util.Locale.ROOT,"%.1f%%",count*100.0/valid));
            }
        } else lines.add("没有有效人脸样本，不能判断主要表情。");
        if(data.get("questions") instanceof List<?> questions) {
            for(Object item:questions) if(item instanceof java.util.Map<?,?> question && question.get("summary") instanceof java.util.Map<?,?> questionSummary) {
                String main=number(questionSummary.get("detectedCount"))>0?"分类不明确":"无有效样本";
                if(questionSummary.get("dominantCounts") instanceof java.util.Map<?,?> counts) {
                    int uncertain=number(questionSummary.get("uncertainCount"));
                    int max=Math.max(uncertain,counts.values().stream().mapToInt(ExportService::number).max().orElse(0));
                    if(max>0) {
                        var labels=java.util.Map.of("neutral","平静","happy","开心","sad","低落","angry","生气","fearful","害怕","disgusted","厌恶","surprised","惊讶");
                        main=com.zhimian.service.ExpressionService.KEYS.stream().filter(key->number(counts.get(key))==max).map(labels::get).collect(java.util.stream.Collectors.joining("、"));
                        if(uncertain==max) main=main.isEmpty()?"分类不明确":main+"、分类不明确";
                    }
                }
                lines.add("第 "+question.get("roundNo")+" 题："+main+"；有效采样 "+number(questionSummary.get("detectedCount"))+" / "+number(questionSummary.get("sampleCount"))+" 次。问题："+safe(question.get("question")));
            }
        }
        lines.add("最高概率不足45%或前两类差值不足12个百分点时，计为分类不明确。占比按检出人脸样本次数计算，不代表面试时长占比。表情分类不推断真实情绪，不参与能力评分；交互时间线请在网页查看。");
        return lines;
    }
    private static int number(Object value) { return value instanceof Number n ? n.intValue() : 0; }

    private static void writePdfSection(PdfWriter writer, String title, List<String> items) throws IOException {
        writer.write(title, 15, 22);
        if (items == null || items.isEmpty()) {
            writer.write("- 暂无", 11, 17);
            return;
        }
        for (String item : items) {
            writer.write("- " + safe(item), 11, 17);
        }
    }

    private static void addDocxSection(XWPFDocument document, String title, List<String> items) {
        addDocxParagraph(document, title, 15, true, ParagraphAlignment.LEFT);
        if (items == null || items.isEmpty()) {
            addDocxParagraph(document, "暂无", 11, false, ParagraphAlignment.LEFT);
            return;
        }
        for (String item : items) {
            addDocxParagraph(document, "• " + safe(item), 11, false, ParagraphAlignment.LEFT);
        }
    }

    private static void addDocxParagraph(XWPFDocument document, String text, int size,
                                         boolean bold, ParagraphAlignment alignment) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(alignment);
        paragraph.setSpacingAfter(120);
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Microsoft YaHei");
        run.setFontSize(size);
        run.setBold(bold);
        run.setText(safe(text));
    }

    private static File findCjkFont() {
        String windowsDir = System.getenv("WINDIR");
        File fontsDir = new File(windowsDir == null ? "C:\\Windows" : windowsDir, "Fonts");
        for (String name : List.of("simhei.ttf", "NotoSansSC-VF.ttf", "Deng.ttf")) {
            File font = new File(fontsDir, name);
            if (font.isFile()) {
                return font;
            }
        }
        throw new ExportException("未找到可用的中文字体");
    }

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static final class PdfWriter implements AutoCloseable {
        private static final float MARGIN = 48;
        private static final float WIDTH = PDRectangle.A4.getWidth() - MARGIN * 2;

        private final PDDocument document;
        private final PDType0Font font;
        private PDPageContentStream stream;
        private float y;

        private PdfWriter(PDDocument document, PDType0Font font) throws IOException {
            this.document = document;
            this.font = font;
            newPage();
        }

        private void write(String text, float size, float leading) throws IOException {
            String normalized = safe(text).replace("\r", "");
            for (String paragraph : normalized.split("\n", -1)) {
                if (paragraph.isEmpty()) {
                    ensureSpace(leading);
                    y -= leading;
                    continue;
                }
                StringBuilder line = new StringBuilder();
                for (int offset = 0; offset < paragraph.length();) {
                    int codePoint = paragraph.codePointAt(offset);
                    String character = new String(Character.toChars(codePoint));
                    String candidate = line + character;
                    float candidateWidth = font.getStringWidth(candidate) / 1000f * size;
                    if (candidateWidth > WIDTH && line.length() > 0) {
                        writeLine(line.toString(), size, leading);
                        line.setLength(0);
                    }
                    line.append(character);
                    offset += Character.charCount(codePoint);
                }
                if (line.length() > 0) {
                    writeLine(line.toString(), size, leading);
                }
            }
        }

        private void writeLine(String line, float size, float leading) throws IOException {
            ensureSpace(leading);
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(line);
            stream.endText();
            y -= leading;
        }

        private void ensureSpace(float leading) throws IOException {
            if (y - leading < MARGIN) {
                newPage();
            }
        }

        private void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PDRectangle.A4.getHeight() - MARGIN;
        }

        @Override
        public void close() throws IOException {
            if (stream != null) {
                stream.close();
            }
        }
    }

    // ---------- HTML 渲染 ----------

    /**
     * 将报告数据渲染为完整的 HTML5 文档（内联 CSS，可直接被 Word 打开并转换）。
     */
    String renderHtml(ReportDetailResponse report) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"zh-CN\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<title>面试能力评估报告</title>\n");
        sb.append("<style>\n");
        sb.append("  * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        sb.append("  body {\n");
        sb.append("    font-family: \"Microsoft YaHei\", \"PingFang SC\", \"SimHei\", sans-serif;\n");
        sb.append("    color: #1e293b; background: #fff; padding: 48px 56px;\n");
        sb.append("    line-height: 1.7; max-width: 800px; margin: 0 auto;\n");
        sb.append("  }\n");
        sb.append("  .header { text-align: center; border-bottom: 3px solid #2563eb; padding-bottom: 22px; margin-bottom: 32px; }\n");
        sb.append("  .header h1 { font-size: 28px; color: #0b1e39; margin-bottom: 8px; }\n");
        sb.append("  .header .meta { font-size: 13px; color: #64748b; }\n");
        sb.append("  .score-card { text-align: center; margin: 28px 0 32px; }\n");
        sb.append("  .score-value { font-size: 56px; font-weight: 900; color: #2563eb; line-height: 1.1; }\n");
        sb.append("  .score-band { display: inline-block; margin-top: 6px; padding: 4px 18px; border-radius: 20px;\n");
        sb.append("    font-size: 14px; font-weight: 700; color: #fff; background: #2563eb; }\n");
        sb.append("  .summary { background: #f8faff; border-left: 4px solid #2563eb; padding: 14px 18px;\n");
        sb.append("    border-radius: 6px; margin-bottom: 30px; font-size: 14px; color: #334155; }\n");
        sb.append("  h2 { font-size: 18px; color: #0f2b5c; margin: 28px 0 12px; padding-bottom: 6px;\n");
        sb.append("    border-bottom: 1px solid #e2e8f0; }\n");
        sb.append("  table.dims { width: 100%; border-collapse: collapse; margin: 12px 0 24px; }\n");
        sb.append("  table.dims th { background: #2563eb; color: #fff; padding: 8px 10px; font-size: 13px; text-align: center; }\n");
        sb.append("  table.dims td { padding: 8px 10px; border: 1px solid #e2e8f0; font-size: 13px; }\n");
        sb.append("  table.dims td.name { font-weight: 700; white-space: nowrap; }\n");
        sb.append("  .bar-wrap { background: #eef2f6; border-radius: 8px; height: 10px; width: 100%; }\n");
        sb.append("  .bar-fill { height: 10px; border-radius: 8px; }\n");
        sb.append("  .bar-excellent { background: #22c55e; }\n");
        sb.append("  .bar-good { background: #3b82f6; }\n");
        sb.append("  .bar-pass { background: #f59e0b; }\n");
        sb.append("  .bar-weak { background: #ef4444; }\n");
        sb.append("  ul { margin-left: 20px; margin-bottom: 16px; }\n");
        sb.append("  ul li { font-size: 14px; margin-bottom: 6px; color: #475569; }\n");
        sb.append("  .footer { margin-top: 44px; padding-top: 14px; border-top: 1px solid #e2e8f0;\n");
        sb.append("    text-align: center; font-size: 12px; color: #94a3b8; }\n");
        sb.append("  @media print { body { padding: 0; } }\n");
        sb.append("</style>\n</head>\n<body>\n");

        // --- 页头 ---
        sb.append("<div class=\"header\">\n");
        sb.append("  <h1>面试能力评估报告</h1>\n");
        sb.append("  <p class=\"meta\">岗位：").append(escape(report.getJobName())).append(" &nbsp;|&nbsp;");
        sb.append("报告编号：").append(report.getReportId()).append(" &nbsp;|&nbsp;");
        sb.append("生成时间：").append(LocalDateTime.now().format(DATE_FMT)).append("</p>\n");
        sb.append("</div>\n");

        // --- 总分 ---
        String band = scoreBand(report.getTotalScore());
        sb.append("<div class=\"score-card\">\n");
        sb.append("  <div class=\"score-value\">").append(report.getTotalScore()).append("</div>\n");
        sb.append("  <div class=\"score-band\">").append(band).append("</div>\n");
        sb.append("</div>\n");

        // --- 综合评价 ---
        if (report.getSummary() != null && !report.getSummary().isEmpty()) {
            sb.append("<div class=\"summary\">").append(escape(report.getSummary())).append("</div>\n");
        }

        // --- 五维能力得分表 ---
        sb.append("<h2>一、五维能力得分</h2>\n");
        sb.append("<table class=\"dims\">\n");
        sb.append("  <tr><th>评估维度</th><th>得分</th><th>等级</th><th>得分条形图</th></tr>\n");
        List<ReportDimensionView> dims = report.getDimensions();
        if (dims != null) {
            for (ReportDimensionView d : dims) {
                double score = d.getScore() != null ? d.getScore().doubleValue() : 0;
                String barClass = barClass(score);
                sb.append("  <tr>");
                sb.append("<td class=\"name\">").append(escape(d.getDimension())).append("</td>");
                sb.append("<td style=\"text-align:center;font-weight:700;\">").append(d.getScore()).append("</td>");
                sb.append("<td style=\"text-align:center;\">").append(escape(d.getLevel())).append("</td>");
                sb.append("<td><div class=\"bar-wrap\"><div class=\"bar-fill ").append(barClass)
                        .append("\" style=\"width:").append((int) score).append("%;\"></div></div></td>");
                sb.append("</tr>\n");
            }
        }
        sb.append("</table>\n");

        // --- 五维解释 ---
        if (dims != null) {
            for (ReportDimensionView d : dims) {
                if (d.getExplanation() != null && !d.getExplanation().isEmpty()) {
                    sb.append("<p style=\"font-size:13px;color:#64748b;margin:2px 0 8px;\">");
                    sb.append("<strong>").append(escape(d.getDimension())).append("：</strong>");
                    sb.append(escape(d.getExplanation())).append("</p>\n");
                }
            }
        }

        // --- 优势 ---
        List<String> strengths = report.getStrengths();
        if (strengths != null && !strengths.isEmpty()) {
            sb.append("<h2>二、表现优势</h2>\n<ul>\n");
            for (String s : strengths) {
                sb.append("  <li>").append(escape(s)).append("</li>\n");
            }
            sb.append("</ul>\n");
        }

        // --- 不足 ---
        List<String> weaknesses = report.getWeaknesses();
        if (weaknesses != null && !weaknesses.isEmpty()) {
            sb.append("<h2>三、待改进项</h2>\n<ul>\n");
            for (String w : weaknesses) {
                sb.append("  <li>").append(escape(w)).append("</li>\n");
            }
            sb.append("</ul>\n");
        }

        // --- 建议 ---
        List<String> suggestions = report.getSuggestions();
        if (suggestions != null && !suggestions.isEmpty()) {
            sb.append("<h2>四、提升建议</h2>\n<ul>\n");
            for (String s : suggestions) {
                sb.append("  <li>").append(escape(s)).append("</li>\n");
            }
            sb.append("</ul>\n");
        }

        // --- 页脚 ---
        sb.append("<div class=\"footer\">由智面幻境 · OfferPilot AI 模拟面试系统自动生成 &nbsp;|&nbsp;");
        sb.append(LocalDateTime.now().format(DATE_FMT)).append("</div>\n");

        sb.append("</body>\n</html>");
        return sb.toString();
    }

    // ---------- 辅助 ----------

    /** HTML 文本转义 */
    static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** 分数 → 等级文字 */
    static String scoreBand(BigDecimal score) {
        if (score == null) return "未评估";
        double s = score.doubleValue();
        if (s >= 85) return "优秀";
        if (s >= 70) return "良好";
        if (s >= 60) return "合格";
        return "待提升";
    }

    /** 分数 → 条形图 CSS 类 */
    static String barClass(double score) {
        if (score >= 85) return "bar-excellent";
        if (score >= 70) return "bar-good";
        if (score >= 60) return "bar-pass";
        return "bar-weak";
    }
}
