package com.newsvoice.tts;
import java.util.ArrayList;
import java.util.List;

/** Arrange all spoken sections in the order the user requested. */
public final class NewsBroadcastSegments {
    private NewsBroadcastSegments() {}
    public static final String OPENING="안녕하십니까. 필리핀 뉴스·경제 종합 브리핑입니다. 지금부터 주요 소식을 전해드리겠습니다.";
    public static final String ANCHOR="마지막으로 앵커 한마디입니다. 오늘 전해드린 소식을 살펴보실 때에는 확정된 사실과 향후 전망을 구분하는 것이 중요하겠습니다. 주요 사안의 후속 소식도 계속 확인하겠습니다.";
    public static final String CLOSING="이상으로 필리핀 뉴스·경제 종합 브리핑을 마치겠습니다. 시청해 주셔서 감사합니다.";

    public static List<NewsStory> build(String raw,String preferredAnchor) {
        List<NewsStory> parsed=NewsStory.parse(raw);
        List<NewsStory> news=new ArrayList<>();
        String opening=null,anchor=null,closing=null;
        for(NewsStory section:parsed) {
            String heading=section.title.trim();
            if(heading.equals("시작 인사")||heading.equals("오프닝")) {
                if(opening!=null)throw new IllegalArgumentException("시작 인사가 중복됐습니다.");
                opening=body(section);
            }else if(heading.equals("앵커 한마디")||heading.equals("앵커 한 말씀")) {
                if(anchor!=null)throw new IllegalArgumentException("앵커 한마디가 중복됐습니다.");
                anchor=body(section);
            }else if(heading.equals("마감 인사")||heading.equals("클로징")) {
                if(closing!=null)throw new IllegalArgumentException("마감 인사가 중복됐습니다.");
                closing=body(section);
            }else news.add(section);
        }
        if(news.isEmpty())throw new IllegalArgumentException("전할 뉴스 원고가 없습니다.");
        int count=news.size();
        String first=verified(opening==null?OPENING:opening,"시작 인사");
        String comment=verified(anchor!=null?anchor:
            preferredAnchor!=null&&!preferredAnchor.trim().isEmpty()?preferredAnchor:ANCHOR,"앵커 한마디");
        String last=verified(closing==null?CLOSING:closing,"마감 인사");
        List<NewsStory> result=new ArrayList<>();
        result.add(NewsStory.segment(NewsStory.OPENING,"시작 인사",first,count));
        result.addAll(news);
        result.add(NewsStory.segment(NewsStory.ANCHOR,"앵커 한마디",comment,count));
        result.add(NewsStory.segment(NewsStory.CLOSING,"마감 인사",last,count));
        return result;
    }
    private static String body(NewsStory s) {
        int pos=s.narration.indexOf('\n');
        if(pos<0)throw new IllegalArgumentException(s.title+" 아래 줄에 낭독할 원고를 작성하세요.");
        String text=s.narration.substring(pos+1).trim();
        if(text.isEmpty())throw new IllegalArgumentException(s.title+" 내용이 비어 있습니다.");
        return text;
    }
    private static String verified(String text,String label){
        BroadcastScriptReview.Result review=BroadcastScriptReview.review(text);
        if(!review.approved())throw new IllegalArgumentException(label+" 검수 오류: "+review.summary());
        return review.reviewed;
    }
    public static int newsCount(List<NewsStory> sections) {
        int count=0;
        for(NewsStory s:sections)if(s.kind==NewsStory.NEWS)count++;
        return count;
    }
    public static int completedNews(NewsStory s){
        int count=Math.max(0,s.articleCount);
        if(s.kind==NewsStory.OPENING)return 0;
        if(s.kind==NewsStory.ANCHOR||s.kind==NewsStory.CLOSING)return count;
        return Math.min(count,Math.max(0,s.articleNumber));
    }
    public static int remainingNews(NewsStory s){
        return Math.max(0,s.articleCount-completedNews(s));
    }
    public static String audioScript(List<NewsStory> sections){
        StringBuilder result=new StringBuilder();
        for(NewsStory s:sections){
            if(result.length()>0)result.append('\n');
            result.append(s.narration);
        }
        return result.toString();
    }
}
