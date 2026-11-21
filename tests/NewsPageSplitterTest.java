import com.newsvoice.tts.NewsPageSplitter;
import java.util.List;
public class NewsPageSplitterTest {
  private static void check(boolean x,String msg){if(!x)throw new AssertionError(msg);}
  public static void main(String[] args){
    String src="첫 번째 뉴스입니다. 정부는 정책을 발표했습니다. 시민들의 반응이 이어졌습니다. "+
      "두 번째 내용입니다. 예산안의 조정은 최종 확정이 아닙니다.";
    List<String> pages=NewsPageSplitter.split(src,s->s.length()<=53);
    check(pages.size()>=2,"page count");
    String merged=String.join("",pages).replaceAll("\\s+","");
    check(merged.equals(src.replaceAll("\\s+","")),"content preservation");
    for(String page:pages)check(page.length()<=53,"page too long");
    String longWord="가".repeat(250);
    List<String> charPages=NewsPageSplitter.split(longWord,s->s.length()<=67);
    check(String.join("",charPages).equals(longWord),"no lost words");
    check(NewsPageSplitter.split("짧은 문장",s->s.length()<=67).size()==1,"short phrase");
    System.out.println("NewsPageSplitter PASS: "+pages.size()+" paragraphs, "+charPages.size()+" long-token pages");
  }
}
