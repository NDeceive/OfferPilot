package com.zhimian.export;

import com.zhimian.common.BizException;
import com.zhimian.service.ResumeWorkbenchService;
import lombok.RequiredArgsConstructor;
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
import java.util.List;

/** The same saved version feeds the browser PDF preview and downloaded files. */
@Service
@RequiredArgsConstructor
public class ResumeDocumentExport {
    private final ResumeWorkbenchService workbench;

    public byte[] export(long versionId, String format) {
        ResumeWorkbenchService.ResumeContent content = workbench.version(versionId).content();
        if (ResumeWorkbenchService.hasPlaceholder(content))
            throw new BizException("简历仍有【待补】或【待确认】内容，请先修改并保存新版本");
        if (!"pdf".equals(format) && !"docx".equals(format)) throw new BizException("仅支持 PDF 或 DOCX 导出");
        try { return "pdf".equals(format) ? pdf(content) : docx(content); }
        catch (IOException e) { throw new BizException("简历导出失败，请检查服务器中文字体配置"); }
        catch (IllegalArgumentException e) { throw new BizException("简历含当前 PDF 字体不支持的字符，请修改后另存版本"); }
    }

    private byte[] pdf(ResumeWorkbenchService.ResumeContent content) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            PDType0Font font = PDType0Font.load(document, findFont());
            try (PdfLines writer = new PdfLines(document, font)) {
                writer.write(content.name().isBlank() ? "个人简历" : content.name(), 21, 31);
                writer.write(String.join("    ", List.of(content.phone(), content.email(), content.targetJob())
                        .stream().filter(value -> !value.isBlank()).toList()), 10, 21);
                section(writer, "个人简介", content.summary());
                section(writer, "教育经历", content.education());
                section(writer, "实习 / 工作经历", content.experience());
                section(writer, "项目经历", content.projects());
                section(writer, "专业技能", content.skills());
                section(writer, "荣誉奖项", content.awards());
            }
            document.save(bytes);
            return bytes.toByteArray();
        }
    }

    private void section(PdfLines writer, String title, String body) throws IOException {
        if (body.isBlank()) return;
        writer.write(title, 14, 25);
        writer.write(body, 10.5f, 18);
        writer.write("", 10, 8);
    }

    private byte[] docx(ResumeWorkbenchService.ResumeContent content) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            document.getDocument().getBody().addNewSectPr().addNewPgSz().setW(java.math.BigInteger.valueOf(11906));
            paragraph(document, content.name().isBlank() ? "个人简历" : content.name(), 20, true, ParagraphAlignment.CENTER);
            paragraph(document, String.join("    ", List.of(content.phone(), content.email(), content.targetJob())
                    .stream().filter(value -> !value.isBlank()).toList()), 10, false, ParagraphAlignment.CENTER);
            section(document, "个人简介", content.summary());
            section(document, "教育经历", content.education());
            section(document, "实习 / 工作经历", content.experience());
            section(document, "项目经历", content.projects());
            section(document, "专业技能", content.skills());
            section(document, "荣誉奖项", content.awards());
            document.write(bytes);
            return bytes.toByteArray();
        }
    }

    private void section(XWPFDocument document, String title, String body) {
        if (body.isBlank()) return;
        paragraph(document, title, 13, true, ParagraphAlignment.LEFT);
        paragraph(document, body, 10, false, ParagraphAlignment.LEFT);
    }

    private void paragraph(XWPFDocument document, String text, int size, boolean bold, ParagraphAlignment align) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(align);
        paragraph.setSpacingAfter(bold ? 120 : 75);
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Microsoft YaHei");
        run.setFontSize(size);
        run.setBold(bold);
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) run.addBreak();
            run.setText(lines[i]);
        }
    }

    private File findFont() {
        String windows = System.getenv("WINDIR");
        List<File> candidates = List.of(
                new File(windows == null ? "C:/Windows/Fonts/simhei.ttf" : windows + "/Fonts/simhei.ttf"),
                new File(windows == null ? "C:/Windows/Fonts/msyh.ttf" : windows + "/Fonts/msyh.ttf"),
                new File("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"),
                new File("/usr/share/fonts/truetype/noto/NotoSansSC-Regular.ttf"));
        return candidates.stream().filter(File::isFile).findFirst()
                .orElseThrow(() -> new BizException("服务器缺少可用的中文字体"));
    }

    private static final class PdfLines implements AutoCloseable {
        private static final float MARGIN = 48;
        private static final float WIDTH = PDRectangle.A4.getWidth() - MARGIN * 2;
        private final PDDocument document;
        private final PDType0Font font;
        private PDPageContentStream stream;
        private float y;

        private PdfLines(PDDocument document, PDType0Font font) throws IOException {
            this.document = document;
            this.font = font;
            newPage();
        }

        private void write(String text, float size, float leading) throws IOException {
            for (String paragraph : text.replace("\r", "").split("\n", -1)) {
                if (paragraph.isBlank()) { y -= leading; continue; }
                StringBuilder line = new StringBuilder();
                for (int offset = 0; offset < paragraph.length();) {
                    int point = paragraph.codePointAt(offset);
                    String character = new String(Character.toChars(point));
                    String next = line + character;
                    if (line.length() > 0 && font.getStringWidth(next) / 1000f * size > WIDTH) {
                        draw(line.toString(), size, leading);
                        line.setLength(0);
                    }
                    line.append(character);
                    offset += Character.charCount(point);
                }
                if (line.length() > 0) draw(line.toString(), size, leading);
            }
        }

        private void draw(String text, float size, float leading) throws IOException {
            if (y - leading < MARGIN) newPage();
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(text);
            stream.endText();
            y -= leading;
        }

        private void newPage() throws IOException {
            if (stream != null) stream.close();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PDRectangle.A4.getHeight() - MARGIN;
        }

        @Override public void close() throws IOException { if (stream != null) stream.close(); }
    }
}
