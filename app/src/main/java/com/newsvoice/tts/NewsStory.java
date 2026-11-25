package com.newsvoice.tts;

public final class NewsStory {
    public static final int NEWS = 0, OPENING = 1;
    public final int articleNumber, articleCount, kind;
    private final String body;

    NewsStory(String body, int articleNumber, int articleCount, int kind) {
        this.body = body;
        this.articleNumber = articleNumber;
        this.articleCount = articleCount;
        this.kind = kind;
    }

    public String spokenText() { return body; }
    public String audioText() { return body; }
}
