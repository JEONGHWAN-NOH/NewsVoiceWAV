import com.newsvoice.tts.NewsScrollMath;
public final class NewsScrollMathTest{
  static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
  public static void main(String[] args){
    int[] rows={12,12,0,8,8,8,8,8,8,8,8,8};
    check(NewsScrollMath.offset(rows,0,120f,3f,900f)==0f,"starts at top");
    check(NewsScrollMath.offset(rows,12f,120f,3f,900f)==0f,"first rows stationary");
    float prior=0;
    for(int i=0;i<=112;i++){
      float actual=NewsScrollMath.offset(rows,i,120f,3f,900f);
      check(actual>=prior,"scroll must never jump backwards");
      check(actual<=900f,"never exceed the final scroll bound");
      prior=actual;
    }
    check(NewsScrollMath.offset(rows,200f,120f,3f,900f)==900f,"end clamped");
    check(NewsScrollMath.rowPosition(new int[]{4,5},2f)==0.5f,"within-line progress");
    check(NewsScrollMath.rowPosition(new int[]{4,5},4f)==1f,"next-line continuity");
    check(NewsScrollMath.offset(new int[]{4,5},4f,120f,3f,0f)==0f,"short news never moves");
    // Article audio lasts 8 seconds. The text must move at a uniform speed,
    // including sentence gaps and sections with silent audio.
    float scroll=480f;
    float previous=-1;
    for(int second=0;second<=8;second++){
      float y=NewsScrollMath.continuousOffset(6_000_000L+second*1_000_000L,
          6_000_000L,14_000_000L,scroll);
      check(y>=previous,"continuous monotonic scroll");
      check(Math.abs(y-second*60f)<0.001f,"constant scrolling speed");
      previous=y;
    }
    check(NewsScrollMath.continuousOffset(5_000_000L,6_000_000L,
        14_000_000L,scroll)==0f,"pre-roll does not move");
    check(NewsScrollMath.continuousOffset(17_000_000L,6_000_000L,
        14_000_000L,scroll)==scroll,"scroll ends at text bottom");
    check(NewsScrollMath.continuousOffset(10_000_000L,6_000_000L,
        14_000_000L,0f)==0f,"short text stays stationary");

    // Voice speed changes by sentence. Scroll must hit exact measured cues
    // without any position or velocity jump at sentence boundaries.
    long[] audioEnds={2_000_000L,6_000_000L,8_000_000L};
    int[] characters={0,12,65,100};
    NewsScrollMath.AudioCurve curve=NewsScrollMath.audioCurve(0,audioEnds,
        characters,100,800f);
    check(curve.sentenceCount()==3,"measured sentence count preserved");
    check(curve.position(-1L)==0f,"voice pre-roll stationary");
    check(Math.abs(curve.position(2_000_000L)-96f)<0.001f,"first sentence aligned");
    check(Math.abs(curve.position(6_000_000L)-520f)<0.001f,"second sentence aligned");
    check(curve.position(8_000_000L)==800f,"entire narration shown");
    check(curve.position(9_000_000L)==800f,"final scroll bounded");
    // Old constant speed would be 400px at 4s; actual speech requires
    // a substantially different position after the short first sentence.
    check(Math.abs(curve.position(4_000_000L)-
        NewsScrollMath.continuousOffset(4_000_000L,0,8_000_000L,800f))>40f,
        "sentence-specific WAV timing must change article scroll speed");
    float lastY=-1f;
    for(long t=0;t<=8_000_000L;t+=10_000L){
        float y=curve.position(t);
        check(y>=lastY,"voice-aligned scroll remains monotone");
        if(t>0 && t<8_000_000L)check(y>lastY,"no pauses at sentence breaks");
        lastY=y;
    }
    for(long boundary:new long[]{2_000_000L,6_000_000L}){
        float left=(curve.position(boundary)-curve.position(boundary-1_000L));
        float right=(curve.position(boundary+1_000L)-curve.position(boundary));
        check(Math.abs(left-right)<0.05f,"sentence-boundary speed must be smooth");
    }
    NewsScrollMath.AudioCurve shortArticle=NewsScrollMath.audioCurve(
        2000L,new long[]{1_002_000L},new int[]{0,16},16,0f);
    check(shortArticle.position(500_000L)==0f,"fitting text does not scroll");
    boolean rejected=false;
    try {
        NewsScrollMath.audioCurve(0,new long[]{3,3},new int[]{0,2,4},4,100f);
    } catch(IllegalArgumentException expected){rejected=true;}
    check(rejected,"invalid WAV cue chronology must be rejected");
    // v5.23: the announcer must lead the moving text by exactly 700ms.
    long delay=700_000L;
    check(curve.delayedPosition(-1L,delay)==0f,
        "before narration the screen must remain stationary");
    check(curve.delayedPosition(0L,delay)==0f,
        "when the voice starts the text stays still for 700ms");
    check(curve.delayedPosition(699_999L,delay)==0f,
        "no premature scroll before the 700ms delay");
    check(curve.delayedPosition(700_000L,delay)==0f,
        "exactly at the scroll start frame");
    check(curve.delayedPosition(700_001L,delay)>0f,
        "scroll begins just after 700ms");
    for(long playedUs=701_000L;playedUs<=8_000_000L;playedUs+=73_000L){
        float expected=curve.position(playedUs-delay);
        float actual=curve.delayedPosition(playedUs,delay);
        check(Math.abs(expected-actual)<0.001f,
            "scroll position must equal the WAV position 0.7 seconds earlier");
        check(actual<=curve.position(playedUs),
            "narration must always lead scrolling");
    }
    check(curve.delayedPosition(8_700_000L,delay)==800f,
        "full scroll completes 700ms after the final voice cue");
    boolean invalidDelay=false;
    try{curve.delayedPosition(0,-700_000L);}
    catch(IllegalArgumentException expected){invalidDelay=true;}
    check(invalidDelay,"negative narration delays are rejected");

    // v5.24: center the actually spoken line using wrapped-row boundaries
    // and the exact WAV duration of every sentence (not a total-time ratio).
    int[] focusedRows={8,8,8,8,8};
    int[] focusedSentenceBoundaries={0,8,24,40};
    long[] focusedEnds={1_000_000L,3_000_000L,9_000_000L};
    float focusedStep=84f,focusedMax=336f,focusedBaseline=1110f;
    NewsScrollMath.AudioCurve centered=NewsScrollMath.centeredAudioCurve(0L,
        focusedEnds,focusedSentenceBoundaries,focusedRows,
        focusedStep,focusedMax);
    check(centered.sentenceCount()>=3,
        "spoken sentences plus wrapped-row timing anchors are present");
    check(centered.position(0)==0f,"first spoken row starts at screen center");
    check(Math.abs(centered.position(1_000_000L)-84f)<0.001f,
        "second visual row should be centered at the first WAV sentence cue");
    check(Math.abs(centered.position(2_000_000L)-168f)<0.001f,
        "third visual row is centered at its time inside the second sentence");
    check(Math.abs(centered.position(3_000_000L)-252f)<0.001f,
        "fourth visual row is centered at the second sentence boundary");
    check(Math.abs(centered.position(9_000_000L)-336f)<0.001f,
        "final visual row reaches the center by the end of the final voice cue");
    check(Math.abs(focusedBaseline+2*focusedStep-
        centered.position(2_000_000L)-focusedBaseline)<0.001f,
        "current printed line baseline must remain at vertical reading focus");
    check(Math.abs(centered.delayedPosition(2_700_000L,700_000L)-168f)<0.001f,
        "approved 700ms audio lead remains intact");
    float prevFocused=-1f;
    for(long t=0;t<=9_000_000L;t+=10_000L){
        float y=centered.position(t);
        check(y>=prevFocused,"line-centered interpolator is monotone");
        check(y>=0f && y<=focusedMax,"line-centered scroll stays within video");
        prevFocused=y;
    }
    for(long boundaryUs:new long[]{1_000_000L,2_000_000L,3_000_000L}){
        float before=centered.position(boundaryUs)-
            centered.position(boundaryUs-1_000L);
        float after=centered.position(boundaryUs+1_000L)-
            centered.position(boundaryUs);
        check(Math.abs(before-after)<0.2f,
            "no speed jumps at sentence or wrapped-line boundaries");
    }
    NewsScrollMath.AudioCurve singleLine=NewsScrollMath.centeredAudioCurve(
        4_000L,new long[]{2_004_000L},new int[]{0,4},new int[]{4},
        focusedStep,0f);
    check(singleLine.position(1_000_000L)==0f,
        "one-line news should remain centered without unnecessary scrolling");
    boolean brokenFocusRejected=false;
    try{
        NewsScrollMath.centeredAudioCurve(0,new long[]{1_000_000L},
            new int[]{0,4},new int[]{3},focusedStep,focusedMax);
    }catch(IllegalArgumentException expected){brokenFocusRejected=true;}
    check(brokenFocusRejected,"character count mismatch must be rejected");
    System.out.println("NewsScrollMathTest PASS");
  }
}
