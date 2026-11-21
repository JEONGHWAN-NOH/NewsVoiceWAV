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
    public static Bitmap render(NewsStory story,int index,int count,int stage,String timeLabel){
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
