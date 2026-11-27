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
        check(!converted.contains("앵커 한마디\n"),"anchor removed");
        check(converted.contains("마감 인사\n"),"closing preserved");
        List<NewsStory> sections=NewsBroadcastSegments.build(raw,"");
        check(sections.size()==4,"opening + two articles + anchor + closing");
        check(NewsBroadcastSegments.newsCount(sections)==2,"two news images");
        check(sections.get(1).title.contains("필리핀 정부가"),"title from input facts only");
        check(sections.get(2).title.contains("칼락스"),"second title from input");
        String spoken=NewsBroadcastSegments.audioScript(sections);
        check(spoken.contains("첫 번째 소식입니다."),"original spoken article cue retained");
        check(spoken.contains("두 번째 소식입니다."),"second spoken cue retained");
        check(!spoken.contains("정부 발표와 실제 사업을"),"anchor skipped");
        check(spoken.contains("감사합니다."),"closing spoken");
        check(!spoken.contains("뉴스 1 ·"),"display headline not spoken");
        String legacy="정치 뉴스\n정부는 발표했습니다.\n===\n경제 뉴스\n시장은 반응했습니다.";
        check(NewsAutoScript.adapt(legacy).equals(legacy),"existing separator format unmodified");
        String special="첫 번째 소식입니다.\n정부는 발표했습니다.\n"
            +"두 번째 소식입니다.\n기업은 투자했습니다.\n"
            +"다음은 한국과 필리핀 관련 특별뉴스입니다.\n양국은 협력했습니다.";
        check(NewsBroadcastSegments.newsCount(
            NewsBroadcastSegments.build(special,""))==3,"special Korean news counts in progress");
        String one="안녕하십니까. 오늘의 주요 뉴스를 전해드립니다.\n첫 번째 소식입니다.\n필리핀 금융시장에서 경기 둔화 우려가 이어지고 있습니다.";
        check(NewsAutoScript.isAutoFormat(one),"single story auto detected");
        List<NewsStory> oneSections=NewsBroadcastSegments.build(one,"");
        check(NewsBroadcastSegments.newsCount(oneSections)==1,"single story recognized");
        check(oneSections.get(1).title.contains("필리핀 금융시장"),"single story headline from news, not greeting");
        check(!oneSections.get(1).title.contains("안녕하십니까"),"greeting excluded from headline");
        String numbered="안녕하십니까. ";
        for(String n:new String[]{"첫","두","세","네","다섯","여섯","일곱","여덟","아홉","열","열 한","열두"})
            numbered+=n+" 번째 뉴스입니다. 기사 본문입니다. ";
        numbered+="오늘의 뉴스, 앵커 한마디입니다. 내용입니다. 이상 필리핀 뉴스였습니다. 감사합니다.";
        List<NewsStory> all=NewsBroadcastSegments.build(numbered,"");
        check(all.size()==14 && all.get(0).kind==NewsStory.OPENING,"numbered opening and twelve stories");
        for(int i=1;i<=12;i++)check(all.get(i).articleNumber==i && !all.get(i).audioText().startsWith("다음 뉴스"),"slot "+i);
        check(all.get(13).kind==NewsStory.CLOSING,"spoken endings");
        String mixed="안녕하십니까.\n첫 번째 뉴스입니다. 첫 기사입니다.\n";
        for(int i=2;i<=8;i++)mixed+=(i%2==0?"다음 뉴스입니다.":i+"번 뉴스입니다.")+" 기사 "+i+" 본문입니다.\n";
        List<NewsStory> eight=NewsBroadcastSegments.build(mixed,"",true);
        check(NewsBroadcastSegments.newsCount(eight)==8,"mixed ordinal, numeric and next news markers");
        for(int i=1;i<=8;i++)check(eight.get(i).articleNumber==i,"mixed slot "+i);
        System.out.println("NewsAutoScript PASS");
    }
}
