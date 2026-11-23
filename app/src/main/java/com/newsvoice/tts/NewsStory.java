package com.newsvoice.tts;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Each item has a visual-only title and a complete spoken body; long items paginate.
 * The parser does not invent headlines, facts, dates, or source attribution.
 */
public final class NewsStory {
    public final String title;
    public final String narration;
    public final String preview;
    public final int articleNumber, articleCount, pageNumber, pageCount;
    public static final int NEWS=0, OPENING=1, ANCHOR=2, CLOSING=3;
    public final int kind;

    private NewsStory(String title, String narration, String preview,
                      int articleNumber, int articleCount, int pageNumber, int pageCount,int kind) {
        this.title=title;this.narration=narration;this.preview=preview;
        this.articleNumber=articleNumber;this.articleCount=articleCount;
        this.pageNumber=pageNumber;this.pageCount=pageCount;this.kind=kind;
    }

    public NewsStory page(String fullPageText,int articleNo,int totalArticles,int number,int totalPages){
        return new NewsStory(title,fullPageText,fullPageText,
             articleNo,totalArticles,number,totalPages,kind);
    }
    /**
     * News titles are visual-only. TTS must speak only the body after the first
     * newline; opening, anchor comment and closing remain spoken in full.
     */
    public String spokenText(){
        String text=narration==null?"":narration.trim();
        if(kind!=NEWS)return text;
        int pos=text.indexOf('\n');
        return pos<0?"":text.substring(pos+1).trim();
    }

    public boolean hasTransition(){return kind==NEWS && articleNumber>1 && !NewsAutoScript.startsWithArticleCue(spokenText());}
    public String audioText(){return hasTransition()?"다음 뉴스입니다. "+spokenText():spokenText();}

    public static NewsStory segment(int kind,String title,String narration,int newsCount){
        if(kind==NEWS||narration==null||narration.trim().isEmpty())
            throw new IllegalArgumentException("방송 멘트 형식이 올바르지 않습니다.");
        return new NewsStory(title,narration.trim(),narration.trim(),0,newsCount,1,1,kind);
    }

    public static List<NewsStory> parse(String script) {
        List<NewsStory> result = new ArrayList<>();
        String[] blocks = Pattern.compile("(?m)^\\s*(?:===|---)\\s*$").split(script.trim());
        for(String block : blocks) {
            String text = block.trim();
            if(text.isEmpty())continue;
            String[] lines = text.split("\\R+",2);
            String heading = lines[0].trim();
            // A single-line article is both the news title and narration.
            if(heading.length() > 95) heading = heading.substring(0,92)+"…";
            String preview = lines.length>1 ? lines[1].trim() : text;
            result.add(new NewsStory(heading,text,preview,0,0,1,1,NEWS));
        }
        if(result.isEmpty())throw new IllegalArgumentException("뉴스 원고가 없습니다.");
        if(result.size()>30)throw new IllegalArgumentException("한 영상은 기사 30개 이하입니다.");
        return result;
    }
}
