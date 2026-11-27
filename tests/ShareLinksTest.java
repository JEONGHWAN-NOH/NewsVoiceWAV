package com.newsvoice.tts;

public class ShareLinksTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        String id="aBcDeFg1234", expected="https://www.youtube.com/watch?v="+id;
        check(ShareLinks.youtube("youtu.be/"+id+"?si=example").equals(expected));
        check(ShareLinks.youtube("https://www.youtube.com/shorts/"+id).equals(expected));
        check(ShareLinks.youtube(expected+"&t=1").equals(expected));
        check(ShareLinks.youtube("https://youtube.com/@channel").isEmpty());
        check(ShareLinks.youtube("https://youtube.com.example.org/watch?v="+id).isEmpty());
        check(ShareLinks.youtube("https://youtube.com@evil.example/watch?v="+id).isEmpty());
        check(ShareLinks.mp4("https://example.org/news.mp4").equals("https://example.org/news.mp4"));
        check(ShareLinks.mp4("file:///news.mp4").isEmpty());
        check(ShareLinks.message("https://example.org/news.mp4",expected).contains("https://example.org/news.mp4\n\n▶ YouTube"));
        check(ShareLinks.message("",expected).contains("함께 전송된 동영상"));
        System.out.println("ShareLinksTest passed");
    }
}
