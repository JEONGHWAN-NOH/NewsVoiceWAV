import com.newsvoice.tts.NewsBroadcastSegments;
import com.newsvoice.tts.NewsStory;
import java.util.List;

public final class NewsBroadcastSegmentsTest {
    private static void check(boolean value,String message){
        if(!value)throw new AssertionError(message);
    }
    public static void main(String[] args){
        String first="이것은 테스트 문장입니다.";
        String raw=first+" 긴 대본을 읽습니다.".repeat(300);
        check(NewsBroadcastSegments.playback(raw).contains(raw),"literal long preview");
        check(NewsBroadcastSegments.build(raw,"").get(0).audioText().equals("첫 번째 뉴스입니다. "+raw),"literal first WAV");
        String script="첫 제목\n"+first+"\n===\n시장 소식\n다음 뉴스입니다. 시장 본문입니다.\n===\n공항 소식\n공항 본문입니다.";
        List<NewsStory> sections=NewsBroadcastSegments.build(script,"앵커 의견입니다.");
        check(sections.size()==5,"three articles and ending sections");
        String expected="첫 번째 뉴스입니다. "+first+"\n두 번째 뉴스입니다. 시장 본문입니다.\n세 번째 뉴스입니다. 공항 본문입니다.";
        check(NewsBroadcastSegments.playback(script).startsWith(expected),"preview has exactly two transitions");
        check(NewsBroadcastSegments.audioScript(sections).startsWith(expected),"export matches preview");
        check(sections.get(1).title.equals("시장 소식") && sections.get(1).spokenText().equals("시장 본문입니다."),"visual body and title match");
        check(sections.get(2).hasTransition() && sections.get(2).articleNumber==3,"third card audio");
        check(sections.get(3).kind==NewsStory.ANCHOR && sections.get(3).audioText().equals("앵커 의견입니다."),"approved anchor");
        String inline=first+" 다음 뉴스입니다. 시장 본문입니다. 다음 뉴스입니다. 공항 본문입니다.";
        check(NewsBroadcastSegments.newsCount(NewsBroadcastSegments.build(inline,""))==3,"inline markers");
        String supplied="시작 인사\n특별한 인사입니다.\n===\n"+script+
            "\n===\n앵커 한마디\n확인된 사실입니다.\n===\n마감 인사\n마치겠습니다.";
        List<NewsStory> override=NewsBroadcastSegments.build(supplied,"설정한 의견입니다.");
        check(override.size()==6 && NewsBroadcastSegments.audioScript(override).startsWith("특별한 인사입니다."),"explicit greeting");
        check(override.get(4).audioText().equals("확인된 사실입니다.") && override.get(5).audioText().equals("마치겠습니다."),"explicit ending");
        check(NewsBroadcastSegments.remainingNews(override.get(2))==1,"remaining article count");
        String lengthy=("제목\n기사입니다.\n===\n").repeat(31);
        check(NewsBroadcastSegments.playback(lengthy).contains("기사입니다."),"long preview has no video export limit");
        System.out.println("NewsBroadcastSegments PASS");
    }
}
