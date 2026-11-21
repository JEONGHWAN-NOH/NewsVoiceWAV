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
    /** One original news item including its entire narrated script = one video card. */
    public static List<NewsStory> paginate(NewsStory story,int newsNumber,int newsTotal){
        fitStory(story); // Same text measurement as the final rendered card.
        List<NewsStory> result=new ArrayList<>();
        result.add(story.page(story.narration,newsNumber,newsTotal,1,1));
        return result;
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

    private static void graphics(Canvas c,String title,int kind){
        int accent=Color.rgb(35,123,202);
        rounded(c,Color.rgb(8,39,89),60,612,1020,966,35);
        Paint glow=p(accent,0,false);
        glow.setShader(new LinearGradient(70,612,950,970,
            Color.rgb(12,64,131),Color.rgb(5,24,53),Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(61,613,1019,965),35,35,glow);
        Paint stroke=p(Color.rgb(75,171,250),0,false);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(5f);
        c.drawRoundRect(new RectF(62,614,1018,964),35,35,stroke);
        Paint soft=p(Color.rgb(14,107,193),0,false);
        soft.setAlpha(105);
        for(int r=210;r<=410;r+=75)c.drawCircle(780,820,r,soft);
        if(kind!=NewsStory.NEWS){
            Paint ring=p(YELLOW,0,false);ring.setStyle(Paint.Style.STROKE);ring.setStrokeWidth(16);
            c.drawCircle(520,800,116,ring);
            rounded(c,WHITE,487,723,552,850,22);
            line(c,WHITE,519,852,519,890,12);
            line(c,WHITE,465,890,572,890,12);
            String label=kind==NewsStory.OPENING?"방송 시작":
                kind==NewsStory.ANCHOR?"앵커 한마디":"방송 마감";
            c.drawText(label,100,683,p(YELLOW,31,true));
            return;
        }
        String lower=title;
        boolean fuel=lower.contains("유가")||lower.contains("경유")||
            lower.contains("휘발유")||lower.contains("연료")||lower.contains("석유");
        boolean economy=lower.contains("투자")||lower.contains("경제")||
            lower.contains("기업")||lower.contains("산업")||lower.contains("수출");
        boolean health=lower.contains("교육")||lower.contains("학교")||
            lower.contains("복지")||lower.contains("보건");
        String category=fuel?"유류·생활경제":economy?"경제·산업":
            health?"사회·교육":"필리핀 주요 현안";
        c.drawText(category,105,685,p(YELLOW,34,true));
        if(fuel){
            rounded(c,Color.rgb(23,114,215),355,729,575,908,25);
            rounded(c,WHITE,384,750,546,821,8);
            line(c,YELLOW,600,752,664,802,13);
            line(c,YELLOW,664,802,754,791,13);
            line(c,YELLOW,754,791,839,875,13);
            line(c,YELLOW,839,875,896,863,13);
            line(c,YELLOW,896,863,907,887,13);
        }else if(economy){
            int[] heights={70,110,170,205,250};
            for(int i=0;i<heights.length;i++){
                rounded(c,Color.rgb(40,133+i*14,211),337+121*i,925-heights[i],
                    415+121*i,926,12);
            }
            line(c,YELLOW,352,838,822,694,10);
        }else if(health){
            rounded(c,Color.rgb(28,119,211),344,732,819,906,28);
            for(int i=0;i<5;i++)line(c,WHITE,385,766+i*26,775,766+i*26,6);
            rounded(c,YELLOW,500,681,652,736,22);
        }else{
            Paint globe=p(Color.rgb(64,170,245),0,false);
            globe.setStrokeWidth(6);globe.setStyle(Paint.Style.STROKE);
            c.drawCircle(600,825,120,globe);
            c.drawOval(new RectF(540,705,660,945),globe);
            c.drawLine(480,825,720,825,globe);
            c.drawCircle(790,740,14,p(YELLOW,0,true));
        }
    }

    /** Full text news reading card; no article photograph or segmented narration. */
    /**
     * The parser includes the headline in narration so TTS can read it aloud.
     * Display the headline once at the top; the prose below is unchanged.
     */
    private static String prose(NewsStory story){
        return story.spokenText();
    }
    /** No image column; one continuous full-width text block for the complete story. */
    private static final class ReadingFit{
        final float x,top,size,step;
        final List<String> lines;
        ReadingFit(float x,float top,float size,float step,List<String> lines){
            this.x=x;this.top=top;this.size=size;this.step=step;this.lines=lines;
        }
    }
    /**
     * The exact same text layout drives preflight and final rendering.
     * Larger text is preferred. No summarization, ellipsis, or multi-page split.
     */
    private static ReadingFit fitStory(NewsStory story){
        String body=prose(story);
        for(int size=69;size>=27;size--){
            Paint font=p(Color.WHITE,size,true);
            List<String> lines=wrapComplete(body,font,891f);
            float step=size*1.35f;
            // Continuous reading area below its section label (y = 659..1570).
            if(lines.size()>0 && lines.size()*step<=894f)
                return new ReadingFit(92,658,size,step,lines);
        }
        throw new IllegalArgumentException("이 뉴스 대본이 한 화면의 읽기 좋은 글씨 크기를 초과했습니다. "
            +"대본을 임의로 축약하거나 숨기지 않았습니다. 기사 원고를 검토해 주세요: "+story.title);
    }

    private static void stroke(Canvas c,float x1,float y1,float x2,float y2,int color,float width,float radius){
        Paint pen=p(color,0,false);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(width);
        c.drawRoundRect(new RectF(x1,y1,x2,y2),radius,radius,pen);
    }
    /** Very faint decorative watermark only; never reserves text width. */
    private static void drawWatermark(Canvas c,NewsStory story){
        Paint faint=p(Color.rgb(65,135,211),0,false);
        faint.setAlpha(20);
        faint.setStrokeWidth(16f);
        faint.setStyle(Paint.Style.STROKE);
        float cx=800,cy=1316;
        String title=story.title;
        boolean institutional=title.contains("상원")||title.contains("예산")||
            title.contains("정부")||title.contains("공공사업")||title.contains("국회");
        boolean economic=title.contains("투자")||title.contains("산업")||
            title.contains("경제")||title.contains("기업")||title.contains("수출")||
            title.contains("유가")||title.contains("경유");
        if(institutional){
            Path roof=new Path();roof.moveTo(cx-176,cy-110);
            roof.lineTo(cx,cy-231);roof.lineTo(cx+176,cy-110);
            c.drawPath(roof,faint);
            c.drawLine(cx-186,cy+187,cx+186,cy+187,faint);
            for(int i=-1;i<=1;i++){
                float x=cx+115*i;
                c.drawRoundRect(new RectF(x-30,cy-76,x+30,cy+163),12,12,faint);
            }
        }else if(economic){
            for(int i=0;i<4;i++){
                float x=570+i*115;
                c.drawRoundRect(new RectF(x,1444-i*70,x+68,1480),7,7,faint);
            }
            c.drawLine(565,1409,969,1080,faint);
        }else{
            c.drawCircle(cx,cy,177,faint);
            c.drawOval(new RectF(cx-86,cy-177,cx+86,cy+177),faint);
            c.drawLine(cx-177,cy,cx+177,cy,faint);
        }
    }
    private static void drawProseLine(Canvas c,String row,float x,float baseline,Paint white){
        Paint yellow=p(Color.rgb(255,210,46),white.getTextSize(),true);
        java.util.regex.Matcher m=java.util.regex.Pattern
            .compile("[0-9][0-9,.:]*").matcher(row);
        int pos=0;float drawX=x;
        while(m.find()){
            if(m.start()>pos){
                String segment=row.substring(pos,m.start());
                c.drawText(segment,drawX,baseline,white);drawX+=white.measureText(segment);
            }
            String digits=m.group();
            c.drawText(digits,drawX,baseline,yellow);drawX+=yellow.measureText(digits);
            pos=m.end();
        }
        if(pos<row.length())c.drawText(row.substring(pos),drawX,baseline,white);
    }

    /** Full TTS script inside ONE continuous full-width panel. */
    private static Bitmap renderFullArticle(NewsStory story,int index,int count,NewsCoverMeta meta){
        ReadingFit layout=fitStory(story);
        Bitmap bitmap=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bitmap);
        int white=Color.WHITE,yellow=Color.rgb(255,210,46),blue=Color.rgb(11,48,101);
        Paint background=p(Color.rgb(5,17,44),0,false);
        background.setShader(new LinearGradient(0,0,0,HEIGHT,
            Color.rgb(9,37,87),Color.rgb(2,11,31),Shader.TileMode.CLAMP));
        c.drawRect(0,0,WIDTH,HEIGHT,background);
        rounded(c,yellow,0,0,WIDTH,14,0);
        String english=meta.clock.startsWith("오전")?
            "GWANGMYEONG  |  MORNING NEWS":
            meta.clock.startsWith("오후")?
            "GWANGMYEONG  |  AFTERNOON NEWS":
            "GWANGMYEONG  |  CITY NEWS";
        Paint engPaint=p(white,33,true);
        while(engPaint.getTextSize()>24&&engPaint.measureText(english)>969)
            engPaint.setTextSize(engPaint.getTextSize()-1f);
        c.drawText(english,51,93,engPaint);
        c.drawText("오늘의 주요 뉴스",50,178,p(yellow,69,true));
        rounded(c,yellow,51,196,1031,204,3);
        rounded(c,blue,43,233,1037,453,30);
        stroke(c,43,233,1037,453,Color.rgb(41,157,241),4,30);
        // Approved design: yellow news-category tab on the left, headline on right.
        rounded(c,yellow,62,280,309,395,22);
        String title=story.title;
        boolean economy=title.contains("경제")||title.contains("예산")||
            title.contains("공공사업")||title.contains("투자")||
            title.contains("유가")||title.contains("휘발유")||
            title.contains("경유")||title.contains("산업")||
            title.contains("수출")||title.contains("기업");
        String label=story.kind==NewsStory.OPENING?"시작 인사":
            story.kind==NewsStory.ANCHOR?"앵커 한마디":
            story.kind==NewsStory.CLOSING?"마감 인사":
            (economy?"경제 뉴스 ":"주요 뉴스 ")+story.articleNumber;
        Paint labelPaint=p(Color.rgb(6,23,47),30,true);
        while(labelPaint.getTextSize()>23&&labelPaint.measureText(label)>226)
            labelPaint.setTextSize(labelPaint.getTextSize()-1);
        c.drawText(label,77,350,labelPaint);
        line(c,Color.rgb(131,190,245),325,268,325,414,3);
        Paint headline=null;List<String> headlines=null;
        for(int size=58;size>=30;size--){
            headline=p(white,size,true);
            headlines=wrapComplete(title,headline,673f);
            if(headlines.size()<=3 && headlines.size()*size*1.22f<=177)break;
        }
        if(headlines==null||headlines.size()>3)
            throw new IllegalArgumentException("제목이 한 화면을 초과합니다: "+title);
        float lineHeight=headline.getTextSize()*1.22f;
        float titleY=325+(2-headlines.size())*lineHeight/2f;
        for(String t:headlines){c.drawText(t,348,titleY,headline);titleY+=lineHeight;}
        rounded(c,Color.rgb(8,43,94),44,469,1036,1598,29);
        stroke(c,44,469,1036,1598,Color.rgb(40,153,237),4,29);
        // Entire width is now available for the complete announcer script.
        String category=story.kind==NewsStory.OPENING?"시작 인사":
            story.kind==NewsStory.ANCHOR?"앵커 한마디":
            story.kind==NewsStory.CLOSING?"마감 인사":
            economy?"경제·산업":"광명시 주요 뉴스";
        c.drawText(category,89,557,p(yellow,43,true));
        line(c,Color.rgb(69,143,220),87,586,990,586,2);
        drawWatermark(c,story);
        Paint body=p(white,layout.size,true);
        float y=layout.top;
        for(String row:layout.lines){
            if(!row.isEmpty())drawProseLine(c,row,layout.x,y,body);
            y+=layout.step;
        }
        if(y-layout.step>1570)throw new IllegalStateException("기사 본문이 카드 경계를 넘었습니다.");
        int total=Math.max(1,story.articleCount);
        int completed=NewsBroadcastSegments.completedNews(story);
        int remain=NewsBroadcastSegments.remainingNews(story);
        rounded(c,yellow,46,1632,257,1700,14);
        c.drawText("뉴스 진행",65,1679,p(Color.rgb(9,24,45),41,true));
        c.drawText(completed+" / "+total,283,1678,p(white,46,true));
        rounded(c,Color.rgb(96,113,147),485,1648,896,1688,20);
        if(completed>0)
            rounded(c,yellow,485,1648,485+411f*completed/total,1688,20);
        c.drawText(Math.round(completed*100f/total)+"%",919,1679,p(white,37,true));
        String remaining=story.kind==NewsStory.OPENING?
            "뉴스 시작 전 · 남은 뉴스 "+remain+"건":
            story.kind==NewsStory.NEWS?
            "현재 "+completed+"번째 뉴스 · 이후 남은 뉴스 "+remain+"건":
            "본 뉴스 완료 · 남은 뉴스 0건";
        c.drawText(remaining,55,1763,p(white,33,true));
        line(c,Color.rgb(39,110,192),46,1801,1031,1801,3);
        c.drawText(meta.date,53,1864,p(white,40,true));
        Paint clock=p(Color.rgb(218,230,247),31,true);
        while(clock.getTextSize()>20&&clock.measureText(meta.clock)>450)
            clock.setTextSize(clock.getTextSize()-1f);
        c.drawText(meta.clock,598,1864,clock);
        return bitmap;
    }
    /** Original city-themed vector cover, not a depiction of a real station photograph. */
    private static Bitmap renderCityCover(NewsCoverMeta meta,int count,int stage){
        Bitmap bitmap=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bitmap);
        final int white=Color.WHITE,gold=Color.rgb(255,209,42),navy=Color.rgb(3,17,46);
        Paint background=p(navy,0,false);
        background.setShader(new LinearGradient(0,0,0,HEIGHT,
            Color.rgb(7,40,94),Color.rgb(2,13,38),Shader.TileMode.CLAMP));
        c.drawRect(0,0,WIDTH,HEIGHT,background);
        rounded(c,gold,0,0,WIDTH,19,0);
        c.drawText("GWANGMYEONG  |  CITY NEWS",57,123,p(white,45,true));
        rounded(c,gold,57,153,1015,163,4);
        rounded(c,Color.rgb(12,55,119),56,217,1026,312,20);
        stroke(c,56,217,1026,312,Color.rgb(53,150,238),4,20);
        String date=meta.date+"  |  "+meta.clock;
        Paint dateFont=p(white,47,true);
        while(dateFont.getTextSize()>28&&dateFont.measureText(date)>904)
            dateFont.setTextSize(dateFont.getTextSize()-1);
        c.drawText(date,87,283,dateFont);

        rounded(c,Color.rgb(8,43,101),48,376,1032,1270,34);
        stroke(c,48,376,1032,1270,Color.rgb(48,153,242),5,34);
        if(stage==0){
            c.drawText("광명시",107,583,p(white,151,true));
            rounded(c,gold,85,703,990,858,20);
            Paint title=p(Color.rgb(5,24,51),87,true);
            String name="뉴스·종합 브리핑";
            while(title.getTextSize()>62&&title.measureText(name)>829)
                title.setTextSize(title.getTextSize()-1);
            c.drawText(name,120,807,title);
            c.drawText("Gwangmyeong City News Briefing",106,970,p(white,35,true));
            rounded(c,gold,102,1091,978,1212,20);
            c.drawText("오늘의 핵심 뉴스",191,1175,p(Color.rgb(5,24,51),79,true));
        }else{
            c.drawText("오늘의 주요 소식",96,614,p(gold,81,true));
            rounded(c,gold,92,652,994,661,4);
            c.drawText("광명시 종합 뉴스",105,817,p(white,72,true));
            c.drawText("총 "+count+"건의 주요 뉴스를 전해드립니다.",108,962,p(white,43,true));
            c.drawText("GWANGMYEONG TODAY",106,1169,p(Color.rgb(125,185,250),41,true));
        }
        // Abstract city buildings and a train motif; no unverified location imagery.
        Paint city=p(Color.rgb(24,98,181),0,false);city.setAlpha(165);
        for(int i=0;i<10;i++){
            int x=45+i*106,top=1560-(145+(i*47)%286);
            rounded(c,city.getColor(),x,top,x+62+(i%3)*9,1575,8);
            for(int row=0;row<3;row++)
                rounded(c,Color.rgb(84,158,222),x+15,top+27+row*43,x+34,top+43+row*43,2);
        }
        rounded(c,Color.rgb(12,59,127),84,1550,1000,1669,28);
        stroke(c,84,1550,1000,1669,Color.rgb(70,154,236),4,28);
        for(int i=0;i<8;i++)rounded(c,Color.rgb(97,175,242),125+i*107,1578,166+i*107,1617,7);
        line(c,gold,139,1642,942,1642,8);
        rounded(c,Color.rgb(7,30,65),59,1770,1022,1880,18);
        String footer=stage==0?
            "시정 · 경제 · 도시개발 · 교육 · 복지   |   뉴스 "+count+"건":
            "광명시 뉴스 "+count+"건   |   제목 제외, 본문만 낭독";
        Paint footerFont=p(white,37,true);
        while(footerFont.getTextSize()>27&&footerFont.measureText(footer)>906)
            footerFont.setTextSize(footerFont.getTextSize()-1);
        c.drawText(footer,83,1840,footerFont);
        return bitmap;
    }
    public static Bitmap render(Context context,NewsStory story,int index,int count,
                                int stage,NewsCoverMeta meta){
        if(stage>=2&&story!=null)return renderFullArticle(story,index,count,meta);
        if(stage==0||stage==1)return renderCityCover(meta,count,stage);
        throw new IllegalArgumentException("지원하지 않는 뉴스 화면 단계: "+stage);
    }
}
