package com.newsvoice.tts;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Accepts a complete spoken Korean bulletin without requiring === separators.
 * Only explicit spoken section cues are used; no news facts or article titles
 * are invented. Existing headline/body/=== scripts are unchanged.
 */
public final class NewsAutoScript {
    private NewsAutoScript() {}
    private static final Pattern DIVIDER=Pattern.compile("(?m)^\\s*(?:===|---)\\s*$");
    private static final Pattern SECTION=Pattern.compile(
        "(?m)(?:^|(?<=[.!?。])(?=\\s))\\s*((?:(?:첫|두|세|네|다섯|여섯|일곱|여덟|아홉|열|열\\s*한|열\\s*두|열세|열네|열다섯|열여섯|열일곱|열여덟|열아홉|스무|[0-9]{1,2})\\s*(?:번째|번)\\s*(?:뉴스|소식)\\s*입니다[.!]?)"
        +"|(?:다음\\s*(?:뉴스|소식)\\s*입니다[.!]?)"
        +"|(?:다음은\\s*(?:한국(?:과 필리핀)?\\s*(?:관련\\s*)?특별\\s*뉴스입니다[.!]?))"
        +"|(?:다음은\\s*필리핀\\s*기업\\s*운영[^\\n]*입니다[.!]?)"
        +"|(?:마지막으로\\s*필리핀\\s*경제[^\\n]*전망입니다[.!]?)"
        +"|(?:(?:오늘의?\\s*)?(?:뉴스[,，]?\\s*)?앵커(?:의)?\\s*한\\s*마디입니다[.!]?)"
        +"|(?:마지막으로\\s*앵커(?:의)?\\s*한\\s*마디입니다[.!]?)"
        +"|(?:이상\\s+(?:필리핀|광명)\\s*뉴스였습니다[.!]?)"
        +"|(?:이상으로[^\\n]{0,100}(?:브리핑을|뉴스를)\\s*(?:마칩니다|마치겠습니다)[.!]?))(?=\\s|$)");
    private static final Pattern ANCHOR=Pattern.compile(".*앵커(?:의)?\\s*한\\s*마디.*");
    private static final Pattern CLOSING=Pattern.compile("^이상.*");

    static boolean startsWithArticleCue(String body) {
        Matcher match=SECTION.matcher(body);
        return match.lookingAt() && isArticle(match.group(1));
    }

    public static boolean isAutoFormat(String raw) {
        if(raw==null||DIVIDER.matcher(raw).find())return false;
        Matcher match=SECTION.matcher(raw);
        int news=0;
        while(match.find())if(isArticle(match.group(1))&&!match.group(1).matches("다음\\s*(?:뉴스|소식)\\s*입니다[.!]?"))news++;
        return news>=1;
    }
    private static boolean isArticle(String cue) {
        return !ANCHOR.matcher(cue).matches()&&!CLOSING.matcher(cue).matches();
    }
    private static String titleFrom(String segment,String cue,int ordinal) {
        String text=segment.substring(Math.min(segment.length(),cue.length())).trim();
        // The title is a literal excerpt of the user's own first news sentence.
        text=text.replaceAll("(?m)^(?:먼저|이어서|다음은)\\s*(?:정치|경제|사회|사건사고|금융|산업)\\s*분야입니다[.!]?\\s*","");
        int end=text.indexOf('。');
        int dot=text.indexOf('.');
        if(end<0||dot>=0&&dot<end)end=dot;
        if(end>=0)text=text.substring(0,end);
        text=text.replaceAll("\\s+"," ").trim();
        if(text.isEmpty())text=cue.replaceAll("[.!]$","");
        if(text.length()>48)text=text.substring(0,47)+"…";
        return "뉴스 "+ordinal+" · "+text;
    }
    public static String adapt(String raw){
        if(!isAutoFormat(raw))return raw;
        Matcher match=SECTION.matcher(raw);
        List<Integer> starts=new ArrayList<>();
        List<String> cues=new ArrayList<>();
        while(match.find()){
            starts.add(match.start());cues.add(match.group(1).trim());
        }
        StringBuilder output=new StringBuilder();
        String intro=raw.substring(0,starts.get(0)).trim();
        if(!intro.isEmpty())output.append("시작 인사\n").append(intro).append("\n===\n");
        int article=0;
        for(int i=0;i<starts.size();i++){
            String cue=cues.get(i);
            String part=raw.substring(starts.get(i),
                i+1<starts.size()?starts.get(i+1):raw.length()).trim();
            if(part.isEmpty()||ANCHOR.matcher(cue).matches())continue;
            if(output.length()>0&&!output.toString().endsWith("===\n"))output.append("\n===\n");
            if(CLOSING.matcher(cue).matches())output.append("마감 인사");
            else output.append(titleFrom(part,cue,++article));
            output.append('\n').append(part);
        }
        return output.toString();
    }
}
