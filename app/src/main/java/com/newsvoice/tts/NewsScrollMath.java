package com.newsvoice.tts;

/**
 * Pure scrolling geometry: follows spoken character progress through wrapped
 * visual rows. Keeps the active text near the middle of a bounded viewport
 * without reducing the font or throwing away source characters.
 */
public final class NewsScrollMath {
    private NewsScrollMath(){ }
    public static float clamp(float value,float min,float max) {
        return Math.max(min,Math.min(max,value));
    }
    public static float rowPosition(int[] visibleCharactersPerRow,float spokenCharacters){
        if(visibleCharactersPerRow==null || visibleCharactersPerRow.length==0)
            throw new IllegalArgumentException("뉴스 본문의 행이 없습니다.");
        float left=Math.max(0,spokenCharacters);
        for(int i=0;i<visibleCharactersPerRow.length;i++){
            int length=visibleCharactersPerRow[i];
            if(length<0)throw new IllegalArgumentException("행 길이가 잘못됐습니다.");
            if(length==0)continue;
            if(left<length)return i+left/length;
            left-=length;
        }
        return visibleCharactersPerRow.length;
    }
    public static float offset(int[] rows,float spokenCharacters,
                               float rowStep,float focusRows,float maxOffset){
        if(rowStep<=0||maxOffset<0)throw new IllegalArgumentException("스크롤 크기가 잘못됐습니다.");
        return clamp((rowPosition(rows,spokenCharacters)-focusRows)*rowStep,0,maxOffset);
    }
}
