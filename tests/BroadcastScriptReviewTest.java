import com.newsvoice.tts.BroadcastScriptReview;

public final class BroadcastScriptReviewTest {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        String raw="정치 뉴스\n필리핀 정부가 오늘 발표했다.다음 계획도 밝혔다.\n===\n경제 뉴스\n투자 금액은 1,200만 페소이다. 성장률은 2.50%였다.";
        BroadcastScriptReview.Result a=BroadcastScriptReview.review(raw);
        check(a.approved(),"expected approval: "+a.summary());
        check(a.reviewed.contains("발표했습니다. 다음"),"period spacing");
        check(a.reviewed.contains("계획도 밝혔습니다."),"formal ending");
        check(a.reviewed.contains("1,200만 페소입니다."),"preserve integer formatting");
        check(a.reviewed.contains("2.50%였습니다."),"preserve decimal formatting");
        check(a.reviewed.contains("\n===\n"),"article separator");
        BroadcastScriptReview.Result b=BroadcastScriptReview.review("테스트\n나는 내일 갈게.");
        check(!b.approved(),"informal expression must be flagged");
        check(BroadcastScriptReview.review("정치 뉴스\n오늘 발표했습니다.\n===\n사회 뉴스\n문제가 없습니다.").approved(),"polite should pass");
        BroadcastScriptReview.Result d=BroadcastScriptReview.review("재판 뉴스\n\"그만해.\"라고 말했다.");
        check(!d.approved(),"quoted expression should require review");
        check(BroadcastScriptReview.review("정부가 발표했다.").approved(),"one line article should be checked");
        check(BroadcastScriptReview.review("인사말\n안녕하십니까. 감사합니다.").approved(),"formal -합니다 ending");
        System.out.println("BroadcastScriptReview PASS");
    }
}
