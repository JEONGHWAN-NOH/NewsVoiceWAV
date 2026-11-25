package com.newsvoice.tts;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Manila-local news date, 6AM/6PM session and one common name per generated media set. */
public final class NewsOutputNaming {
    private NewsOutputNaming() { }
    public static final ZoneId MANILA=ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyy-MM-dd",Locale.ROOT);
    // Seconds avoid most duplicate filenames while keeping the clock readable.
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("HHmmss",Locale.ROOT);
    public static final String DIRECTORY_NAME="1. 필리핀뉴스 v3.0";

    public static final class Names {
        public final String dateFolder,base;
        private Names(String dateFolder,String base){
            this.dateFolder=dateFolder;this.base=base;
        }
        public String wav(){return base+".wav";}
        public String mp4(){return base+".mp4";}
        public String jpg(){return base+"_cover.jpg";}
        public String credits(){return base+"_image_credits.txt";}
    }

    public static Names at(Instant timestamp) {
        ZonedDateTime local=timestamp.atZone(MANILA);
        String date=DATE.format(local);
        String session=local.getHour()<12?"오전6시":"오후6시";
        String base=date+"_"+session+"_필리핀뉴스_"+TIME.format(local);
        return new Names(date,base);
    }

    /** Sanitize the filename before using its Manila date as a MediaStore subdirectory. */
    public static String folderFor(String filename,Instant fallback){
        if(filename!=null && filename.matches(
                "\\d{4}-\\d{2}-\\d{2}_(?:오전6시|오후6시)_필리핀뉴스_\\d{6}(?:_cover|_image_credits)?\\.(?:wav|mp4|jpg|txt)")){
            String date=filename.substring(0,10);
            try{return DATE.format(LocalDate.parse(date,DATE));}
            catch(java.time.DateTimeException ignored){ }
        }
        return DATE.format(fallback.atZone(MANILA));
    }
}
