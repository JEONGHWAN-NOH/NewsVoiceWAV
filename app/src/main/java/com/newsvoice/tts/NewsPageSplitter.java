package com.newsvoice.tts;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits narration without dropping characters. A caller supplies the real Android
 * text-layout fit test; every resulting page is synthesized separately.
 */
public final class NewsPageSplitter {
    private NewsPageSplitter() {}
    public interface Fits { boolean test(String text); }
    public static List<String> split(String narration, Fits fits) {
        if(narration == null || narration.trim().isEmpty()) throw new IllegalArgumentException("Empty narration");
        List<String> pages=new ArrayList<>();
        String remaining=narration.trim();
        while(!remaining.isEmpty()) {
            if(pages.size()>=120) throw new IllegalArgumentException("뉴스 페이지 수가 120장을 초과합니다.");
            if(fits.test(remaining)) {pages.add(remaining);break;}
            int lo=1,hi=remaining.length()-1,best=0;
            while(lo<=hi) {
                int mid=(lo+hi)>>>1;
                if(fits.test(remaining.substring(0,mid).trim())){best=mid;lo=mid+1;}
                else hi=mid-1;
            }
            if(best<1) throw new IllegalArgumentException("텍스트를 현재 카드에 배치할 수 없습니다.");
            int cut=chooseBoundary(remaining,best);
            String page=remaining.substring(0,cut).trim();
            if(page.isEmpty()) throw new IllegalArgumentException("잘못된 페이지 경계입니다.");
            if(!fits.test(page))throw new IllegalArgumentException("페이지 텍스트가 카드 영역을 넘습니다.");
            pages.add(page);
            remaining=remaining.substring(cut).trim();
        }
        String original=narration.replaceAll("\\s+","");
        StringBuilder rebuilt=new StringBuilder();
        for(String page:pages)rebuilt.append(page.replaceAll("\\s+",""));
        if(!original.equals(rebuilt.toString()))throw new IllegalStateException("원고가 페이지 분할 중 유실되었습니다.");
        return pages;
    }
    private static int chooseBoundary(String src,int best) {
        // Favor complete sentences and paragraphs over splitting a spoken phrase.
        int minimum=Math.max(1,(int)(best*0.52));
        for(int i=best;i>=minimum;i--) {
            char prev=src.charAt(i-1);
            boolean nextWhitespace=i==src.length()||Character.isWhitespace(src.charAt(i));
            if(nextWhitespace && (prev=='.'||prev=='!'||prev=='?'||prev=='。'||prev=='！'||prev=='？'||prev=='\n'))return i;
        }
        for(int i=best;i>=minimum;i--) {
            if(Character.isWhitespace(src.charAt(i-1)))return i;
        }
        return best;
    }
}
