import com.newsvoice.tts.NewsBroadcastSegments;
import com.newsvoice.tts.NewsStory;
import java.util.List;
public final class NewsBroadcastSegmentsTest {
    private static void assertTrue(boolean b,String desc){if(!b)throw new AssertionError(desc);}
    public static void main(String[] args) {
        String text="정치 뉴스\n정부는 주요 정책을 발표했습니다.\n===\n경제 뉴스\n전력 공급을 확인했습니다.";
        List<NewsStory> segments=NewsBroadcastSegments.build(text,"추가 소식도 차분히 확인하겠습니다.");
        assertTrue(segments.size()==5,"5 segments");
        assertTrue(NewsBroadcastSegments.newsCount(segments)==2,"only 2 actual news items");
        assertTrue(segments.get(0).kind==NewsStory.OPENING,"opening first");
        assertTrue(segments.get(1).kind==NewsStory.NEWS&&segments.get(2).kind==NewsStory.NEWS,"news middle");
        assertTrue(segments.get(3).kind==NewsStory.ANCHOR,"anchor last corner");
        assertTrue(segments.get(4).kind==NewsStory.CLOSING,"closing final");
        assertTrue(segments.get(3).narration.contains("차분히"),"custom commentary used");
        String spoken=NewsBroadcastSegments.audioScript(segments);
        assertTrue(spoken.startsWith("안녕하십니까."),"opening spoken");
        assertTrue(spoken.contains("정부는 주요 정책을 발표했습니다."),"news 1 body spoken");
        assertTrue(spoken.contains("전력 공급을 확인했습니다."),"news 2 body spoken");
        assertTrue(!spoken.contains("정치 뉴스")&&!spoken.contains("경제 뉴스"),
            "audio-only script excludes news headlines");
        assertTrue(spoken.contains("추가 소식도 차분히 확인하겠습니다."),
            "anchor comment spoken");
        assertTrue(spoken.endsWith("시청해 주셔서 감사합니다."),
            "closing spoken");
        NewsStory opening=segments.get(0).page(segments.get(0).narration,0,2,1,1);
        NewsStory second=segments.get(2).page(segments.get(2).narration,2,2,2,3);
        assertTrue(NewsBroadcastSegments.remainingNews(opening)==2,"opening has 2 remaining");
        assertTrue(NewsBroadcastSegments.remainingNews(second)==0,"second item is last");
        assertTrue(NewsBroadcastSegments.remainingNews(segments.get(3))==0,"anchor 100 percent");
        String withAnchor="앵커 한마디\n뉴스 후속 사항은 공식 발표로 확인하겠습니다.\n===\n"+text;
        List<NewsStory> moved=NewsBroadcastSegments.build(withAnchor,"");
        assertTrue(moved.size()==5&&NewsBroadcastSegments.newsCount(moved)==2,"no duplicate segment");
        assertTrue(moved.get(3).narration.contains("후속 사항"),"user anchor moved to last");
        try{
            NewsBroadcastSegments.build(text,"그렇게 하면 돼.");
            throw new AssertionError("informal comment must be blocked");
        }catch(IllegalArgumentException expected){}
        try {
            NewsBroadcastSegments.build("제목만 있는 뉴스", "");
            throw new AssertionError("headline-only article must be rejected");
        }catch(IllegalArgumentException expected){}
        System.out.println("NewsBroadcastSegments PASS");
    }
}
