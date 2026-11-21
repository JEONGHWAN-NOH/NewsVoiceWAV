import com.newsvoice.tts.NewsCoverMeta;
import java.time.Instant;
public final class NewsCoverMetaTest {
    private static void eq(String a,String b){if(!a.equals(b))throw new AssertionError(a+" != "+b);}
    public static void main(String[] args){
        NewsCoverMeta afternoon=NewsCoverMeta.from(Instant.parse("2026-11-21T05:17:00Z"));
        eq(afternoon.date,"2026.11.21");eq(afternoon.clock,"오후 1시 17분 뉴스");
        NewsCoverMeta morning=NewsCoverMeta.from(Instant.parse("2026-11-20T22:00:00Z"));
        eq(morning.date,"2026.11.21");eq(morning.clock,"오전 6시 뉴스");
        NewsCoverMeta evening=NewsCoverMeta.from(Instant.parse("2026-11-21T10:00:00Z"));
        eq(evening.clock,"오후 6시 뉴스");
        NewsCoverMeta midnight=NewsCoverMeta.from(Instant.parse("2026-11-21T16:01:00Z"));
        eq(midnight.date,"2026.11.22");eq(midnight.clock,"오전 12시 01분 뉴스");
        System.out.println("Manila-time news cover metadata PASS");
    }
}
