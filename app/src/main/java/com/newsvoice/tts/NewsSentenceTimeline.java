package com.newsvoice.tts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Preserves every non-whitespace character when breaking a Korean news script
 * into spoken sentences. The same boundaries control TTS WAV generation and
 * Canvas highlights, so the highlight never relies on an estimated WPM rate.
 */
public final class NewsSentenceTimeline {
    private NewsSentenceTimeline(){ }

    public static List<String> split(String narration){
        if(narration==null||narration.trim().isEmpty())
            throw new IllegalArgumentException("낭독할 뉴스 본문이 없습니다.");
        String text=narration.trim();
        List<String> result=new ArrayList<>();
        int start=0;
        for(int i=0;i<text.length();i++){
            char ch=text.charAt(i);
            if(ch!='.' && ch!='!' && ch!='?' && ch!='。' && ch!='！' && ch!='？')continue;
            int end=i+1;
            // A sentence may end with a closing quotation or parenthesis.
            while(end<text.length()){
                char closing=text.charAt(end);
                if(closing!='"' && closing!='”' && closing!='’' &&
                   closing!=')' && closing!=']' && closing!='}')break;
                end++;
            }
            if(end<text.length()&&!Character.isWhitespace(text.charAt(end)))continue;
            String sentence=text.substring(start,end).trim();
            if(!sentence.isEmpty())result.add(sentence);
            start=end;
            i=end-1;
        }
        String tail=text.substring(start).trim();
        if(!tail.isEmpty())result.add(tail);
        if(result.isEmpty())throw new IllegalArgumentException("비어 있는 문장입니다.");
        StringBuilder rebuilt=new StringBuilder();
        for(String sentence:result)rebuilt.append(withoutWhitespace(sentence));
        if(!rebuilt.toString().equals(withoutWhitespace(text)))
            throw new IllegalStateException("문장 분할 중 원고가 유실됐습니다.");
        return Collections.unmodifiableList(result);
    }

    private static String withoutWhitespace(String text){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<text.length();i++)
            if(!Character.isWhitespace(text.charAt(i)))b.append(text.charAt(i));
        return b.toString();
    }
    public static int visibleLength(String text){
        int total=0;
        for(int i=0;i<text.length();i++)
            if(!Character.isWhitespace(text.charAt(i)))total++;
        return total;
    }
    /** Half-open offset range in the body, excluding layout-only whitespace. */
    public static int[] visibleRange(List<String> sentences,int activeIndex){
        if(activeIndex<0 || activeIndex>=sentences.size())return new int[]{-1,-1};
        int start=0;
        for(int i=0;i<activeIndex;i++)start+=visibleLength(sentences.get(i));
        return new int[]{start,start+visibleLength(sentences.get(activeIndex))};
    }
}
