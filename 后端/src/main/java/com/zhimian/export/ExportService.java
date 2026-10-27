package com.zhimian.export;

import com.zhimian.dto.*;
import com.zhimian.entity.InterviewMessage;
import com.zhimian.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;
import org.springframework.stereotype.Service;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import javax.imageio.ImageIO;

/** The same content and charts are used by both native export formats. */
@Slf4j @Service @RequiredArgsConstructor
public class ExportService {
    private final ReportService reportService;
    static final Color INK=new Color(32,58,49), GREEN=new Color(5,150,105), LIGHT=new Color(240,246,240);
    static final Color PAPER=new Color(255,254,250), BODY=new Color(53,68,62), MUTED=new Color(116,128,120), LINE=new Color(223,230,223);
    private static final int WORD_WIDTH=10226;
    record Dimension(String name, BigDecimal score, BigDecimal target, BigDecimal weight) {}
    record Overview(String label,String score,String note,String summary) {}
    record Block(String text,int size,List<List<String>> rows,BufferedImage image,Overview overview) {
        Block(String text,int size,List<List<String>> rows,BufferedImage image){this(text,size,rows,image,null);}
    }
    public byte[] export(Long id,String format){return export(id,format,false);}
    public byte[] export(Long id,String format,boolean includeTranscript){
        if(!Set.of("pdf","docx").contains(format))throw new ExportException("不支持的导出格式");
        var report=reportService.getDetail(id);
        var messages=reportService.getExportMessages(id);
        try{var blocks=structure(report,messages==null?List.of():messages,includeTranscript);return format.equals("pdf")?pdf(blocks,report):docx(blocks,report);}
        catch(Exception e){log.error("报告导出失败 reportId={} format={}",id,format,e);throw new ExportException("报告生成失败："+e.getMessage());}
    }
    static List<Dimension> dimensions(ReportDetailResponse r){
        if(r.getModuleScores()!=null&&!r.getModuleScores().isEmpty())return r.getModuleScores().stream().map(m->new Dimension(safe(m.getModuleName()),m.getRawScore(),m.getTargetScore(),m.getBaseWeight())).toList();
        return r.getDimensions()==null?List.of():r.getDimensions().stream().map(d->new Dimension(safe(d.getDimension()),d.getScore(),null,null)).toList();
    }
    private static List<Block> structure(ReportDetailResponse r,List<InterviewMessage> messages,boolean transcript){
        var b=new ArrayList<Block>();text(b,"INTERVIEW PERFORMANCE REPORT",8);text(b,"面试能力评估报告",24);text(b,"目标岗位  "+safe(r.getJobName())+"     报告编号  "+r.getReportId(),10);
        long duration=r.getActualDurationSeconds()!=null?r.getActualDurationSeconds():r.getDurationSeconds()==null?0:r.getDurationSeconds();
        text(b,"面试时间  "+(r.getStartTime()==null?"未记录":r.getStartTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd  HH:mm")))+"     时长  "+(duration>0?duration/60+"分"+duration%60+"秒":"未记录"),10);
        String summary=blank(r.getSummary())?"暂无可评价结论":r.getSummary();boolean longSummary=summary.codePointCount(0,summary.length())>180;
        b.add(new Block(null,0,null,null,new Overview(r.getOverallMatchScore()!=null?"训练目标匹配度":"综合评分",r.getOverallMatchScore()!=null?value(r.getOverallMatchScore())+"%":value(r.getTotalScore())+" / 100",r.getOverallMatchScore()!=null?"相对于本次训练目标的匹配度，可超过100%，并非百分制得分。":"评分范围 0–100；缺失评分不补造。",longSummary?"完整结论见下文。":summary)));
        if(longSummary){text(b,"本次结论",16);text(b,summary,12);}
        var dims=dimensions(r);
        for(int offset=0;offset<dims.size();offset+=6){var group=dims.subList(offset,Math.min(offset+6,dims.size()));text(b,offset==0?"能力画像与维度评分明细":"能力画像与维度评分明细 续",16);
            var rows=new ArrayList<List<String>>();rows.add(List.of("维度","实际分","目标分","差值","权重"));for(var d:group)rows.add(List.of(d.name(),value(d.score()),value(d.target()),signedDifference(d.score(),d.target()),d.weight()==null?"—":value(d.weight().multiply(BigDecimal.valueOf(100)))+"%"));
            panel(b,ReportCharts.radar(group),rows);text(b,"绿色实线为实际分，灰色虚线为目标分；维度分范围0–100。差值=实际分−目标分；缺值不补造。",9);}
        if(dims.isEmpty())text(b,"本次暂无维度评分",12);
        text(b,"本报告用于训练复盘与下一步练习参考，不代表录用判断。",9);
        text(b,"下一轮训练重点",20);
        if(r.getStrengths()!=null&&!r.getStrengths().isEmpty()){text(b,"值得保留",16);r.getStrengths().stream().filter(s->!blank(s)).distinct().limit(2).forEach(s->text(b,s,11));}
        var selected=r.getModuleScores()==null?List.<ModuleScoreView>of():r.getModuleScores().stream().filter(m->!blank(m.getSuggestion())).sorted(Comparator.comparing((ModuleScoreView m)->m.getImprovementPriority()==null?BigDecimal.ZERO:m.getImprovementPriority()).reversed()).toList();
        var seen=new HashSet<String>();int index=0;
        for(var m:selected){if(!seen.add(m.getSuggestion().strip()))continue;text(b,String.format(Locale.ROOT,"%02d  %s",++index,safe(m.getModuleName())),16);if(!blank(m.getEvidence()))text(b,"发现依据  "+m.getEvidence(),12);text(b,"练习动作  "+m.getSuggestion(),11);if(index==3)break;}
        if(index==0){var suggestions=r.getSuggestions()==null?List.<String>of():r.getSuggestions();for(var s:suggestions){if(blank(s)||!seen.add(s.strip()))continue;text(b,(++index)+"  "+s,12);if(index==3)break;}}
        if(index==0)text(b,"本次暂无明确训练建议",12);
        text(b,"精选回答证据",16);int excerpts=0;
        for(int i=0;i<messages.size()&&excerpts<2;i++){var m=messages.get(i);if(!"CANDIDATE".equals(m.getRole())||blank(m.getContent()))continue;String question="";for(int j=i-1;j>=0;j--)if("INTERVIEWER".equals(messages.get(j).getRole())){question=messages.get(j).getContent();break;}text(b,"Q"+safe(m.getRoundNo())+"  "+excerpt(question,100),13);text(b,"回答节选  "+excerpt(m.getContent(),160),11);excerpts++;}
        if(excerpts==0)text(b,"暂无回答记录",12);
        expressions(b,r.getExpressions());
        if(transcript&&!messages.isEmpty()){text(b,"完整问答复盘",20);for(var m:messages){if(blank(m.getContent()))continue;text(b,"Q"+safe(m.getRoundNo())+"  "+("CANDIDATE".equals(m.getRole())?"你的回答":"FOLLOWUP".equals(m.getMsgType())?"面试官追问":"面试官"),13);text(b,m.getContent(),12);}}
        return b;
    }
    private static void expressions(List<Block>b,Map<String,Object>data){
        var summary=map(data==null?null:data.get("summary"));int detected=number(summary.get("detectedCount"));if(detected==0){text(b,"本次无有效表情记录。",12);return;}
        text(b,"表情回顾",20);text(b,"采样总次数 "+number(summary.get("sampleCount"))+"     检出人脸 "+detected+"     未检出 "+number(summary.get("missingCount"))+"     分类不明确 "+percent(number(summary.get("uncertainCount")),detected),10);
        text(b,"表情分布与统计",16);
        var rows=new ArrayList<List<String>>();rows.add(List.of("识别类别","次数","占比"));var counts=map(summary.get("dominantCounts"));for(int i=0;i<ReportCharts.KEYS.size();i++){String k=ReportCharts.KEYS.get(i);int n=number(k.equals("uncertain")?summary.get("uncertainCount"):counts.get(k));rows.add(List.of(ReportCharts.LABELS.get(i),String.valueOf(n),percent(n,detected)));}panel(b,ReportCharts.donut(summary),rows);
        text(b,"占比按检出人脸的采样次数计算，包含分类不明确；不代表面试时长占比。",9);
        var samples=maps(data.get("timeline"));var questions=maps(data.get("questions"));text(b,"表情时间线",16);
        long start=longNumber(data.get("startedAt")),end=longNumber(data.get("endedAt"));
        if(!samples.isEmpty()){samples=new ArrayList<>(samples);samples.sort(Comparator.comparingLong(s->longNumber(s.get("capturedAt"))));if(start==0)start=longNumber(samples.get(0).get("capturedAt"));end=Math.max(end,longNumber(samples.get(samples.size()-1).get("capturedAt")));}
        if(samples.isEmpty())text(b,"历史记录仅有统计摘要，无时间线采样。",12);
        else for(long window=start;window<Math.max(start+1,end);window+=300000){long stop=Math.min(Math.max(start+1,end),window+300000);text(b,"采样范围 "+time(window-start)+"–"+time(stop-start),10);image(b,ReportCharts.timeline(samples,questions,window,stop,start));text(b,"颜色同上表。纵轴为最高分类概率，菱形为分类不明确，虚线为切题；密集题号见逐题表。采样间断处留空。",9);}
        text(b,"逐题回顾表",16);rows=new ArrayList<>();rows.add(List.of("题号 / 时间段","主要识别类别","有效采样","不明确占比"));for(var q:questions){var qs=map(q.get("summary"));int n=number(qs.get("detectedCount"));rows.add(List.of("Q"+safe(q.get("roundNo"))+" "+(longNumber(q.get("startedAt"))>0?time(longNumber(q.get("startedAt"))-start)+"–"+time(longNumber(q.get("endedAt"))-start):"未记录"),dominant(qs),n+" / "+number(qs.get("sampleCount")),n==0?"—":percent(number(qs.get("uncertainCount")),n)));}table(b,rows);
        text(b,"最高概率不足45%或前两类相差不足12个百分点时，分类不明确。识别结果不推断真实情绪，不参与能力评分。",9);
    }
    static String dominant(Map<String,Object>s){if(number(s.get("detectedCount"))==0)return "无有效样本";var counts=map(s.get("dominantCounts"));int max=number(s.get("uncertainCount"));for(var n:counts.values())max=Math.max(max,number(n));var labels=new ArrayList<String>();for(int i=0;i<ReportCharts.KEYS.size();i++){var k=ReportCharts.KEYS.get(i);if(number(k.equals("uncertain")?s.get("uncertainCount"):counts.get(k))==max&&max>0)labels.add(ReportCharts.LABELS.get(i));}return String.join("、",labels);}
    static Map<String,Object> map(Object x){if(!(x instanceof Map<?,?>m))return Map.of();var r=new LinkedHashMap<String,Object>();m.forEach((k,v)->r.put(String.valueOf(k),v));return r;}
    static List<Map<String,Object>> maps(Object x){return x instanceof List<?>l?l.stream().filter(v->v instanceof Map).map(ExportService::map).toList():List.of();}
    static int number(Object x){return x instanceof Number n?n.intValue():0;}
    static long longNumber(Object x){return x instanceof Number n?n.longValue():0;}
    static String time(long ms){long s=Math.max(0,ms/1000);return String.format(Locale.ROOT,"%02d:%02d",s/60,s%60);}
    static String percent(int n,int total){return total==0?"—":String.format(Locale.ROOT,"%.1f%%",n*100.0/total);}
    static String safe(Object x){return x==null?"":x.toString();}
    static String value(BigDecimal n){return n==null?"—":n.stripTrailingZeros().toPlainString();}
    static String signedDifference(BigDecimal actual,BigDecimal target){if(actual==null||target==null)return "—";var diff=actual.subtract(target);return (diff.signum()>0?"+":"")+value(diff);}
    static boolean blank(String s){return s==null||s.isBlank();}
    private static String excerpt(String s,int length){if(s==null)return "";int n=s.codePointCount(0,s.length());return n<=length?s:s.substring(0,s.offsetByCodePoints(0,length))+"…（节选）";}
    private static void text(List<Block>b,String s,int size){if(!blank(s))b.add(new Block(s,size,null,null));}
    private static void image(List<Block>b,BufferedImage image){b.add(new Block(null,0,null,image));}
    private static void table(List<Block>b,List<List<String>>rows){if(rows.size()>1)b.add(new Block(null,0,rows,null));}
    private static void panel(List<Block>b,BufferedImage image,List<List<String>>rows){b.add(new Block(null,0,rows,image));}
    private static byte[] logo() throws IOException {
        try(var input=ExportService.class.getResourceAsStream("/report/offerpilot-logo.png")) {
            if(input==null)throw new IOException("报告品牌资源缺失");
            return input.readAllBytes();
        }
    }
    private static String hex(Color color){return String.format(Locale.ROOT,"%02X%02X%02X",color.getRed(),color.getGreen(),color.getBlue());}
    private static float fontSize(int size){return size==24?26:size==12||size==11?10.5f:size;}
    private static Color textColor(int size){return size<=10?MUTED:size==11?new Color(24,77,59):size>=13?INK:BODY;}

    private static byte[] pdf(List<Block> blocks,ReportDetailResponse report)throws Exception {
        try(var document=new PDDocument();var output=new ByteArrayOutputStream()) {
            var font=PDType0Font.load(document,findCjkFont());
            try(var writer=new PdfWriter(document,font,report)) {
                for(int i=0;i<blocks.size();i++) {
                    var block=blocks.get(i);
                    if(block.overview()!=null)writer.overview(block.overview());
                    else if(block.image()!=null&&block.rows()!=null)writer.panel(block.image(),block.rows());
                    else if(block.rows()!=null)writer.table(block.rows());
                    else if(block.image()!=null) {
                        float height=PdfWriter.WIDTH*block.image().getHeight()/block.image().getWidth();
                        float caption=i+1<blocks.size()&&blocks.get(i+1).text()!=null?writer.textHeight(blocks.get(i+1).text(),blocks.get(i+1).size(),PdfWriter.WIDTH):0;
                        writer.ensure(height+caption+8);writer.image(block.image(),height);
                    } else {
                        float keep=writer.textHeight(block.text(),block.size(),PdfWriter.WIDTH);
                        if(block.size()<13)keep=Math.min(keep,fontSize(block.size())*3.3f+8);
                        if(block.size()>=13)keep=writer.headingKeepHeight(blocks,i);
                        writer.ensure(keep);writer.write(block.text(),block.size());
                    }
                }
            }
            int pages=document.getNumberOfPages();
            for(int i=0;i<pages;i++)try(var footer=new PDPageContentStream(document,document.getPage(i),PDPageContentStream.AppendMode.APPEND,true,true)) {
                footer.setStrokingColor(LINE);footer.moveTo(42,43);footer.lineTo(PDRectangle.A4.getWidth()-42,43);footer.stroke();
                footer.setNonStrokingColor(MUTED);footer.beginText();footer.setFont(font,8);footer.newLineAtOffset(42,27);footer.showText("OfferPilot 智面幻境 · 面试复盘");footer.endText();
                String count=String.format(Locale.ROOT,"%02d / %02d",i+1,pages);
                footer.setNonStrokingColor(INK);footer.beginText();footer.setFont(PDType1Font.HELVETICA,9);footer.newLineAtOffset(PDRectangle.A4.getWidth()-42-PDType1Font.HELVETICA.getStringWidth(count)*.009f,27);footer.showText(count);footer.endText();
            }
            document.getDocumentInformation().setTitle("面试能力评估报告");document.getDocumentInformation().setAuthor("OfferPilot 智面幻境");
            document.save(output);return output.toByteArray();
        }
    }

    private static byte[] docx(List<Block> blocks,ReportDetailResponse report)throws Exception {
        try(var document=new XWPFDocument();var output=new ByteArrayOutputStream()) {
            var section=document.getDocument().getBody().addNewSectPr();var size=section.addNewPgSz();size.setW(java.math.BigInteger.valueOf(11906));size.setH(java.math.BigInteger.valueOf(16838));
            var margins=section.addNewPgMar();margins.setTop(java.math.BigInteger.valueOf(1680));margins.setBottom(java.math.BigInteger.valueOf(1040));margins.setLeft(java.math.BigInteger.valueOf(840));margins.setRight(java.math.BigInteger.valueOf(840));margins.setHeader(java.math.BigInteger.valueOf(400));margins.setFooter(java.math.BigInteger.valueOf(450));
            document.getDocument().addNewBackground().setColor(hex(PAPER));
            var styles=document.createStyles();
            for(String name:List.of("Normal","Title","Heading1","Heading2")) {
                var style=CTStyle.Factory.newInstance();style.setStyleId(name);style.setType(STStyleType.PARAGRAPH);style.addNewName().setVal(name);
                var run=style.addNewRPr();run.addNewColor().setVal(hex(INK));var fonts=run.addNewRFonts();fonts.setAscii("Segoe UI");fonts.setHAnsi("Segoe UI");fonts.setEastAsia("Microsoft YaHei");
                run.addNewSz().setVal(java.math.BigInteger.valueOf(name.equals("Title")?52:name.equals("Heading1")?32:21));
                styles.addStyle(new XWPFStyle(style));
            }
            var header=document.createHeader(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
            var headerParagraph=header.createParagraph();headerParagraph.setSpacingAfter(100);wordRule(headerParagraph,false);rightTab(headerParagraph);
            var brand=headerParagraph.createRun();brand.addPicture(new ByteArrayInputStream(logo()),Document.PICTURE_TYPE_PNG,"OfferPilot",Units.toEMU(138),Units.toEMU(35.8));
            var headerText=headerParagraph.createRun();format(headerText,8,false,MUTED);headerText.setText("\t面试能力报告 · "+safe(report.getReportId()));
            var footer=document.createFooter(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);var footerParagraph=footer.createParagraph();footerParagraph.setSpacingBefore(80);wordRule(footerParagraph,true);rightTab(footerParagraph);
            var footerText=footerParagraph.createRun();format(footerText,8,false,MUTED);footerText.setText("OfferPilot 智面幻境 · 面试复盘\t");
            footerParagraph.getCTP().addNewFldSimple().setInstr("PAGE");var separator=footerParagraph.createRun();format(separator,9,false,INK);separator.setText(" / ");footerParagraph.getCTP().addNewFldSimple().setInstr("NUMPAGES");
            for(int i=0;i<blocks.size();i++) {
                var block=blocks.get(i);
                if(block.overview()!=null)wordOverview(document,block.overview());
                else if(block.rows()!=null&&block.image()!=null)wordPanel(document,block.image(),block.rows());
                else if(block.rows()!=null)wordTable(document.createTable(block.rows().size(),block.rows().get(0).size()),block.rows(),WORD_WIDTH,false);
                else if(block.image()!=null) {
                    var paragraph=document.createParagraph();paragraph.setSpacingAfter(40);paragraph.setKeepNext(true);
                    wordImage(paragraph,block.image(),WORD_WIDTH/20.0);
                } else {
                    var paragraph=document.createParagraph();wordParagraph(paragraph,block.text(),block.size());
                    if(block.size()>=13||block.text().startsWith("发现依据")||(i+1<blocks.size()&&blocks.get(i+1).image()!=null))paragraph.setKeepNext(true);
                }
            }
            document.getProperties().getCoreProperties().setTitle("面试能力评估报告");document.getProperties().getCoreProperties().setCreator("OfferPilot 智面幻境");
            document.write(output);return output.toByteArray();
        }
    }
    private static void rightTab(XWPFParagraph paragraph) {
        var properties=paragraph.getCTP().isSetPPr()?paragraph.getCTP().getPPr():paragraph.getCTP().addNewPPr();
        var tab=properties.addNewTabs().addNewTab();tab.setVal(STTabJc.RIGHT);tab.setPos(java.math.BigInteger.valueOf(WORD_WIDTH));
    }
    private static void wordRule(XWPFParagraph paragraph,boolean top) {
        var properties=paragraph.getCTP().getPPr();var borders=properties.isSetPBdr()?properties.getPBdr():properties.addNewPBdr();var border=top?borders.addNewTop():borders.addNewBottom();border.setVal(STBorder.SINGLE);border.setColor(hex(LINE));border.setSz(java.math.BigInteger.valueOf(4));border.setSpace(java.math.BigInteger.valueOf(6));
    }
    private static void format(XWPFRun run,double size,boolean bold,Color color) {
        run.setFontFamily("Segoe UI");run.setFontFamily("Microsoft YaHei",XWPFRun.FontCharRange.eastAsia);run.setFontSize(size);run.setBold(bold);run.setColor(hex(color));
    }
    private static void wordParagraph(XWPFParagraph paragraph,String text,int size) {
        paragraph.setSpacingBefore(size>=16?50:0);paragraph.setSpacingAfter(size>=16?50:60);paragraph.setSpacingBetween(size>=16?fontSize(size)*1.35:size<=10?fontSize(size)*1.5:17,LineSpacingRule.EXACT);
        paragraph.getCTP().getPPr().addNewWidowControl();paragraph.getCTP().getPPr().addNewSnapToGrid().setVal(false);
        if(size==24)paragraph.setStyle("Title");else if(size>=16)paragraph.setStyle("Heading1");else if(size==13)paragraph.setStyle("Heading2");
        var run=paragraph.createRun();format(run,fontSize(size),size>=13||size==11,textColor(size));
        String[] lines=safe(text).split("\n",-1);for(int i=0;i<lines.length;i++){if(i>0)run.addBreak();run.setText(lines[i]);}
        if(size==11){var properties=paragraph.getCTP().getPPr();properties.addNewShd().setFill(hex(text.startsWith("回答节选")?new Color(246,243,234):LIGHT));paragraph.setIndentationLeft(140);paragraph.setIndentationRight(140);}
    }
    private static void wordImage(XWPFParagraph paragraph,BufferedImage image,double width)throws Exception {
        var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);var run=paragraph.createRun();
        run.addPicture(new ByteArrayInputStream(bytes.toByteArray()),Document.PICTURE_TYPE_PNG,"报告图表",Units.toEMU(width),Units.toEMU(width*image.getHeight()/image.getWidth()));
    }
    private static void wordOverview(XWPFDocument document,Overview overview) {
        var table=document.createTable(1,2);table.setWidth(WORD_WIDTH);table.setCellMargins(160,200,160,200);noBorders(table);
        var left=table.getRow(0).getCell(0);left.setWidth("3500");left.setColor(hex(LIGHT));var right=table.getRow(0).getCell(1);right.setWidth(String.valueOf(WORD_WIDTH-3500));
        var label=left.getParagraphs().get(0);wordParagraph(label,overview.label(),10);label.setKeepNext(true);
        var score=left.addParagraph();score.setSpacingAfter(70);score.setSpacingBetween(48,LineSpacingRule.EXACT);score.setKeepNext(true);var run=score.createRun();format(run,overview.score().endsWith("%")?38:28,true,new Color(24,77,59));run.setText(overview.score());
        var note=left.addParagraph();wordParagraph(note,overview.note(),9);
        var heading=right.getParagraphs().get(0);wordParagraph(heading,"本次结论",13);heading.setKeepNext(true);
        var summary=right.addParagraph();wordParagraph(summary,overview.summary(),12);
        wordSpacer(document);
    }
    private static void wordSpacer(XWPFDocument document){var paragraph=document.createParagraph();paragraph.setSpacingBefore(0);paragraph.setSpacingAfter(0);paragraph.setSpacingBetween(4,LineSpacingRule.EXACT);format(paragraph.createRun(),1,false,MUTED);}
    private static void noBorders(XWPFTable table) {
        var properties=table.getCTTbl().getTblPr();var borders=properties.isSetTblBorders()?properties.getTblBorders():properties.addNewTblBorders();
        for(var border:List.of(borders.addNewTop(),borders.addNewBottom(),borders.addNewLeft(),borders.addNewRight(),borders.addNewInsideH(),borders.addNewInsideV()))border.setVal(STBorder.NIL);
    }
    private static void wordPanel(XWPFDocument document,BufferedImage image,List<List<String>> rows)throws Exception {
        var outer=document.createTable(1,2);outer.setWidth(WORD_WIDTH);outer.setCellMargins(0,0,0,0);noBorders(outer);
        var left=outer.getRow(0).getCell(0);int chartWidth=rows.get(0).size()==3?3200:3900;left.setWidth(String.valueOf(chartWidth));left.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        var paragraph=left.getParagraphs().get(0);paragraph.setSpacingAfter(0);wordImage(paragraph,image,(chartWidth-180)/20.0);
        var right=outer.getRow(0).getCell(1);right.setWidth(String.valueOf(WORD_WIDTH-chartWidth));right.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        var anchor=right.getParagraphs().get(0);anchor.setSpacingAfter(0);anchor.setSpacingBefore(0);anchor.setSpacingBetween(1,LineSpacingRule.EXACT);format(anchor.createRun(),1,false,MUTED);
        // Build the attached nested table directly; POI insertNewTbl leaves stale default-row wrappers.
        var table=new XWPFTable(right.getCTTc().addNewTbl(),right,rows.size(),rows.get(0).size());
        wordTable(table,rows,WORD_WIDTH-chartWidth,true);
        var trailing=right.addParagraph();trailing.setSpacingAfter(0);trailing.setSpacingBetween(1,LineSpacingRule.EXACT);format(trailing.createRun(),1,false,MUTED);
        outer.getRow(0).setCantSplitRow(false);wordSpacer(document);
    }
    private static float[] columnWidths(int count,float width) {
        float first=count==5?.40f:count==4?.33f:.50f;var result=new float[count];result[0]=width*first;for(int i=1;i<count;i++)result[i]=width*(1-first)/(count-1);return result;
    }
    private static void wordTable(XWPFTable table,List<List<String>> rows,int width,boolean compact) {
        table.setWidth(width);table.setCellMargins(compact?50:80,80,compact?50:80,80);noBorders(table);
        var properties=table.getCTTbl().getTblPr();properties.addNewTblLayout().setType(STTblLayoutType.FIXED);
        var widths=columnWidths(rows.get(0).size(),width);var grid=table.getCTTbl().getTblGrid()!=null?table.getCTTbl().getTblGrid():table.getCTTbl().addNewTblGrid();while(grid.sizeOfGridColArray()>0)grid.removeGridCol(0);for(float column:widths)grid.addNewGridCol().setW(java.math.BigInteger.valueOf(Math.round(column)));
        for(int i=0;i<rows.size();i++) {
            var row=table.getRow(i);row.setCantSplitRow(true);if(i==0)row.setRepeatHeader(true);
            for(int j=0;j<rows.get(i).size();j++) {
                var cell=row.getCell(j);cell.setWidth(String.valueOf(Math.round(widths[j])));cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);if(i==0)cell.setColor(hex(LIGHT));
                var cellProperties=cell.getCTTc().isSetTcPr()?cell.getCTTc().getTcPr():cell.getCTTc().addNewTcPr();var borders=cellProperties.addNewTcBorders();var bottom=borders.addNewBottom();bottom.setVal(STBorder.SINGLE);bottom.setColor(hex(LINE));bottom.setSz(java.math.BigInteger.valueOf(4));
                var paragraph=cell.getParagraphs().get(0);paragraph.setSpacingAfter(0);paragraph.setSpacingBefore(0);paragraph.setSpacingBetween(compact?12:13.5,LineSpacingRule.EXACT);paragraph.getCTP().getPPr().addNewSnapToGrid().setVal(false);paragraph.setAlignment(j==0?ParagraphAlignment.LEFT:ParagraphAlignment.RIGHT);paragraph.setKeepNext(i==0);
                int category=j==0&&i>0&&rows.get(0).get(0).equals("识别类别")?ReportCharts.LABELS.indexOf(rows.get(i).get(j)):-1;
                if(category>=0){var marker=paragraph.createRun();format(marker,8.5,false,ReportCharts.COLORS[category]);marker.setText("■ ");}
                var run=paragraph.createRun();format(run,compact?8.5:9.5,i==0||j==1,i==0?MUTED:j==1?new Color(24,77,59):BODY);run.setText(rows.get(i).get(j));
            }
        }
    }
    static File findCjkFont(){String configured=System.getenv("REPORT_CJK_FONT");if(configured!=null&&!configured.isBlank()){var f=new File(configured);if(f.isFile())return f;throw new ExportException("REPORT_CJK_FONT 指定的中文字体文件不存在");}String windows=System.getenv("WINDIR");for(String path:List.of((windows==null?"C:/Windows":windows)+"/Fonts/simhei.ttf","/usr/share/fonts/truetype/noto/NotoSansSC-Regular.ttf")){var f=new File(path);if(f.isFile())return f;}throw new ExportException("未找到可用的中文字体，请配置 REPORT_CJK_FONT");}

    private static class PdfWriter implements AutoCloseable {
        static final float MARGIN=42,WIDTH=PDRectangle.A4.getWidth()-84,BOTTOM=58,TOP=PDRectangle.A4.getHeight()-87;
        final PDDocument doc;final PDType0Font font;final ReportDetailResponse report;final BufferedImage brand;
        PDPageContentStream stream;float y;boolean hasContent;
        PdfWriter(PDDocument document,PDType0Font font,ReportDetailResponse report)throws IOException {doc=document;this.font=font;this.report=report;brand=ImageIO.read(new ByteArrayInputStream(logo()));newPage();}
        void newPage()throws IOException {
            if(stream!=null){if(!hasContent)return;stream.close();}
            var page=new PDPage(PDRectangle.A4);doc.addPage(page);stream=new PDPageContentStream(doc,page);rect(0,0,PDRectangle.A4.getWidth(),PDRectangle.A4.getHeight(),PAPER);
            rect(MARGIN,PDRectangle.A4.getHeight()-3,33,3,new Color(16,185,129));
            stream.drawImage(LosslessFactory.createFromImage(doc,brand),MARGIN-3,PDRectangle.A4.getHeight()-62,138,35.8f);
            String heading="面试能力报告 · "+safe(report.getReportId());draw(heading,PDRectangle.A4.getWidth()-MARGIN-font.getStringWidth(heading)*.008f,PDRectangle.A4.getHeight()-43,8,MUTED);
            rule(MARGIN,PDRectangle.A4.getHeight()-68,WIDTH);y=TOP;hasContent=false;
        }
        void ensure(float height)throws IOException {if(y-height<BOTTOM&&hasContent)newPage();}
        void rect(float x,float bottom,float width,float height,Color color)throws IOException {stream.setNonStrokingColor(color);stream.addRect(x,bottom,width,height);stream.fill();}
        void rule(float x,float yy,float width)throws IOException {stream.setStrokingColor(LINE);stream.setLineWidth(.5f);stream.moveTo(x,yy);stream.lineTo(x+width,yy);stream.stroke();}
        List<String> lines(String text,float size,float width)throws IOException {
            var result=new ArrayList<String>();for(var paragraph:safe(text).replace("\r","").replace('\t',' ').split("\n",-1)) {
                var clean=new StringBuilder();for(int codePoint:paragraph.codePoints().toArray()){String character=Character.isISOControl(codePoint)?" ":new String(Character.toChars(codePoint));try{font.encode(character);}catch(IllegalArgumentException unsupported){character="□";}clean.append(character);}
                var iterator=java.text.BreakIterator.getLineInstance(Locale.CHINA);iterator.setText(clean.toString());var line=new StringBuilder();int start=iterator.first();
                for(int end=iterator.next();end!=java.text.BreakIterator.DONE;start=end,end=iterator.next()){
                    String token=clean.substring(start,end);if(font.getStringWidth(line+token)/1000*size>width&&!line.isEmpty()){result.add(line.toString());line.setLength(0);}
                    for(int codePoint:token.codePoints().toArray()){String character=new String(Character.toChars(codePoint));if(font.getStringWidth(line+character)/1000*size>width&&!line.isEmpty()){result.add(line.toString());line.setLength(0);}line.append(character);}
                }result.add(line.toString());
            }return result;
        }
        float textHeight(String text,int size,float width)throws IOException {float fontSize=fontSize(size);return size==16||size==20?lines(text,fontSize,width).size()*fontSize*1.35f+(size==16?4:8):lines(text,fontSize,width).size()*fontSize*(size>=24?1.4f:size<=10?1.45f:1.65f)+(size>=24?8:5);}
        float headingKeepHeight(List<Block> blocks,int index)throws IOException {
            float height=0;
            for(int i=index;i<blocks.size();i++) {
                var block=blocks.get(i);
                if(block.text()!=null&&block.size()>=13){height+=textHeight(block.text(),block.size(),WIDTH);continue;}
                if(block.text()!=null&&i+1<blocks.size()&&blocks.get(i+1).image()!=null){height+=textHeight(block.text(),block.size(),WIDTH);continue;}
                if(block.image()!=null){
                    height+=block.rows()!=null?panelHeight(block.image(),block.rows())+10:WIDTH*block.image().getHeight()/block.image().getWidth()+5;
                    if(i+1<blocks.size()&&blocks.get(i+1).text()!=null&&blocks.get(i+1).size()<=10)height+=textHeight(blocks.get(i+1).text(),blocks.get(i+1).size(),WIDTH);
                }
                else if(block.rows()!=null)height+=tableHeight(block.rows().subList(0,Math.min(2,block.rows().size())),WIDTH)+10;
                else if(block.text()!=null){height+=Math.min(42,textHeight(block.text(),block.size(),WIDTH));if(block.text().startsWith("发现依据")&&i+1<blocks.size()&&blocks.get(i+1).text()!=null&&blocks.get(i+1).text().startsWith("练习动作"))height+=Math.min(42,textHeight(blocks.get(i+1).text(),blocks.get(i+1).size(),WIDTH));}
                else if(block.overview()!=null)height+=130;
                break;
            }
            return height;
        }
        void draw(String text,float x,float baseline,float size,Color color)throws IOException {stream.setNonStrokingColor(color);stream.beginText();stream.setFont(font,size);stream.newLineAtOffset(x,baseline);stream.showText(text);stream.endText();}
        void write(String text,int size)throws IOException {
            float fontSize=fontSize(size),leading=fontSize*(size>=24?1.4f:size<=10?1.45f:1.65f);var lines=lines(text,fontSize,WIDTH-(size==11?20:0));
            if(size==16||size==20){y-=size==16?1:2;rule(MARGIN,y+1,WIDTH);y-=size==16?1:2;leading=fontSize*1.35f;}
            for(String line:lines){ensure(leading+5);if(size==11)rect(MARGIN,y-leading,WIDTH,leading+3,text.startsWith("回答节选")?new Color(246,243,234):LIGHT);draw(line,MARGIN+(size==11?10:0),y-fontSize,fontSize,textColor(size));y-=leading;hasContent=true;}
            y-=size>=24?8:size==16?2:size==20?4:5;
        }
        void overview(Overview overview)throws IOException {
            float left=176,rightX=MARGIN+left+23,rightWidth=WIDTH-left-23;var summary=lines(overview.summary(),10.5f,rightWidth-12);var note=lines(overview.note(),8.5f,left-26);
            float height=Math.max(112,Math.max(44+summary.size()*17,78+note.size()*13));ensure(height+20);
            rect(MARGIN,y-height,left,height,LIGHT);rect(MARGIN,y-height,2.5f,height,new Color(16,185,129));draw(overview.label(),MARGIN+15,y-21,10,new Color(24,77,59));
            String scoreText=overview.score().replace("—","--");float scoreSize=Math.min(36,(left-30)*1000/PDType1Font.HELVETICA_BOLD.getStringWidth(scoreText));
            stream.setNonStrokingColor(new Color(24,77,59));stream.beginText();stream.setFont(PDType1Font.HELVETICA_BOLD,scoreSize);stream.newLineAtOffset(MARGIN+15,y-65);stream.showText(scoreText);stream.endText();
            for(int i=0;i<note.size();i++)draw(note.get(i),MARGIN+15,y-85-i*13,8.5f,MUTED);
            draw("本次结论",rightX,y-19,13,INK);for(int i=0;i<summary.size();i++)draw(summary.get(i),rightX,y-46-i*17,10.5f,BODY);
            y-=height+10;hasContent=true;
        }
        void image(BufferedImage image,float height)throws IOException {stream.drawImage(LosslessFactory.createFromImage(doc,image),MARGIN,y-height,WIDTH,height);y-=height+5;hasContent=true;}
        float tableHeight(List<List<String>> rows,float width)throws IOException {return tableHeight(rows,width,false);}
        float tableHeight(List<List<String>> rows,float width,boolean compact)throws IOException {float[] columns=columnWidths(rows.get(0).size(),width);float height=0;for(var row:rows){int max=1;for(int i=0;i<row.size();i++)max=Math.max(max,lines(row.get(i),9,columns[i]-12).size());height+=max*(compact?12:13.5f)+(compact?5:8);}return height;}
        float panelHeight(BufferedImage image,List<List<String>> rows)throws IOException {float chartWidth=WIDTH*(rows.get(0).size()==3?.31f:.35f);return Math.max(chartWidth*image.getHeight()/image.getWidth(),tableHeight(rows,WIDTH-chartWidth-14,true));}
        void panel(BufferedImage image,List<List<String>> rows)throws IOException {
            float chartWidth=WIDTH*(rows.get(0).size()==3?.31f:.35f),tableWidth=WIDTH-chartWidth-14,height=panelHeight(image,rows);
            if(height>TOP-BOTTOM-60){float imageHeight=Math.min(195,chartWidth*image.getHeight()/image.getWidth());ensure(imageHeight+30);stream.drawImage(LosslessFactory.createFromImage(doc,image),MARGIN,y-imageHeight,chartWidth,imageHeight);y-=imageHeight+10;hasContent=true;table(rows);return;}
            ensure(height+34);float chartHeight=chartWidth*image.getHeight()/image.getWidth();stream.drawImage(LosslessFactory.createFromImage(doc,image),MARGIN,y-(height+chartHeight)/2,chartWidth,chartHeight);
            float cursor=y;for(int i=0;i<rows.size();i++)cursor-=row(rows.get(i),MARGIN+chartWidth+14,cursor,tableWidth,i==0,0,Integer.MAX_VALUE,true);
            y-=height+8;hasContent=true;
        }
        void table(List<List<String>> rows)throws IOException {
            ensure(tableHeight(rows.subList(0,Math.min(2,rows.size())),WIDTH)+8);y-=row(rows.get(0),MARGIN,y,WIDTH,true,0,Integer.MAX_VALUE);hasContent=true;
            float[] widths=columnWidths(rows.get(0).size(),WIDTH);
            for(int i=1;i<rows.size();i++) {
                int count=1;for(int j=0;j<rows.get(i).size();j++)count=Math.max(count,lines(rows.get(i).get(j),9,widths[j]-12).size());
                for(int part=0;part<count;) {
                    int remaining=(int)((y-BOTTOM-8)/13.5f);
                    if(remaining<1){newPage();y-=row(rows.get(0),MARGIN,y,WIDTH,true,0,Integer.MAX_VALUE);hasContent=true;continue;}
                    int chunk=Math.min(count-part,Math.min(remaining,35));y-=row(rows.get(i),MARGIN,y,WIDTH,false,part,chunk);part+=chunk;
                }
            }y-=5;
        }
        float row(List<String> cells,float x,float top,float width,boolean heading,int start,int limit)throws IOException {
            return row(cells,x,top,width,heading,start,limit,false);
        }
        float row(List<String> cells,float x,float top,float width,boolean heading,int start,int limit,boolean compact)throws IOException {
            float[] columns=columnWidths(cells.size(),width);var wrapped=new ArrayList<List<String>>();int max=1;
            for(int i=0;i<cells.size();i++){var all=lines(cells.get(i),9,columns[i]-12);int end=(int)Math.min(all.size(),(long)start+limit);var slice=all.subList(Math.min(start,all.size()),end);wrapped.add(slice);max=Math.max(max,slice.size());}
            float leading=compact?12:13.5f,height=max*leading+(compact?5:8);if(heading)rect(x,top-height,width,height,LIGHT);float cellX=x;
            int category=cells.size()==3&&!heading?ReportCharts.LABELS.indexOf(cells.get(0)):-1;
            for(int j=0;j<cells.size();j++){for(int k=0;k<wrapped.get(j).size();k++){String line=wrapped.get(j).get(k);float textX=j==0?cellX+5:cellX+columns[j]-6-font.getStringWidth(line)*.009f;if(j==0&&category>=0){if(k==0)rect(cellX+5,top-(compact?13:16),5,5,ReportCharts.COLORS[category]);textX+=12;}draw(line,textX,top-(compact?13:16)-k*leading,9,heading?MUTED:j==1?new Color(24,77,59):BODY);}cellX+=columns[j];}
            rule(x,top-height,width);return height;
        }
        public void close()throws IOException {if(stream!=null)stream.close();}
    }
}
