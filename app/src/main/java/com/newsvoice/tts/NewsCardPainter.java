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
    /** A whole news item is one large-print vertically scrolling slide, not a tiny card. */
    public static List<NewsStory> paginate(NewsStory story,int newsNumber,int newsTotal){
        if(story.spokenText().trim().isEmpty())
            throw new IllegalArgumentException("낭독할 본문이 없습니다.");
        // The original title remains screen-only; scrolling uses the complete unabridged body.
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

    /** Full-width news reading card; long articles may use consecutive cards. */
    /**
     * The displayed headline is explicitly excluded from synthesized speech.
     * Display the headline once at the top; the prose below is unchanged.
     */
    private static String prose(NewsStory story){
        return story.spokenText();
    }
    /** No image column; one continuous full-width text block per card. */
    private static final class ReadingFit{
        final float x,top,size,step;
        final List<String> lines;
        ReadingFit(float x,float top,float size,float step,List<String> lines){
            this.x=x;this.top=top;this.size=size;this.step=step;this.lines=lines;
        }
    }
    /** Same real Canvas metrics are used by the splitter and final renderer. */
    private static ReadingFit tryFitNarration(String body){
        if(body==null||body.trim().isEmpty())return null;
        for(int size=69;size>=27;size--){
            Paint font=p(Color.WHITE,size,true);
            List<String> lines=wrapComplete(body,font,891f);
            float step=size*1.35f;
            if(lines.size()>0 && lines.size()*step<=894f)
                return new ReadingFit(92,658,size,step,lines);
        }
        return null;
    }
    private static ReadingFit fitStory(NewsStory story){
        ReadingFit fit=tryFitNarration(prose(story));
        if(fit!=null)return fit;
        throw new IllegalArgumentException("자동 분할 후에도 배치할 수 없습니다: "+story.title);
    }

    private static final float SCROLL_FONT=56f;
    private static final float SCROLL_STEP=84f;
    private static final float SCROLL_TOP=1110f; // current spoken line baseline sits near the screen center
    private static final float SCROLL_BOTTOM=1568f;
    private static final float SCROLL_FOCUS_ROWS=3.2f;

    /** Prepared once per article: the voice script is never ellipsized or shrunk. */
    public static final class ScrollPlan {
        final NewsStory story;
        final List<String> lines;
        final int[] rowLengths,rowStarts,sentenceStarts;
        final int totalCharacters;
        final float maxOffset;
        private ScrollPlan(NewsStory story,List<String> lines,int[] lengths,
                           int[] starts,int[] sentenceStarts,int characters,float maxOffset){
            this.story=story;this.lines=lines;this.rowLengths=lengths;
            this.rowStarts=starts;this.sentenceStarts=sentenceStarts;
            this.totalCharacters=characters;this.maxOffset=maxOffset;
        }
        /** One immutable row-centered speed curve per spoken article. */
        public NewsScrollMath.AudioCurve centeredAudioCurve(
            long voiceStartUs,long[] sentenceEndsUs){
            if(story.hasTransition()){
                voiceStartUs=sentenceEndsUs[0];
                sentenceEndsUs=java.util.Arrays.copyOfRange(sentenceEndsUs,1,sentenceEndsUs.length);
            }
            return NewsScrollMath.centeredAudioCurve(voiceStartUs,
                sentenceEndsUs,sentenceStarts,rowLengths,SCROLL_STEP,maxOffset);
        }
        public float offset(int sentence,float fraction,boolean narrationFinished){
            if(sentence<0){
                if(!narrationFinished)return 0f;
                return NewsScrollMath.offset(rowLengths,totalCharacters,
                    SCROLL_STEP,SCROLL_FOCUS_ROWS,maxOffset);
            }
            if(sentence+1>=sentenceStarts.length)
                throw new IllegalArgumentException("문장 인덱스가 잘못됐습니다.");
            float position=sentenceStarts[sentence]+
                NewsScrollMath.clamp(fraction,0f,1f)*
                (sentenceStarts[sentence+1]-sentenceStarts[sentence]);
            return NewsScrollMath.offset(rowLengths,position,
                SCROLL_STEP,SCROLL_FOCUS_ROWS,maxOffset);
        }
    }

    /** Compute wrapping only once, not for each encoded video frame. */
    public static ScrollPlan prepareScroll(NewsStory story){
        if(story==null)throw new IllegalArgumentException("뉴스 내용이 없습니다.");
        String script=story.spokenText();
        List<String> rows=wrapComplete(script,p(Color.WHITE,SCROLL_FONT,true),880f);
        if(rows.isEmpty())throw new IllegalArgumentException("뉴스 본문이 비어 있습니다.");
        int[] lengths=new int[rows.size()];
        int[] starts=new int[rows.size()];
        int total=0;
        for(int i=0;i<rows.size();i++){
            starts[i]=total;
            lengths[i]=NewsSentenceTimeline.visibleLength(rows.get(i));
            total+=lengths[i];
        }
        if(total!=NewsSentenceTimeline.visibleLength(script))
            throw new IllegalStateException("스크롤 화면에 모든 원고 글자가 배치되지 않았습니다.");
        List<String> sentences=NewsSentenceTimeline.split(script);
        int[] boundaries=new int[sentences.size()+1];
        for(int i=0;i<sentences.size();i++)
            boundaries[i+1]=boundaries[i]+NewsSentenceTimeline.visibleLength(sentences.get(i));
        if(boundaries[boundaries.length-1]!=total)
            throw new IllegalStateException("본문과 음성 문장 구간이 일치하지 않습니다.");
        float maxOffset=Math.max(0f,(rows.size()-1)*SCROLL_STEP); // center first and last spoken rows
        return new ScrollPlan(story,rows,lengths,starts,boundaries,total,maxOffset);
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

    /** News-specific photo; the label is always "reference image", never an event photograph claim. */
    private static void drawReferenceThumbnail(Canvas c,NewsImageFetcher.Photo photo,
                                                String title){
        final float l=68,t=253,r=596,b=788;
        Path clip=new Path();
        clip.addRoundRect(new RectF(l,t,r,b),16,16,Path.Direction.CW);
        c.save();c.clipPath(clip);
        if(photo!=null&&photo.bitmap!=null&&!photo.bitmap.isRecycled()){
            Bitmap bmp=photo.bitmap;
            float srcRatio=(float)bmp.getWidth()/bmp.getHeight();
            float dstRatio=(r-l)/(b-t);
            int w=bmp.getWidth(),h=bmp.getHeight();
            Rect source;
            if(srcRatio>dstRatio){
                int crop=Math.max(1,Math.round(h*dstRatio));
                source=new Rect((w-crop)/2,0,(w+crop)/2,h);
            }else{
                int crop=Math.max(1,Math.round(w/dstRatio));
                source=new Rect(0,(h-crop)/2,w,(h+crop)/2);
            }
            Paint bitmapPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(bmp,source,new RectF(l,t,r,b),bitmapPaint);
        }else{
            Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setShader(new LinearGradient(l,t,r,b,
                Color.rgb(16,80,151),Color.rgb(7,37,77),Shader.TileMode.CLAMP));
            c.drawRect(l,t,r,b,fill);
            Paint map=p(Color.rgb(127,193,255),0,false);
            map.setStrokeWidth(3);map.setStyle(Paint.Style.STROKE);
            c.drawCircle(240,438,64,map);
            c.drawOval(new RectF(215,375,265,501),map);
            c.drawLine(176,438,304,438,map);
            c.drawLine(240,374,240,502,map);
            c.drawText("PHILIPPINES",147,607,p(Color.WHITE,25,true));
        }
        rounded(c,Color.argb(195,3,20,43),l,b-32,r,b,0);
        String credit=photo==null?"뉴스 관련 중립 이미지":
            photo.sourceUrl.isEmpty()?"아나운서 이미지":"자료 이미지 · Wikimedia Commons";
        c.drawText(credit,l+12,b-10,p(Color.WHITE,19,true));
        c.restore();
        Paint border=p(Color.rgb(88,183,255),0,false);
        border.setStrokeWidth(3);border.setStyle(Paint.Style.STROKE);
        c.drawRoundRect(new RectF(l,t,r,b),16,16,border);
    }

    /** Large-print news slide: voice-synchronized upward movement within a clipped viewport. */
    private static Bitmap renderFullArticle(NewsStory story,int index,int count,
                                            NewsCoverMeta meta,int activeSentence,
                                            ScrollPlan scroll,float scrollPixels,
                                            NewsImageFetcher.Photo photo,float broadcastProgress,
                                            Bitmap reusable){
        Bitmap bitmap=reusable==null?
            Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888):reusable;
        if(bitmap.getWidth()!=WIDTH||bitmap.getHeight()!=HEIGHT||
           bitmap.getConfig()!=Bitmap.Config.ARGB_8888)
            throw new IllegalArgumentException("재사용 영상 버퍼 크기나 형식이 잘못됐습니다.");
        Canvas c=new Canvas(bitmap);
        int white=Color.WHITE,yellow=Color.rgb(255,210,46),blue=Color.rgb(11,48,101);
        Paint background=p(Color.rgb(5,17,44),0,false);
        background.setShader(new LinearGradient(0,0,0,HEIGHT,
            Color.rgb(9,37,87),Color.rgb(2,11,31),Shader.TileMode.CLAMP));
        c.drawRect(0,0,WIDTH,HEIGHT,background);
        rounded(c,yellow,0,0,WIDTH,14,0);
        String english=meta.clock.startsWith("오전")?
            "PHILIPPINES  |  MORNING NEWS":
            meta.clock.startsWith("오후")?
            "PHILIPPINES  |  AFTERNOON NEWS":
            "PHILIPPINES  |  NEWS BRIEF";
        c.drawText(english,51,93,p(white,33,true));
        c.drawText("오늘의 주요 뉴스",50,178,p(yellow,69,true));
        rounded(c,yellow,51,196,1031,204,3);
        rounded(c,blue,43,233,1037,809,30);
        stroke(c,43,233,1037,809,Color.rgb(41,157,241),4,30);
        // Approved A-mode design: illustrative news image on left, text on right.
        // The former yellow '주요 뉴스 1' plaque is deliberately removed.
        String title=story.title;
        boolean economy=title.contains("경제")||title.contains("예산")||
            title.contains("공공사업")||title.contains("투자")||
            title.contains("유가")||title.contains("휘발유")||
            title.contains("경유")||title.contains("산업")||
            title.contains("수출")||title.contains("기업");
        drawReferenceThumbnail(c,photo,title);
        line(c,Color.rgb(131,190,245),607,258,607,784,3);
        Paint headline=null;List<String> headlines=null;
        for(int size=55;size>=27;size--){
            headline=p(white,size,true);
            headlines=wrapComplete(title,headline,396f);
            if(headlines.size()<=12 &&
               headlines.size()*size*1.18f<=520)break;
        }
        if(headlines==null||headlines.size()>12)
            throw new IllegalArgumentException("제목이 상단 이미지 옆 공간을 초과합니다: "+title);
        float lineHeight=headline.getTextSize()*1.18f;
        float titleY=521-(headlines.size()-1)*lineHeight/2f+headline.getTextSize()*0.32f;
        for(String row:headlines){c.drawText(row,618,titleY,headline);titleY+=lineHeight;}
        rounded(c,Color.rgb(8,43,94),44,830,1036,1598,29);
        stroke(c,44,830,1036,1598,Color.rgb(40,153,237),4,29);
        // Entire width is now available for the complete announcer script.
        String category=story.kind==NewsStory.OPENING?"시작 인사":
            story.kind==NewsStory.ANCHOR?"앵커 한마디":
            story.kind==NewsStory.CLOSING?"마감 인사":
            economy?"경제·산업":"필리핀 주요 뉴스";
        c.drawText(category,89,906,p(yellow,43,true));
        String scrollLabel="큰 글씨 · 자동 스크롤";
        Paint scrollLabelPaint=p(Color.rgb(182,214,242),29,true);
        c.drawText(scrollLabel,991-scrollLabelPaint.measureText(scrollLabel),
            904,scrollLabelPaint);
        line(c,Color.rgb(69,143,220),87,935,990,935,2);
        drawWatermark(c,story);
        // No active-sentence shading or underline: steady white large print.
        // Sentence cues only determine the gradual vertical movement.
        c.save();
        c.clipRect(75f,954f,1002f,SCROLL_BOTTOM);
        Paint body=p(Color.WHITE,SCROLL_FONT,true);
        for(int row=0;row<scroll.lines.size();row++){
            float y=SCROLL_TOP+row*SCROLL_STEP-scrollPixels;
            if(y<954f-SCROLL_STEP || y>SCROLL_BOTTOM+SCROLL_STEP)continue;
            String text=scroll.lines.get(row);
            if(!text.isEmpty())drawProseLine(c,text,92f,y,body);
        }
        c.restore();
        // A quiet progress rail makes the scroll position visible without subtitles.
        if(scroll.maxOffset>0){
            rounded(c,Color.rgb(48,79,128),1014,960,1020,1542,3);
            float scrollRatio=NewsScrollMath.clamp(scrollPixels/scroll.maxOffset,0f,1f);
            float thumbTop=960+484f*scrollRatio;
            rounded(c,Color.rgb(255,210,46),1012,thumbTop,1022,thumbTop+98f,5);
        }
        int total=Math.max(1,story.articleCount);
        int completed=NewsBroadcastSegments.completedNews(story);
        int remain=NewsBroadcastSegments.remainingNews(story);
        // Count shows the current article, while the yellow rail measures
        // continuous elapsed broadcast time, not just completed article count.
        float overall=broadcastProgress<0f?
            (float)completed/total:NewsScrollMath.clamp(broadcastProgress,0f,1f);
        int shown=story.kind==NewsStory.OPENING?0:
            story.kind==NewsStory.NEWS?Math.min(total,Math.max(1,story.articleNumber)):total;
        rounded(c,yellow,46,1632,330,1700,14);
        c.drawText("전체 뉴스 진행",59,1679,p(Color.rgb(9,24,45),33,true));
        c.drawText(shown+" / "+total,353,1678,p(white,42,true));
        rounded(c,Color.rgb(96,113,147),535,1648,895,1688,20);
        if(overall>0f)
            rounded(c,yellow,535,1648,535+360f*overall,1688,20);
        c.drawText(Math.round(overall*100f)+"%",917,1679,p(white,37,true));
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

    public static Bitmap render(Context context,NewsStory story,int index,int count,int stage,
                                NewsCoverMeta meta){
        return render(context,story,index,count,stage,meta,-1);
    }
    /** Called by the video encoder with a cached plan and a per-frame scroll offset. */
    public static Bitmap renderScrolling(NewsStory story,int index,int count,NewsCoverMeta meta,
                                         int activeSentence,ScrollPlan plan,float scrollPixels,
                                         Bitmap reusable){
        return renderScrolling(story,index,count,meta,activeSentence,
            plan,scrollPixels,null,-1f,reusable);
    }
    public static Bitmap renderScrolling(NewsStory story,int index,int count,NewsCoverMeta meta,
                                         int activeSentence,ScrollPlan plan,float scrollPixels,
                                         NewsImageFetcher.Photo photo,float broadcastProgress,
                                         Bitmap reusable){
        if(plan.story!=story)throw new IllegalArgumentException("기사와 스크롤 레이아웃이 다릅니다.");
        return renderFullArticle(story,index,count,meta,activeSentence,plan,
            scrollPixels,photo,broadcastProgress,reusable);
    }
    public static Bitmap render(Context context,NewsStory story,int index,int count,int stage,
                                NewsCoverMeta meta,int activeSentence){
        if(stage>=2 && story!=null)return renderFullArticle(story,index,count,meta,
            activeSentence,prepareScroll(story),0,null,-1f,null);
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
