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
    System.out.println("NewsScrollMathTest PASS");
  }
}
