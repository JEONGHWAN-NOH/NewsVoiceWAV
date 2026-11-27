package com.newsvoice.tts;

import android.content.Context;
import android.graphics.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Approved artwork with the broadcast's Manila date and session refreshed. */
final class ShareCover {
    static Bitmap bitmap(Context c,Instant time)throws IOException {
        Bitmap original;
        try(InputStream in=c.getAssets().open("today_news.webp")){original=BitmapFactory.decodeStream(in);}
        if(original==null)throw new IOException("표지 파일 오류");
        Bitmap result=original.copy(Bitmap.Config.ARGB_8888,true);original.recycle();
        Canvas canvas=new Canvas(result);canvas.scale(result.getWidth()/1404f,result.getHeight()/1536f);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.WHITE);
        canvas.drawRoundRect(485,480,1160,568,42,42,p);
        p.setColor(0xff11143a);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextAlign(Paint.Align.CENTER);p.setTextSize(44);
        ZonedDateTime date=time.atZone(ZoneId.of("Asia/Manila"));
        canvas.drawText(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 (E)",Locale.KOREAN).format(date),822,540,p);
        p.setColor(0xff063bae);canvas.drawRoundRect(505,580,1116,669,42,42,p);
        p.setColor(Color.WHITE);p.setTextSize(47);canvas.drawText((date.getHour()<12?"오전":"오후")+" 뉴스 브리핑",810,641,p);
        return result;
    }
    static void write(Context c,File file,Instant time)throws IOException {
        Bitmap b=bitmap(c,time);try(OutputStream out=new FileOutputStream(file)){if(!b.compress(Bitmap.CompressFormat.JPEG,90,out))throw new IOException("표지 저장 실패");}finally{b.recycle();}
    }
}
