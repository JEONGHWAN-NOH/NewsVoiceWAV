import com.newsvoice.tts.NewsStory;
import java.util.List;
public class NewsStoryTest {
  public static void main(String[] args) {
    List<NewsStory> stories=NewsStory.parse("첫 번째 뉴스\n중요한 내용을 전달합니다.\n===\n두 번째 뉴스\n추가 기사입니다.");
    if(stories.size()!=2)throw new AssertionError("story count");
    if(!stories.get(1).title.equals("두 번째 뉴스"))throw new AssertionError("title");
    if(!stories.get(0).narration.contains("중요한"))throw new AssertionError("narration");
    if(!stories.get(0).spokenText().equals("중요한 내용을 전달합니다."))
        throw new AssertionError("news headline must not be spoken");
    if(stories.get(0).spokenText().contains("첫 번째 뉴스"))
        throw new AssertionError("headline leaked into news voice");
    if(!stories.get(0).page(stories.get(0).narration,1,2,1,1)
        .spokenText().equals("중요한 내용을 전달합니다."))
        throw new AssertionError("paged news headline must not be spoken");
    if(!NewsStory.segment(NewsStory.OPENING,"시작 인사","안녕하십니까.",2)
        .spokenText().equals("안녕하십니까."))
        throw new AssertionError("spoken opening must remain");
    if(NewsStory.parse("하나의 뉴스만 있습니다.").size()!=1)throw new AssertionError("single");
    System.out.println("NewsStory parser: PASS");
  }
}
