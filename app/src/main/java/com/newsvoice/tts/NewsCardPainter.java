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

    private static final float SCROLL_FONT=64f;
    private static final float SCROLL_STEP=96f;
    private static final float SCROLL_TOP=840f; // current spoken line baseline sits near the screen center
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
        public float pageOffset(long presentationUs,long audioStartUs,long[] cueEnds){
            long time=presentationUs-700000L;
            int skip=story.hasTransition()?1:0;
            long start=skip==1?cueEnds[0]:audioStartUs;
            if(time<=start)return 0f;
            float position=totalCharacters;
            for(int i=skip;i<cueEnds.length;i++){
                if(time<cueEnds[i]){
                    int sentence=i-skip;
                    float fraction=NewsScrollMath.clamp((float)(time-start)/Math.max(1L,cueEnds[i]-start),0f,1f);
                    position=sentenceStarts[sentence]+fraction*(sentenceStarts[sentence+1]-sentenceStarts[sentence]);break;
                }
                start=cueEnds[i];
            }
            int row=0;while(row+1<rowStarts.length&&position>=rowStarts[row+1])row++;
            return (row/10)*10*SCROLL_STEP;
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


    /** Large-print news slide: voice-synchronized upward movement within a clipped viewport. */
    private static Bitmap renderFullArticle(NewsStory story,int index,int count,
                                            NewsCoverMeta meta,int activeSentence,
                                            ScrollPlan scroll,float scrollPixels,
                                            float broadcastProgress,
                                            Bitmap photo,boolean paged,Bitmap reusable){
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
        if(photo!=null&&!photo.isRecycled()){
            c.drawBitmap(photo,null,new RectF(60,230,1020,550),new Paint(Paint.FILTER_BITMAP_FLAG));
            rounded(c,Color.argb(180,0,0,0),60,510,290,550,0);
            c.drawText("AI 참고 이미지",74,539,p(white,26,true));
        }else{
            c.drawText(story.kind==NewsStory.NEWS?"이미지 없이 방송 중":"NEWSVOICE WAV",88,410,p(WHITE,42,true));
        }
        line(c,yellow,88,560,992,560,6);
        // No active-sentence shading or underline: steady white large print.
        // Sentence cues only determine the gradual vertical movement.
        c.save();
        c.clipRect(75f,600f,1002f,SCROLL_BOTTOM);
        Paint body=p(Color.WHITE,SCROLL_FONT,true);
        for(int row=0;row<scroll.lines.size();row++){
            float y=(paged?660f:SCROLL_TOP)+row*SCROLL_STEP-scrollPixels;
            if(y<600f-SCROLL_STEP || y>SCROLL_BOTTOM+SCROLL_STEP)continue;
            String text=scroll.lines.get(row);
            if(!text.isEmpty())drawProseLine(c,text,92f,y,body);
        }
        c.restore();
        // A quiet progress rail makes the scroll position visible without subtitles.
        if(!paged&&scroll.maxOffset>0){
            rounded(c,Color.rgb(48,79,128),1014,610,1020,1542,3);
            float scrollRatio=NewsScrollMath.clamp(scrollPixels/scroll.maxOffset,0f,1f);
            float thumbTop=610+834f*scrollRatio;
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


    public static Bitmap render(Context context,NewsStory story,int index,int count,int stage,
                                NewsCoverMeta meta){
        return render(context,story,index,count,stage,meta,-1);
    }
    /** Called by the video encoder with a cached plan and a per-frame scroll offset. */
    public static Bitmap renderScrolling(NewsStory story,int index,int count,NewsCoverMeta meta,
                                         int activeSentence,ScrollPlan plan,float scrollPixels,
                                         Bitmap reusable){
        return renderScrolling(story,index,count,meta,activeSentence,
            plan,scrollPixels,-1f,reusable);
    }
    public static Bitmap renderScrolling(NewsStory story,int index,int count,NewsCoverMeta meta,
                                         int activeSentence,ScrollPlan plan,float scrollPixels,
                                         float broadcastProgress,
                                         Bitmap reusable){
        if(plan.story!=story)throw new IllegalArgumentException("기사와 스크롤 레이아웃이 다릅니다.");
        return renderFullArticle(story,index,count,meta,activeSentence,plan,
            scrollPixels,broadcastProgress,null,false,reusable);
    }
    public static Bitmap renderBroadcast(NewsStory story,int index,int count,NewsCoverMeta meta,
        int sentence,ScrollPlan plan,float offset,float progress,Bitmap photo,boolean paged,Bitmap reusable){
        return renderFullArticle(story,index,count,meta,sentence,plan,offset,progress,photo,paged,reusable);
    }
    public static Bitmap render(Context context,NewsStory story,int index,int count,int stage,
                                NewsCoverMeta meta,int activeSentence){
        if(stage>=2 && story!=null)return renderFullArticle(story,index,count,meta,
            activeSentence,prepareScroll(story),0,-1f,null,false,null);
        Bitmap bitmap=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bitmap);
        c.drawColor(NAVY);
        wrapped(c,"필리핀 뉴스",88,280,900,100,WHITE,84,2,true);
        line(c,YELLOW,88,420,992,420,7);
        wrapped(c,"오늘의 주요 소식",88,670,900,90,WHITE,72,3,true);
        c.drawText(meta.date,88,930,p(WHITE,55,true));
        c.drawText(meta.clock,88,1020,p(WHITE,40,false));
        c.drawText("NEWSVOICE WAV LITE",88,1800,p(SUB,35,true));
        return bitmap;
    }
}
