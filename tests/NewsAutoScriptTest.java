import com.newsvoice.tts.NewsAutoScript;
import com.newsvoice.tts.NewsBroadcastSegments;
import com.newsvoice.tts.NewsStory;
import java.util.List;
public final class NewsAutoScriptTest {
    private static void check(boolean yes,String reason) {
        if(!yes)throw new AssertionError(reason);
    }
    public static void main(String[] args){
        String raw="안녕하십니까. 오늘의 주요 뉴스를 전해드립니다.\n"
            +"먼저 정치 분야입니다.\n\n"
            +"첫 번째 소식입니다.\n"
            +"필리핀 정부가 새로운 계획을 발표했습니다. 최종 일정은 확인되지 않았습니다.\n\n"
            +"이어서 경제 분야입니다.\n"
            +"두 번째 소식입니다.\n"
            +"칼락스 고속도로 공정률이 97퍼센트입니다. 개통일은 미정입니다.\n\n"
            +"오늘의 앵커 한마디입니다.\n"
            +"정부 발표와 실제 사업을 구분해 살펴보겠습니다.\n\n"
            +"이상으로 오늘의 필리핀 뉴스 브리핑을 마칩니다.\n감사합니다.";
        check(NewsAutoScript.isAutoFormat(raw),"full numbered script detected");
        String converted=NewsAutoScript.adapt(raw);
        check(converted.contains("시작 인사\n"),"original introduction is opening");
        check(converted.contains("앵커 한마디\n"),"anchor preserved");
        check(converted.contains("마감 인사\n"),"closing preserved");
        List<NewsStory> sections=NewsBroadcastSegments.build(raw,"");
        check(sections.size()==5,"opening + two articles + anchor + closing");
        check(NewsBroadcastSegments.newsCount(sections)==2,"two news images");
        check(sections.get(1).title.contains("필리핀 정부가"),"title from input facts only");
        check(sections.get(2).title.contains("칼락스"),"second title from input");
        String spoken=NewsBroadcastSegments.audioScript(sections);
        check(spoken.contains("첫 번째 소식입니다."),"original spoken article cue retained");
        check(spoken.contains("두 번째 소식입니다."),"second spoken cue retained");
        check(spoken.contains("정부 발표와 실제 사업을"),"anchor spoken");
        check(spoken.contains("감사합니다."),"closing spoken");
        check(!spoken.contains("뉴스 1 ·"),"display headline not spoken");
        String legacy="정치 뉴스\n정부는 발표했습니다.\n===\n경제 뉴스\n시장은 반응했습니다.";
        check(NewsAutoScript.adapt(legacy).equals(legacy),"existing separator format unmodified");
        String special="첫 번째 소식입니다.\n정부는 발표했습니다.\n"
            +"두 번째 소식입니다.\n기업은 투자했습니다.\n"
            +"다음은 한국과 필리핀 관련 특별뉴스입니다.\n양국은 협력했습니다.";
        check(NewsBroadcastSegments.newsCount(
            NewsBroadcastSegments.build(special,""))==3,"special Korean news counts in progress");
        System.out.println("NewsAutoScript PASS");
    }
}
