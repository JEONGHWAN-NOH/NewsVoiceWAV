package com.newsvoice.tts;
import java.util.ArrayList;
import java.util.List;

/** Arrange all spoken sections in the order the user requested. */
public final class NewsBroadcastSegments {
    private NewsBroadcastSegments() {}
    public static final String ANCHOR="마지막으로 앵커 한마디입니다. 오늘 전해드린 소식을 살펴보실 때에는 확정된 사실과 향후 전망을 구분하는 것이 중요하겠습니다. 주요 사안의 후속 소식도 계속 확인하겠습니다.";
    public static final String CLOSING="이상으로 광명시 종합 뉴스 브리핑을 마치겠습니다. 시청해 주셔서 감사합니다.";

    private static final String NEXT="다음 뉴스입니다.";
    private static final java.util.regex.Pattern NEXT_CUE=java.util.regex.Pattern.compile(
        "(?m)(?:^|(?<=[.!?])\\s+|\\n+)다음\\s+뉴스입니다[.!]?(?=\\s|$)\\s*");
    private static final java.util.regex.Pattern DIVIDER=java.util.regex.Pattern.compile("(?m)^\\s*(?:===|---)\\s*$");
    private static String structured(String raw){
        String adapted=NewsAutoScript.adapt(raw);
        if(!adapted.equals(raw)||DIVIDER.matcher(raw).find())return adapted;
        java.util.regex.Matcher cues=NEXT_CUE.matcher(raw);
        StringBuilder result=new StringBuilder();
        int start=0;
        while(cues.find()){
            appendPlain(result,raw.substring(start,cues.start()));
            start=cues.end();
        }
        appendPlain(result,raw.substring(start));
        return result.toString();
    }
    private static void appendPlain(StringBuilder result,String raw){
        String text=raw.trim();
        if(text.isEmpty())return;
        if(result.length()>0)result.append("\n===\n");
        String title=text.split("[.!?\\n]",2)[0].trim();
        if(title.isEmpty())title="주요 뉴스";
        if(title.length()>45)title=title.substring(0,45)+"…";
        result.append(title).append('\n').append(text);
    }
    public static String playback(String raw){
        if(raw.trim().isEmpty())return "";
        if(!NewsAutoScript.isAutoFormat(raw)&&!DIVIDER.matcher(raw).find())return raw;
        List<NewsStory> sections;
        try {
            sections=NewsStory.parse(NewsAutoScript.adapt(raw));
        }catch(IllegalArgumentException ex){
            // The video renderer limits article count. Live reading accepts longer scripts.
            return raw;
        }
        StringBuilder out=new StringBuilder();
        int news=0;
        for(NewsStory item:sections){
            boolean corner=item.title.matches("시작 인사|오프닝|마감 인사|클로징|앵커 한마디|앵커 한 말씀");
            String body=item.spokenText();
            if(body.isEmpty())body=item.narration;
            if(out.length()>0)out.append('\n');
            if(!corner && news++>0 && !NEXT_CUE.matcher(body).lookingAt() && !NewsAutoScript.startsWithArticleCue(body))out.append(NEXT).append(' ');
            out.append(body);
        }
        return out.toString();
    }
    public static List<NewsStory> build(String raw,String preferredAnchor) {
        return build(raw,preferredAnchor,false);
    }
    public static List<NewsStory> build(String raw,String preferredAnchor,boolean openingPortrait) {
        List<NewsStory> parsed=NewsStory.parse(structured(raw));
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
            }else {
                if(section.spokenText().isEmpty())
                    throw new IllegalArgumentException("일반 뉴스는 첫 줄에 제목, 다음 줄부터 낭독할 본문이 필요합니다: "+heading);
                news.add(section);
            }
        }
        if(news.isEmpty())throw new IllegalArgumentException("전할 뉴스 원고가 없습니다.");
        int count=news.size();

        String comment=anchor!=null?anchor:
            preferredAnchor!=null&&!preferredAnchor.trim().isEmpty()?preferredAnchor:ANCHOR;
        String last=closing==null?CLOSING:closing;
        List<NewsStory> result=new ArrayList<>();
        if(opening==null && openingPortrait)opening="안녕하십니까. 오늘의 뉴스를 전해드립니다.";
        if(opening!=null)result.add(NewsStory.segment(NewsStory.OPENING,"시작 인사",opening,count));
        for(int i=0;i<count;i++){
            NewsStory item=news.get(i);
            String text=item.spokenText();
            java.util.regex.Matcher cue=NEXT_CUE.matcher(text);
            if(i>0 && cue.lookingAt())text=text.substring(cue.end());
            result.add(item.page(item.title+"\n"+text,i+1,count,1,1));
        }
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
            result.append(s.audioText());
        }
        return result.toString();
    }
}
