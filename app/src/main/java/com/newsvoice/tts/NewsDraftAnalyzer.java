package com.newsvoice.tts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java, offline draft segmentation for a user-reviewable news-card preview.
 * No source imagery is fetched or presented as authentic news footage here.
 * Existing TTS, cover, music, video, and storage paths remain untouched.
 */
public final class NewsDraftAnalyzer {
    private NewsDraftAnalyzer() {}

    public static final class Card {
        public final String category;
        public final String title;
        public final String body;
        public final String imageSearchHint;
        public Card(String category, String title, String body, String imageSearchHint) {
            this.category = category;
            this.title = title;
            this.body = body;
            this.imageSearchHint = imageSearchHint;
        }
    }

    public static final class Draft {
        public final String broadcast;
        public final String date;
        public final List<Card> cards;
        public Draft(String broadcast, String date, List<Card> cards) {
            this.broadcast = broadcast;
            this.date = date;
            this.cards = cards;
        }
    }

    public static Draft analyze(String script) {
        String raw = script == null ? "" : script.trim();
        String broadcast = raw.contains("광명") && !raw.contains("필리핀") ? "광명시 뉴스"
                : raw.contains("필리핀") ? "필리핀 뉴스" : "방송 종류 확인 필요";
        Matcher dateMatcher = Pattern.compile("(20\\d{2})\\s*년\\s*(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일").matcher(raw);
        String date = dateMatcher.find() ? String.format(Locale.ROOT, "%s-%02d-%02d",
                dateMatcher.group(1), Integer.parseInt(dateMatcher.group(2)),
                Integer.parseInt(dateMatcher.group(3))) : "날짜 확인 필요";

        Pattern heading = Pattern.compile("(?m)^(정치(?:와 경제)?|경제|사회(?:와 안전)?|사건(?:사고)?|안전|한국 관련 특별|한국 특별|기업 대응|오늘의 뉴스.{0,8}앵커 한마디)\\s*(?:소식|뉴스)?(?:입니다)?[.。：]?\\s*$");
        Matcher m = heading.matcher(raw);
        List<Card> cards = new ArrayList<>();
        String category = "도입";
        int from = 0;
        while (m.find()) {
            add(cards, category, raw.substring(from, m.start()));
            category = m.group(1);
            from = m.end();
        }
        add(cards, category, raw.substring(from));
        return new Draft(broadcast, date, cards);
    }

    private static void add(List<Card> cards, String category, String body) {
        String clean = body.trim();
        if (clean.isEmpty()) return;
        String first = clean.split("[.!?。\\n]", 2)[0].trim();
        String title = first.length() > 36 ? first.substring(0, 36) + "…" : first;
        if (title.isEmpty()) title = category;
        String hint = ("광명".equals(category) ? "광명시 " : "") + category + " 관련 자료 사진 (확인 필요)";
        cards.add(new Card(category, title, clean, hint));
    }
}
