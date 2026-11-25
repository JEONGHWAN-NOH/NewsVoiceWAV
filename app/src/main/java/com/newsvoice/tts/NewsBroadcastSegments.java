package com.newsvoice.tts;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public final class NewsBroadcastSegments {
    private NewsBroadcastSegments() {}
    private static final Pattern ARTICLE=Pattern.compile(
        "(?m)(?:^|(?<=[.!?。])(?=\\s))\\s*((?:(?:[0-9]+|첫|두|세|네|다섯|여섯|일곱|여덟|아홉|열|열\\s*한|열\\s*두|열세|열네|열다섯|열여섯|열일곱|열여덟|열아홉|스무)\\s*번째\\s*(?:뉴스|소식)입니다[.!]?)"
        +")(?=\\s|$)");
    public static String playback(String raw) {
        return raw.replace("\uFEFF","").trim();
    }

    public static List<NewsStory> build(String raw) {
        String script = playback(raw);
        if (script.isEmpty()) throw new IllegalArgumentException("원고를 입력하세요.");
        Matcher cues = ARTICLE.matcher(script);
        List<Integer> starts = new ArrayList<>();
        while (cues.find()) starts.add(cues.start(1));
        List<NewsStory> stories = new ArrayList<>();
        if (starts.isEmpty()) {
            stories.add(new NewsStory(script, 1, 1, NewsStory.NEWS));
            return stories;
        }
        String intro = script.substring(0, starts.get(0)).trim();
        if (!intro.isEmpty()) stories.add(new NewsStory(intro, 0, starts.size(), NewsStory.OPENING));
        for (int i = 0; i < starts.size(); i++) {
            int end = i + 1 < starts.size() ? starts.get(i + 1) : script.length();
            stories.add(new NewsStory(script.substring(starts.get(i), end).trim(), i + 1, starts.size(), NewsStory.NEWS));
        }
        return stories;
    }

    public static String audioScript(List<NewsStory> sections) {
        List<String> bodies = new ArrayList<>();
        for (NewsStory section : sections) bodies.add(section.audioText());
        return String.join("\n", bodies);
    }
}
