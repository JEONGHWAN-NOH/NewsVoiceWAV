package com.newsvoice.tts;

import java.util.ArrayList;
import java.util.List;

public final class NewsBroadcastSegments {
    private NewsBroadcastSegments() {}
    public static String playback(String raw) {
        return raw.replace("\uFEFF","").trim();
    }

    public static List<NewsStory> build(String raw) {
        String script = playback(raw);
        if (script.isEmpty()) throw new IllegalArgumentException("원고를 입력하세요.");
        return java.util.Collections.singletonList(new NewsStory(script, 1, 1, NewsStory.NEWS));
    }

    public static String audioScript(List<NewsStory> sections) {
        List<String> bodies = new ArrayList<>();
        for (NewsStory section : sections) bodies.add(section.audioText());
        return String.join("\n", bodies);
    }
}

