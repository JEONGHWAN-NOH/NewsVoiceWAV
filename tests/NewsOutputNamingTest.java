import com.newsvoice.tts.NewsOutputNaming;
import java.time.Instant;

public final class NewsOutputNamingTest {
    private static void assertEquals(String expected,String actual) {
        if(!expected.equals(actual))throw new AssertionError("expected "+expected+" but got "+actual);
    }
    private static void check(boolean p,String m){if(!p)throw new AssertionError(m);}
    public static void main(String[] args){
        NewsOutputNaming.Names am=NewsOutputNaming.at(Instant.parse("2026-11-21T22:15:36Z"));
        assertEquals("2026-11-22",am.dateFolder);
        assertEquals("2026-11-22_오전7시_광명시뉴스_071536",am.base);
        assertEquals(am.base+".wav",am.wav());
        assertEquals(am.base+".mp4",am.mp4());
        assertEquals(am.base+"_cover.jpg",am.jpg());
        assertEquals(am.base+"_image_credits.txt",am.credits());
        check(am.wav().replace(".wav","").equals(am.mp4().replace(".mp4","")),
            "WAV/MP4 names must share identical story/clock prefix");
        assertEquals("2026-11-22",NewsOutputNaming.folderFor(am.mp4(),Instant.EPOCH));
        assertEquals("2026-11-22",NewsOutputNaming.folderFor(am.credits(),Instant.EPOCH));

        NewsOutputNaming.Names pm=NewsOutputNaming.at(Instant.parse("2026-11-22T10:07:05Z"));
        assertEquals("2026-11-22",pm.dateFolder);
        assertEquals("2026-11-22_오후7시_광명시뉴스_190705",pm.base);
        NewsOutputNaming.Names next=NewsOutputNaming.at(Instant.parse("2026-11-22T16:00:08Z"));
        assertEquals("2026-11-23",next.dateFolder);
        assertEquals("2026-11-23_오전7시_광명시뉴스_010008",next.base);
        check(!am.mp4().equals(pm.mp4()),"two sessions are distinguishable");
        check(!am.mp4().equals(NewsOutputNaming.at(Instant.parse("2026-11-21T22:15:37Z")).mp4()),
            "repeated builds in adjacent seconds do not collide");
        assertEquals("2026-11-22",NewsOutputNaming.folderFor("newsvoice_old.wav",Instant.parse("2026-11-21T19:00:00Z")));
        assertEquals("2026-11-22",NewsOutputNaming.folderFor(
            "2026-11-22_오전7시_광명시뉴스_071536.mp4",Instant.EPOCH));
        System.out.println("NewsOutputNaming PASS");
    }
}
