package com.newsvoice.tts;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import java.io.InputStream;
import java.io.IOException;
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
        ghost.setAlpha(13);
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
    /** Same font/line layout is used by pagination and actual rendering. */
    private static final float BODY_SIZE=44f, BODY_STEP=66f, BODY_WIDTH=795f;
    private static final int MAX_BODY_LINES=11;
    public static List<NewsStory> paginate(NewsStory article,int articleNo,int articleCount){
        Paint font=p(Color.rgb(22,44,66),BODY_SIZE,false);
        List<String> chunks=NewsPageSplitter.split(article.narration,
            text -> wrapComplete(text,font,BODY_WIDTH).size()<=MAX_BODY_LINES);
        List<NewsStory> pages=new ArrayList<>();
        for(int i=0;i<chunks.size();i++)
            pages.add(article.page(chunks.get(i),articleNo,articleCount,i+1,chunks.size()));
        return pages;
    }
    public static List<NewsStory> paginateBroadcast(List<NewsStory> sections){
        int total=NewsBroadcastSegments.newsCount(sections),counted=0;
        if(total<1)throw new IllegalArgumentException("전할 뉴스가 없습니다.");
        List<NewsStory> pages=new ArrayList<>();
        for(NewsStory segment:sections){
            int newsIndex=segment.kind==NewsStory.NEWS?++counted:
                segment.kind==NewsStory.OPENING?0:total;
            pages.addAll(paginate(segment,newsIndex,total));
        }
        return pages;
    }
    private static int pastelColor(NewsStory s){
        String q=s.title+" "+s.narration;
        if(q.contains("외교")||q.contains("해양")||q.contains("서필리핀해")||q.contains("중국"))return Color.rgb(230,244,251);
        if(q.contains("탄핵")||q.contains("재판")||q.contains("법원")||q.contains("소명"))return Color.rgb(244,238,251);
        if(q.contains("상원")||q.contains("예산")||q.contains("공공사업"))return Color.rgb(252,242,226);
        return Color.rgb(232,247,240);
    }
    private static Bitmap renderFullArticle(NewsStory story,int index,int count,NewsCoverMeta meta){
        Bitmap bmp=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bmp);
        final int ink=Color.rgb(20,43,64), blue=Color.rgb(40,98,124);
        final int teal=Color.rgb(20,112,111), accent=Color.rgb(211,161,65);
        Paint bg=p(Color.WHITE,0,false);
        bg.setShader(new LinearGradient(0,0,WIDTH,HEIGHT,Color.rgb(252,253,254),
            pastelColor(story),Shader.TileMode.CLAMP));
        c.drawRect(0,0,WIDTH,HEIGHT,bg);
        // Decorative abstract dots remain outside the high-contrast manuscript panel.
        Paint pastelRing=p(teal,0,false);pastelRing.setAlpha(17);
        pastelRing.setStyle(Paint.Style.STROKE);pastelRing.setStrokeWidth(30);
        c.drawCircle(981,168,270,pastelRing);c.drawCircle(85,1744,195,pastelRing);
        Paint border=p(Color.rgb(200,216,222),0,false);
        border.setStrokeWidth(3);border.setStyle(Paint.Style.STROKE);
        c.drawRoundRect(new RectF(45,45,1035,1873),36,36,border);
        rounded(c,Color.rgb(230,242,244),82,90,998,224,25);
        c.drawText("필리핀 뉴스 | NEWSVOICE WAV",116,157,p(ink,39,true));
        String date=meta.date;
        c.drawText(date,118,208,p(blue,27,false));
        int articleNumber=story.articleNumber;
        int articleCount=Math.max(1,story.articleCount>0?story.articleCount:count);
        String header=story.kind==NewsStory.OPENING?"시작 인사":
            story.kind==NewsStory.ANCHOR?"앵커 한마디":
            story.kind==NewsStory.CLOSING?"마감 인사":
            "뉴스 "+articleNumber+" / "+articleCount;
        rounded(c,Color.rgb(226,243,240),100,262,540,321,19);
        c.drawText(header,124,304,p(teal,34,true));
        rounded(c,Color.rgb(239,235,249),631,262,977,321,19);
        c.drawText("화면 "+story.pageNumber+" / "+story.pageCount,652,304,p(ink,32,true));
        List<String> titleRows=null;Paint titlePaint=null;
        for(int size=62;size>=32;size-=2){
            titlePaint=p(ink,size,true);
            titleRows=wrapComplete(story.title.replace("\n"," "),titlePaint,859);
            if(titleRows.size()<=3)break;
        }
        if(titleRows==null||titleRows.size()>3)throw new IllegalArgumentException("제목이 너무 깁니다.");
        float titleBase=425;
        for(String row:titleRows){c.drawText(row,104,titleBase,titlePaint);titleBase+=titlePaint.getTextSize()+12;}
        rounded(c,accent,103,630,344,640,5);
        // The page text always sits on an opaque, warm-white panel.
        rounded(c,Color.WHITE,86,705,995,1638,30);
        Paint panelOutline=p(Color.rgb(209,224,228),0,false);
        panelOutline.setStyle(Paint.Style.STROKE);panelOutline.setStrokeWidth(2);
        c.drawRoundRect(new RectF(86,705,995,1638),30,30,panelOutline);
        c.drawText(story.kind==NewsStory.OPENING?"시작 인사":
            story.kind==NewsStory.ANCHOR?"앵커 한마디":
            story.kind==NewsStory.CLOSING?"마감 인사":"아나운서 낭독 대본",
            128,774,p(teal,37,true));
        line(c,Color.rgb(218,231,232),124,810,948,810,3);
        // Topic-specific watermark is extremely pale and never compromises readability.
        c.save(); c.clipRect(98,824,980,1614);
        thematicWatermark(c,story.title);
        c.restore();
        Paint body=p(ink,BODY_SIZE,false);
        List<String> rows=wrapComplete(story.narration,body,BODY_WIDTH);
        if(rows.size()>MAX_BODY_LINES){
            bmp.recycle();
            throw new IllegalArgumentException("원고 페이지 분할에 실패했습니다. 카드 여백을 확인하세요.");
        }
        float baseline=875;
        for(String row:rows){
            if(!row.isEmpty())c.drawText(row,128,baseline,body);
            baseline+=BODY_STEP;
        }
        if(story.pageCount>1) {
            c.drawText(story.pageNumber<story.pageCount?"다음 화면에서 이어집니다.":"이번 기사의 낭독이 끝났습니다.",
                130,1597,p(teal,26,true));
        }
        // Show globally completed NEWS ITEMS, not the number of split slide pages.
        int completed=NewsBroadcastSegments.completedNews(story);
        int remaining=NewsBroadcastSegments.remainingNews(story);
        String label=story.kind==NewsStory.OPENING?
            "뉴스 시작 전 · 남은 뉴스 "+remaining+"건":
            story.kind==NewsStory.NEWS?
            "뉴스 "+articleNumber+"/"+articleCount+" · 이후 남은 뉴스 "+remaining+"건":
            "본 뉴스 완료 · 남은 뉴스 0건";
        line(c,Color.rgb(198,216,223),102,1700,982,1700,2);
        c.drawText(label,105,1776,p(ink,34,true));
        c.drawText(Math.round(100f*completed/articleCount)+"%",880,1822,p(teal,28,true));
        rounded(c,Color.rgb(214,232,234),105,1846,977,1866,9);
        if(completed>0)rounded(c,teal,105,1846,
            105+Math.round(872f*completed/articleCount),1866,9);
        return bmp;
    }


    /** Pixel artwork is an original offline image; date/slot/count are live Android overlays. */
    private static Bitmap renderBroadcastCover(Context context,NewsCoverMeta meta,int articleCount) {
        Bitmap cover=null;
        try(InputStream input=context.getAssets().open("philippines_cover.webp")){
            cover=BitmapFactory.decodeStream(input);
            if(cover==null)return null;
            Bitmap output=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(output);
            Paint image=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(cover,null,new Rect(0,0,WIDTH,HEIGHT),image);
            cover.recycle();

            // Date strip on top (drawn fresh for every video, never burned into the artwork).
            rounded(canvas,Color.argb(209,7,30,60),215,142,987,217,15);
            Paint date=p(WHITE,45,true);
            date.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
            String timestamp=meta.date+" | "+meta.clock;
            while(date.getTextSize()>27&&date.measureText(timestamp)>730f)
                date.setTextSize(date.getTextSize()-1f);
            canvas.drawText(timestamp,234,197,date);

            Paint country=p(Color.WHITE,150,true);
            country.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
            String line1="필리핀";
            canvas.drawText(line1,195,539,country);

            Paint main=p(Color.rgb(6,23,45),97,true);
            main.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
            String title="뉴스·경제 브리핑";
            while(main.getTextSize()>49&&main.measureText(title)>915f)
                main.setTextSize(main.getTextSize()-1f);
            canvas.drawText(title,86,768,main);

            Paint english=p(WHITE,39,true);
            english.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
            String en="Philippines Daily News Briefing";
            while(english.getTextSize()>26&&english.measureText(en)>810)
                english.setTextSize(english.getTextSize()-1f);
            float enX=(WIDTH-english.measureText(en))/2f;
            canvas.drawText(en,enX,879,english);

            Paint headline=p(Color.rgb(7,26,46),79,true);
            headline.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
            String today="오늘의 핵심 뉴스";
            while(headline.getTextSize()>48&&headline.measureText(today)>610)
                headline.setTextSize(headline.getTextSize()-1f);
            float hx=(WIDTH-headline.measureText(today))/2f;
            canvas.drawText(today,hx,1025,headline);

            rounded(canvas,Color.argb(221,5,28,55),96,1810,986,1896,21);
            Paint footer=p(Color.WHITE,39,true);
            String end="정치 · 경제 · 사회 · 사건사고   |   주요 뉴스 "+articleCount+"건";
            while(footer.getTextSize()>25&&footer.measureText(end)>850)
                footer.setTextSize(footer.getTextSize()-1f);
            canvas.drawText(end,116,1866,footer);
            return output;
        }catch(IOException|RuntimeException e){
            if(cover!=null&&!cover.isRecycled())cover.recycle();
            return null; // Gracefully use the original vector cover if the asset cannot be read.
        }
    }

    public static Bitmap render(Context context,NewsStory story,int index,int count,int stage,NewsCoverMeta meta){
        if(stage>=2 && story!=null)return renderFullArticle(story,index,count,meta);
        if(stage==0){
            Bitmap artwork=renderBroadcastCover(context,meta,count);
            if(artwork!=null)return artwork;
        }
        Bitmap bitmap=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bitmap);
        Paint bg=p(NAVY,0,false);
        bg.setShader(new LinearGradient(0,0,WIDTH,HEIGHT,
            Color.rgb(231,245,248),Color.rgb(249,239,228),Shader.TileMode.CLAMP));
        c.drawRect(0,0,WIDTH,HEIGHT,bg);
        Paint outline=p(Color.rgb(187,209,218),0,false);outline.setStrokeWidth(2);outline.setStyle(Paint.Style.STROKE);
        c.drawRoundRect(new RectF(47,45,1033,1872),36,36,outline);
        c.drawCircle(560,1170,300,outline);
        c.drawCircle(560,1170,225,outline);
        c.drawCircle(1150,40,270,outline);
        rounded(c,YELLOW,102,116,164,127,6);
        c.drawText("PHILIPPINES  /  DAILY BRIEF",185,130,p(SUB,27,false));
        line(c,Color.rgb(65,115,130),101,166,978,166,2);
        String date=meta.date;
        String timeLabel=meta.clock;
        if(stage==0){
            rounded(c,Color.rgb(28,71,84),100,212,532,273,18);
            c.drawText("PHILIPPINES",125,259,p(YELLOW,36,false));
            wrapped(c,"필리핀 뉴스\n경제 종합 브리핑",103,467,860,105,Color.rgb(20,43,64),85,3,true);
            rounded(c,YELLOW,103,705,270,716,1);
            c.drawText(date,105,865,p(Color.rgb(31,74,102),66,true));
            c.drawText(timeLabel,106,940,p(Color.rgb(45,90,115),30,false));
            c.drawText("NEWSVOICE WAV",248,1225,p(Color.rgb(18,100,103),70,true));
            rounded(c,Color.rgb(21,49,66),100,1430,982,1685,30);
            wrapped(c,"뉴스 보이스 자동 음성 · 기사별 카드 · MP4",135,1525,808,59,WHITE,39,3,false);
        } else if(stage==1){
            rounded(c,Color.rgb(28,71,84),100,232,590,296,16);
            c.drawText("TODAY'S HEADLINES",131,277,p(MINT,34,true));
            wrapped(c,"오늘의 주요 소식",102,535,860,100,Color.rgb(20,43,64),82,2,true);
            line(c,YELLOW,100,646,340,646,9);
            wrapped(c,"뉴스 음성과 카드 화면을 자동으로 동기화합니다.",104,745,820,62,Color.rgb(43,89,111),42,4,false);
            c.drawText(String.format(java.util.Locale.US,"총 %d개 뉴스",count),110,1508,p(Color.rgb(29,89,113),55,true));
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
