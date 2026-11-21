import com.newsvoice.tts.NewsSentenceTimeline;
import java.util.List;
public class NewsSentenceTimelineTest {
  private static void check(boolean x,String why){if(!x)throw new AssertionError(why);}
  public static void main(String[] args){
    String script="필리핀 물가는 16.80%입니다. 정부는 발표했습니다! \"이어 확인하겠습니다.\"\n시장은 관망했습니다.";
    List<String> parts=NewsSentenceTimeline.split(script);
    check(parts.size()==4,"sentence boundaries="+parts);
    check(parts.get(0).contains("16.80%"),"decimal number preserved");
    check(parts.get(2).endsWith("\""),"closing quote preserved");
    String combined=String.join("",parts).replaceAll("\\s+","");
    check(combined.equals(script.replaceAll("\\s+","")),"not a single character lost");
    int[] range=NewsSentenceTimeline.visibleRange(parts,1);
    check(range[0]==NewsSentenceTimeline.visibleLength(parts.get(0)),"start cue");
    check(range[1]-range[0]==NewsSentenceTimeline.visibleLength(parts.get(1)),"end cue");
    check(NewsSentenceTimeline.visibleRange(parts,-1)[0]==-1,"no premature highlight");
    check(NewsSentenceTimeline.split("한 문장입니다.").size()==1,"one sentence");
    check(NewsSentenceTimeline.split("날짜는 2026.11.21입니다.").size()==1,"date decimals");
    System.out.println("NewsSentenceTimeline PASS: "+parts);
  }
}
