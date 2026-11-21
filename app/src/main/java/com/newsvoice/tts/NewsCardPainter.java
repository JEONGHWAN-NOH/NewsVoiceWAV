package com.newsvoice.tts;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;

/** Draws an original, reusable 9:16 full-screen news card (no subtitle strip). */
public final class NewsCardPainter {
    private NewsCardPainter() { }
    public static final int WIDTH=1080, HEIGHT=1920;
    private static final int NAVY=Color.rgb(9,22,40), BLUE=Color.rgb(15,51,76);
    private static final int WHITE=Color.rgb(237,245,252), SUB=Color.rgb(173,197,212);
    private static final int YELLOW=Color.rgb(255,202,82), MINT=Color.rgb(107,217,205);
    private static Paint p(int c,float size,boolean bold) {
        Paint x=new Paint(Paint.ANTI_ALIAS_FLAG);x.setColor(c);x.setTextSize(size);
        x.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return x;
    }
    private static void line(Canvas c,int color,float left,float top,float right,float bottom,float thick){
        Paint x=p(color,0,false);x.setStrokeWidth(thick);c.drawLine(left,top,right,bottom,x);
    }
    private static void rounded(Canvas c,int color,float left,float top,float right,float bottom,float r){
        c.drawRoundRect(new RectF(left,top,right,bottom),r,r,p(color,0,false));
    }
    private static void wrapped(Canvas c,String text,float x,float top,float maxWidth,float lineHeight,int color,float fontSize,int maxLines,boolean bold){
        Paint paint=p(color,fontSize,bold);
        String data=text.replace('\r',' ').replace('\n',' ').trim();
        List<String> rows=new ArrayList<>();
        while(!data.isEmpty()&&rows.size()<maxLines){
            int cut=paint.breakText(data,true,maxWidth,null);
            if(cut<1)break;
            if(cut>=data.length()){rows.add(data);break;}
            // Preserve Korean words and punctuation, not arbitrary 1-char ellipses.
            int space=data.lastIndexOf(' ',cut);
            if(space>cut/2)cut=space;
            rows.add(data.substring(0,cut).trim());
            data=data.substring(cut).trim();
        }
        if(!data.isEmpty() && rows.size()>=maxLines){
            String last=rows.remove(rows.size()-1);while(last.length()>2 && paint.measureText(last+"…")>maxWidth)last=last.substring(0,last.length()-1);
            rows.add(last+"…");
        }
        for(int i=0;i<rows.size();i++)c.drawText(rows.get(i),x,top+i*lineHeight,paint);
    }

