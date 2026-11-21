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
    System.out.println("NewsScrollMathTest PASS");
  }
}
