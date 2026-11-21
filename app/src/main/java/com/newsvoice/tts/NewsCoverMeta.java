package com.newsvoice.tts;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Immutable cover information captured once when this MP4 begins rendering. */
public final class NewsCoverMeta {
    public final String date,clock,session;
    private NewsCoverMeta(String d,String c,String s){date=d;clock=c;session=s;}
    public static NewsCoverMeta now(){return from(Instant.now());}
    public static NewsCoverMeta from(Instant timestamp){
        ZonedDateTime t=timestamp.atZone(ZoneId.of("Asia/Manila"));
        String date=DateTimeFormatter.ofPattern("yyyy.MM.dd",Locale.KOREA).format(t);
        String ampm=t.getHour()<12?"오전":"오후";
        int h=t.getHour()%12; if(h==0)h=12;
        String clock=t.getMinute()==0?
            String.format(Locale.KOREA,"%s %d시 뉴스",ampm,h):
            String.format(Locale.KOREA,"%s %d시 %02d분 뉴스",ampm,h,t.getMinute());
        return new NewsCoverMeta(date,clock,ampm+" 브리핑");
    }
}