    /** Text wrapping for an entire voiceover, never ellipsizing or silently dropping lines. */
    private static List<String> wrapComplete(String text,Paint textPaint,float maxWidth) {
        List<String> all=new ArrayList<>();
        String normalized=text.replace("\r", "");
        for(String paragraph:normalized.split("\n",-1)) {
            if(paragraph.trim().isEmpty()) {all.add("");continue;}
            String rest=paragraph.trim();
            while(!rest.isEmpty()) {
                int measured=textPaint.breakText(rest,true,maxWidth,null);
                if(measured<=0) throw new IllegalArgumentException("텍스트를 배치할 수 없습니다.");
                if(measured>=rest.length()) {all.add(rest);break;}
                int cut=measured;
                int space=rest.lastIndexOf(' ',cut-1);
                if(space>cut/2)cut=space+1;
                String line=rest.substring(0,cut).trim();
                if(!line.isEmpty())all.add(line);
                rest=rest.substring(cut).trim();
            }
        }
        return all;
    }
    private static void thematicWatermark(Canvas c,String content){
        Paint ghost=p(Color.rgb(65,131,157),0,false);
        ghost.setAlpha(34);
        ghost.setStrokeWidth(13);
        ghost.setStyle(Paint.Style.STROKE);
        if(content.contains("예산")||content.contains("상원")||content.contains("공공사업")) {
            Path roof=new Path();roof.moveTo(258,1260);roof.lineTo(538,1030);roof.lineTo(838,1260);
            c.drawPath(roof,ghost);
            c.drawLine(266,1290,835,1290,ghost);
            for(int x=304;x<=748;x+=111) c.drawRoundRect(new RectF(x,1310,x+45,1517),6,6,ghost);
            c.drawLine(254,1541,862,1541,ghost);
        } else if(content.contains("중국")||content.contains("외교")||content.contains("서필리핀해")||content.contains("해양")) {
            c.drawCircle(546,1295,244,ghost);
            c.drawOval(new RectF(430,1051,664,1539),ghost);
            c.drawLine(305,1295,790,1295,ghost);
            c.drawArc(new RectF(302,1157,790,1420),20,140,false,ghost);
            c.drawArc(new RectF(302,1157,790,1420),201,138,false,ghost);
        } else if(content.contains("재판")||content.contains("탄핵")||content.contains("법정")||content.contains("소명")) {
            c.drawLine(545,1060,545,1510,ghost);
            c.drawLine(334,1172,761,1172,ghost);
            c.drawLine(370,1172,370,1350,ghost);c.drawLine(721,1172,721,1350,ghost);
            c.drawArc(new RectF(262,1284,477,1416),0,180,false,ghost);
            c.drawArc(new RectF(611,1284,830,1416),0,180,false,ghost);
            c.drawLine(390,1528,705,1528,ghost);
        } else {
            c.drawCircle(545,1295,246,ghost);
            c.drawOval(new RectF(420,1049,669,1541),ghost);
            c.drawLine(302,1295,789,1295,ghost);
        }
    }
    /** v5.3: pale topic-specific background, full narration within one 9:16 card. */
    private static Bitmap renderFullArticle(NewsStory story,int index,int count) {
        Bitmap bmp=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bmp);
        Paint bg=p(Color.rgb(246,249,252),0,false);
        bg.setShader(new LinearGradient(0,0,WIDTH,HEIGHT,
            Color.rgb(250,252,253),Color.rgb(234,244,248),Shader.TileMode.CLAMP));
        c.drawRect(0,0,WIDTH,HEIGHT,bg);
        Paint outline=p(Color.rgb(102,156,176),0,false);
        outline.setStyle(Paint.Style.STROKE);outline.setStrokeWidth(2);
        c.drawRoundRect(new RectF(44,46,1036,1868),38,38,outline);
        final int ink=Color.rgb(15,43,65);
        final int teal=Color.rgb(34,142,137);
        final int gold=Color.rgb(232,183,65);
        rounded(c,ink,63,82,1017,262,28);
        c.drawText("PHILIPPINES  /  POLITICS",195,148,p(WHITE,31,true));
        rounded(c,gold,102,126,172,138,6);
        String date=LocalDateTime.now(ZoneId.of("Asia/Manila"))
            .format(DateTimeFormatter.ofPattern("yyyy. MM. dd"));
        c.drawText(date+"  |  NEWSVOICE WAV",103,216,p(SUB,29,false));
        rounded(c,teal,101,304,428,369,18);
        c.drawText(String.format(java.util.Locale.KOREA,"뉴스 %d / %d",index+1,count),125,349,p(Color.WHITE,34,true));
        String headline=story.title.replace('\n',' ').trim();
        List<String> titleLines=null;
        Paint titleFont=null;
        for(int size=68;size>=38;size-=2) {
            titleFont=p(ink,size,true);
            titleLines=wrapComplete(headline,titleFont,859);
            if(titleLines.size()<=3)break;
        }
        if(titleLines==null||titleLines.size()>3)throw new IllegalArgumentException("기사 제목이 너무 깁니다. 제목을 줄여주세요.");
        float baseline=462;
        for(String str:titleLines){c.drawText(str,100,baseline,titleFont);baseline+=titleFont.getTextSize()+23;}
        rounded(c,gold,101,626,341,638,5);
        rounded(c,Color.rgb(205,225,234),89,701,995,1676,32);
        rounded(c,Color.WHITE,85,692,990,1667,32);
        Paint panelBorder=p(Color.rgb(196,216,224),0,false);
        panelBorder.setStyle(Paint.Style.STROKE);panelBorder.setStrokeWidth(2);
        c.drawRoundRect(new RectF(85,692,990,1667),32,32,panelBorder);
        thematicWatermark(c,story.narration);
        c.drawText("아나운서 낭독 대본 · 전체",127,759,p(teal,37,true));
        line(c,Color.rgb(214,228,234),127,810,945,810,3);
        // story.narration contains BOTH the headline and the full voiceover.
        // Keep both, as this is precisely what TextToSpeech synthesizes.
        String fullText=story.narration.replace("\n","\n\n");
        List<String> rows=null;
        Paint body=null;
        float step=0;
        for(int size=55;size>=26;size--){
            body=p(ink,size,false);
            rows=wrapComplete(fullText,body,801);
            step=size*1.55f;
            if(865+(rows.size()-1)*step<=1605)break;
        }
        if(rows==null||865+(rows.size()-1)*step>1605) {
            bmp.recycle();
            throw new IllegalArgumentException("기사 원고가 한 카드에 모두 들어가지 않습니다. 기사를 나누거나 원고 길이를 줄여주세요.");
        }
        for(int i=0;i<rows.size();i++)if(!rows.get(i).isEmpty())c.drawText(rows.get(i),128,865+i*step,body);
        line(c,Color.rgb(116,155,178),103,1749,981,1749,2);
        c.drawText("NEWSVOICE WAV  |  FULL SCRIPT",103,1810,p(ink,26,true));
        c.drawText(String.format(java.util.Locale.US,"%02d / %02d",index+1,count),866,1810,p(teal,30,true));
        rounded(c,Color.rgb(208,229,235),103,1832,977,1845,6);
        rounded(c,teal,103,1832,103+Math.round(874f*(index+1)/Math.max(1,count)),1845,6);
        return bmp;
    }

    public static Bitmap render(NewsStory story,int index,int count,int stage,String timeLabel){
        if(stage>=2 && story!=null)return renderFullArticle(story,index,count);
        Bitmap bitmap=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bitmap);
        Paint bg=p(NAVY,0,false);
        bg.setShader(new LinearGradient(0,0,WIDTH,HEIGHT,NAVY,BLUE, Shader.TileMode.CLAMP));
        c.drawRect(0,0,WIDTH,HEIGHT,bg);
        Paint outline=p(Color.rgb(46,93,111),0,false);outline.setStrokeWidth(2);outline.setStyle(Paint.Style.STROKE);
        c.drawRoundRect(new RectF(47,45,1033,1872),36,36,outline);
        c.drawCircle(560,1170,300,outline);
        c.drawCircle(560,1170,225,outline);
        c.drawCircle(1150,40,270,outline);
        rounded(c,YELLOW,102,116,164,127,6);
        c.drawText("PHILIPPINES  /  DAILY BRIEF",185,130,p(SUB,27,false));
        line(c,Color.rgb(65,115,130),101,166,978,166,2);
        String date=LocalDateTime.now(ZoneId.of("Asia/Manila")).format(DateTimeFormatter.ofPattern("yyyy. MM. dd"));
        if(stage==0){
            rounded(c,Color.rgb(28,71,84),100,212,532,273,18);
            c.drawText("PHILIPPINES",125,259,p(YELLOW,36,false));
            wrapped(c,"필리핀 뉴스\n경제 종합 브리핑",103,467,860,105,WHITE,85,3,true);
            rounded(c,YELLOW,103,705,270,716,1);
            c.drawText(date,105,865,p(YELLOW,66,false));
            c.drawText(timeLabel,106,940,p(SUB,30,false));
            c.drawText("NEWSVOICE WAV",248,1225,p(MINT,70,true));
            rounded(c,Color.rgb(21,49,66),100,1430,982,1685,30);
            wrapped(c,"뉴스 보이스 자동 음성 · 기사별 카드 · MP4",135,1525,808,59,WHITE,39,3,false);
        } else if(stage==1){
            rounded(c,Color.rgb(28,71,84),100,232,590,296,16);
            c.drawText("TODAY'S HEADLINES",131,277,p(MINT,34,true));
            wrapped(c,"오늘의 주요 소식",102,535,860,100,WHITE,82,2,true);
            line(c,YELLOW,100,646,340,646,9);
            wrapped(c,"뉴스 음성과 카드 화면을 자동으로 동기화합니다.",104,745,820,62,SUB,42,4,false);
            c.drawText(String.format(java.util.Locale.US,"총 %d개 뉴스",count),110,1508,p(YELLOW,55,true));
        } else {
            rounded(c,Color.rgb(27,69,83),100,215,582,286,19);
            c.drawText(String.format(java.util.Locale.US,"%02d  주요 소식",index+1),127,265,p(MINT,39,true));
            String headline=story.title.replace('\n',' ').trim();
            wrapped(c,headline,104,417,865,103,WHITE,76,4,true);
            line(c,YELLOW,105,710,342,710,10);
            rounded(c,Color.rgb(24,58,77),130,855,945,1275,42);
            Paint ring=p(MINT,0,false);ring.setStyle(Paint.Style.STROKE);ring.setStrokeWidth(13);
            c.drawCircle(535,1065,150,ring);
            c.drawText(String.format(java.util.Locale.US,"%02d",index+1),445,1104,p(YELLOW,107,true));
            rounded(c,Color.rgb(19,49,67),99,1345,984,1704,33);
            wrapped(c,story.preview,139,1444,797,62,WHITE,40,4,false);
        }
        line(c,Color.rgb(55,109,123),102,1762,983,1762,2);
        c.drawText("NEWSVOICE WAV  |  " + date,103,1813,p(SUB,25,false));
        c.drawText(String.format(java.util.Locale.US,"%02d / %02d",Math.max(1,index+2),count+2),872,1813,p(YELLOW,27,false));
        rounded(c,MINT,105,1847,105+Math.max(28,(875*(Math.max(1,index+2))/(count+2))),1855,4);
        return bitmap;
    }
}
