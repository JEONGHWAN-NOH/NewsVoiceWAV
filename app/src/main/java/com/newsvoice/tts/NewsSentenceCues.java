package com.newsvoice.tts;

import java.util.ArrayList;
import java.util.List;

/** Shared, deterministic sentence boundaries for TTS and the highlighted card. */
public final class NewsSentenceCues {
    private NewsSentenceCues() {}
    public static int countVisible(String text) {
        int n=0;
        if(text!=null)for(int i=0;i<text.length();i++)
            if(!Character.isWhitespace(text.charAt(i)))n++;
        return n;
    }
    public static List<String> split(String text) {
        if(text==null||text.trim().isEmpty())throw new IllegalArgumentException("낭독 본문이 비어 있습니다.");
        List<String> result=new ArrayList<>();
        StringBuilder current=new StringBuilder();
        String normalized=text.replace('\r',' ').trim();
        for(int i=0;i<normalized.length();i++){
            char ch=normalized.charAt(i);
            if(ch=='\n') {
                flush(current,result);
                continue;
            }
            current.append(ch);
            boolean end=(ch=='.'||ch=='!'||ch=='?'||ch=='。'||ch=='！'||ch=='？');
            boolean boundary=(i+1==normalized.length()||Character.isWhitespace(normalized.charAt(i+1)));
            if(end && boundary)flush(current,result);
        }
        flush(current,result);
        if(result.isEmpty())throw new IllegalArgumentException("읽을 문장이 없습니다.");
        StringBuilder reconstructed=new StringBuilder();
        for(String sentence:result)reconstructed.append(sentence.replaceAll("\\s+",""));
        if(!reconstructed.toString().equals(normalized.replaceAll("\\s+","")))
            throw new IllegalStateException("문장 분리 중 원고 내용이 유실되었습니다.");
        return result;
    }
    private static void flush(StringBuilder current,List<String> out){
        String s=current.toString().trim();
        if(!s.isEmpty())out.add(s);
        current.setLength(0);
    }
    /** Exclusive ends measured without whitespace: the same metric used in line rendering. */
    public static int[] endCharacterOffsets(String text) {
        List<String> sentences=split(text);
        int[] end=new int[sentences.size()];
        int total=0;
        for(int i=0;i<sentences.size();i++){
            total+=countVisible(sentences.get(i));
            end[i]=total;
        }
        if(total!=countVisible(text))throw new IllegalStateException("문장별 글자 수가 일치하지 않습니다.");
        return end;
    }
}
