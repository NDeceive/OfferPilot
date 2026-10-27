package com.zhimian.export;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import com.zhimian.export.ExportService.Dimension;
import static com.zhimian.export.ExportService.*;

/** Headless Java2D charts shared by PDF and Word; no desktop process or new library. */
final class ReportCharts {
    static final List<String> KEYS=List.of("neutral","happy","sad","angry","fearful","disgusted","surprised","uncertain");
    static final List<String> LABELS=List.of("平静","开心","低落","生气","害怕","厌恶","惊讶","分类不明确");
    static final Color[] COLORS={new Color(130,181,163),new Color(196,217,157),new Color(169,189,207),new Color(215,185,171),new Color(196,187,212),new Color(175,191,176),new Color(224,209,159),new Color(150,160,155)};
    private static Font font(){try{return Font.createFont(Font.TRUETYPE_FONT,findCjkFont()).deriveFont(34f);}catch(Exception e){throw new ExportException("图表字体加载失败");}}
    private static BufferedImage canvas(int height){return new BufferedImage(1400,height,BufferedImage.TYPE_INT_RGB);}
    private static Graphics2D graphics(BufferedImage im){var g=im.createGraphics();g.setColor(PAPER);g.fillRect(0,0,im.getWidth(),im.getHeight());g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setFont(font());return g;}
    static BufferedImage radar(List<Dimension> dims){
        var im=new BufferedImage(760,760,BufferedImage.TYPE_INT_RGB);var g=graphics(im);g.setFont(font().deriveFont(30f));
        double cx=380,cy=350,r=230;int n=dims.size();
        for(int level=1;level<=5;level++){g.setColor(new Color(220,229,223));var shape=new Path2D.Double();for(int i=0;i<Math.max(3,n);i++){double a=-Math.PI/2+2*Math.PI*i/Math.max(3,n),x=cx+r*level/5*Math.cos(a),y=cy+r*level/5*Math.sin(a);if(i==0)shape.moveTo(x,y);else shape.lineTo(x,y);}shape.closePath();g.draw(shape);}
        for(int i=0;i<n;i++){double a=-Math.PI/2+2*Math.PI*i/Math.max(3,n);g.setColor(LINE);g.draw(new Line2D.Double(cx,cy,cx+r*Math.cos(a),cy+r*Math.sin(a)));g.setColor(BODY);String name=dims.get(i).name();String label=name.codePointCount(0,name.length())<=7?name:String.valueOf(i+1);float x=(float)(cx+(r+66)*Math.cos(a)-g.getFontMetrics().stringWidth(label)/2.0),y=(float)(cy+(r+58)*Math.sin(a)+10);g.drawString(label,Math.max(4,Math.min(im.getWidth()-g.getFontMetrics().stringWidth(label)-4,x)),y);}
        curve(g,dims,cx,cy,r,false);curve(g,dims,cx,cy,r,true);
        g.setColor(GREEN);g.setStroke(new BasicStroke(5));g.drawLine(120,700,175,700);g.drawString("实际分",195,711);
        g.setColor(new Color(115,135,125));g.setStroke(new BasicStroke(4,BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND,10,new float[]{12,8},0));g.drawLine(400,700,455,700);g.drawString("目标分",475,711);g.dispose();return im;
    }
    private static void curve(Graphics2D g,List<Dimension>d,double cx,double cy,double r,boolean target){
        g.setColor(target?new Color(115,135,125):GREEN);g.setStroke(target?new BasicStroke(4,BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND,10,new float[]{12,8},0):new BasicStroke(5));
        Point2D.Double[] points=new Point2D.Double[d.size()];
        for(int i=0;i<d.size();i++){var score=target?d.get(i).target():d.get(i).score();if(score==null)continue;double a=-Math.PI/2+2*Math.PI*i/Math.max(3,d.size()),length=r*Math.max(0,Math.min(100,score.doubleValue()))/100;points[i]=new Point2D.Double(cx+length*Math.cos(a),cy+length*Math.sin(a));g.fill(new Ellipse2D.Double(points[i].x-6,points[i].y-6,12,12));}
        if(d.size()>1){if(!target&&java.util.Arrays.stream(points).allMatch(java.util.Objects::nonNull)){var fill=new Path2D.Double();fill.moveTo(points[0].x,points[0].y);for(int i=1;i<points.length;i++)fill.lineTo(points[i].x,points[i].y);fill.closePath();g.setColor(new Color(16,185,129,28));g.fill(fill);g.setColor(GREEN);}for(int i=0;i<d.size();i++){var p=points[i];var q=points[(i+1)%d.size()];if(p!=null&&q!=null)g.draw(new Line2D.Double(p,q));}}
    }
    static BufferedImage donut(Map<String,Object> summary){var im=new BufferedImage(760,760,BufferedImage.TYPE_INT_RGB);var g=graphics(im);var counts=map(summary.get("dominantCounts"));int total=number(summary.get("detectedCount"));double angle=90;for(int i=0;i<KEYS.size();i++){int count=number(i==7?summary.get("uncertainCount"):counts.get(KEYS.get(i)));double extent=count*360.0/Math.max(1,total);g.setColor(COLORS[i]);g.fill(new Arc2D.Double(100,100,560,560,angle,-extent,Arc2D.PIE));angle-=extent;}g.setColor(PAPER);g.fillOval(185,185,390,390);g.setColor(INK);g.setFont(new Font("SansSerif",Font.PLAIN,104));String label=String.valueOf(total);g.drawString(label,(760-g.getFontMetrics().stringWidth(label))/2,385);g.setFont(font().deriveFont(32f));g.setColor(MUTED);String caption="检出样本";g.drawString(caption,(760-g.getFontMetrics().stringWidth(caption))/2,442);g.dispose();return im;}
    static BufferedImage timeline(List<Map<String,Object>> samples,List<Map<String,Object>> questions,long start,long end,long origin){
        var im=canvas(210);var g=graphics(im);g.setFont(font().deriveFont(28f));int left=110,right=1350,top=42,bottom=148;long span=Math.max(1,end-start);
        for(int i=0;i<=2;i++){int y=bottom-(bottom-top)*i/2;g.setColor(new Color(220,229,223));g.drawLine(left,y,right,y);g.setColor(INK);g.drawString(i*50+"%",5,y+10);}
        for(int i=0;i<=4;i++){int x=left+(right-left)*i/4;g.drawString(time(start-origin+span*i/4),x-35,bottom+48);}
        g.setStroke(new BasicStroke(2,BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND,10,new float[]{8,8},0));int lastLabel=-200;
        for(var q:questions){long t=longNumber(q.get("startedAt"));if(t<start||t>end)continue;int x=left+(int)((t-start)*(right-left)/(double)span);g.setColor(new Color(170,180,175));g.drawLine(x,top,x,bottom);if(x-lastLabel>=120){g.setColor(INK);g.drawString("Q"+safe(q.get("roundNo")),x-15,top-10);lastLabel=x;}}
        Point2D.Double previous=null;long previousTime=0;g.setStroke(new BasicStroke(3));
        for(var s:samples){long t=longNumber(s.get("capturedAt"));if(t<start||t>end)continue;if(!Boolean.TRUE.equals(s.get("faceDetected"))){previous=null;continue;}var probabilities=map(s.get("probabilities"));int first=0;double highest=-1,second=-1;for(int i=0;i<7;i++){double v=probabilities.get(KEYS.get(i)) instanceof Number p?p.doubleValue():0;if(v>highest){second=highest;highest=v;first=i;}else second=Math.max(second,v);}boolean uncertain=highest<.45||highest-second<.12;g.setColor(COLORS[uncertain?7:first]);double x=left+(t-start)*(right-left)/(double)span,y=bottom-highest*(bottom-top);var current=new Point2D.Double(x,y);if(previous!=null&&t-previousTime<=6000)g.draw(new Line2D.Double(previous,current));if(uncertain){var diamond=new Path2D.Double();diamond.moveTo(x,y-8);diamond.lineTo(x+8,y);diamond.lineTo(x,y+8);diamond.lineTo(x-8,y);diamond.closePath();g.fill(diamond);}else g.fill(new Ellipse2D.Double(x-5,y-5,10,10));previous=current;previousTime=t;}
        g.dispose();return im;
    }
    private static List<String> wrap(Graphics2D g,String text,int width){var lines=new java.util.ArrayList<String>();var line=new StringBuilder();for(int cp:text.codePoints().toArray()){String ch=new String(Character.toChars(cp));if(g.getFontMetrics().stringWidth(line+ch)>width&&!line.isEmpty()){lines.add(line.toString());line.setLength(0);}line.append(ch);}if(!line.isEmpty())lines.add(line.toString());return lines;}
}
