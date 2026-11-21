package com.newsvoice.tts;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** One news item = one video card. Split by a line containing only === or ---.
 * The parser does not invent headlines, facts, dates, or source attribution.
 */
public final class NewsStory {
    public final String title;
    public final String narration;
    public final String preview;
    public final int articleNumber, articleCount, pageNumber, pageCount;

    private NewsStory(String title, String narration, String preview,
                      int articleNumber, int articleCount, int pageNumber, int pageCount) {
        this.title=title;this.narration=narration;this.preview=preview;
        this.articleNumber=articleNumber;this.articleCount=articleCount;
        this.pageNumber=pageNumber;this.pageCount=pageCount;
    }

    public NewsStory page(String fullPageText,int articleNo,int totalArticles,int number,int totalPages){
        return new NewsStory(title,fullPageText,fullPageText,
             articleNo,totalArticles,number,totalPages);
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
            result.add(new NewsStory(heading,text,preview,0,0,1,1));
        }
        if(result.isEmpty())throw new IllegalArgumentException("뉴스 원고가 없습니다.");
        if(result.size()>30)throw new IllegalArgumentException("한 영상은 기사 30개 이하입니다.");
        return result;
    }
}
