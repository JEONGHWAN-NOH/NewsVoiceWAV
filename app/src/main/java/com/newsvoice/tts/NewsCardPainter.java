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

    public static List<NewsStory> paginateBroadcast(List<NewsStory> sections) {
        return sections;
    }

    private static final float SCROLL_FONT=64f;
    private static final float SCROLL_STEP=96f;
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
        Paint body=p(Color.WHITE,SCROLL_FONT,true);
        float centerBaseline=(HEIGHT-body.ascent()-body.descent())/2f;
        int first=Math.max(0,(int)((scrollPixels-centerBaseline-body.descent())/SCROLL_STEP));
        int end=Math.min(scroll.lines.size(),2+(int)((HEIGHT+scrollPixels-centerBaseline-body.ascent())/SCROLL_STEP));
        for(int row=first;row<end;row++){
            float y=centerBaseline+row*SCROLL_STEP-scrollPixels;
            if(y+body.descent()<0 || y+body.ascent()>HEIGHT)continue;
            String text=scroll.lines.get(row);
            c.drawText(text,(WIDTH-body.measureText(text))/2f,y,body);
        }
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
            scrollPixels,broadcastProgress,reusable);
    }
    public static Bitmap render(Context context,NewsStory story,int index,int count,int stage,
                                NewsCoverMeta meta,int activeSentence){
        if(stage>=2 && story!=null)return renderFullArticle(story,index,count,meta,
            activeSentence,prepareScroll(story),0,-1f,null);
        Bitmap bitmap=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bitmap);
        c.drawColor(NAVY);
        return bitmap;
    }
}

