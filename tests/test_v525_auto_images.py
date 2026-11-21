from pathlib import Path
root=Path("app/src/main/java/com/newsvoice/tts")
card=(root/"NewsCardPainter.java").read_text(encoding="utf-8")
video=(root/"NewsVideoComposer.java").read_text(encoding="utf-8")
fetcher=(root/"NewsImageFetcher.java").read_text(encoding="utf-8")
worker=(root/"NewsVoiceWorker.java").read_text(encoding="utf-8")
auto=(root/"NewsAutoScript.java").read_text(encoding="utf-8")
segments=(root/"NewsBroadcastSegments.java").read_text(encoding="utf-8")
ui=(root/"MainActivity.java").read_text(encoding="utf-8")
assert 'NewsAutoScript.adapt(raw)' in segments
assert '첫 번째' not in auto or '첫' in auto  # Korean ordinals recognized in regex
assert 'drawReferenceThumbnail(c,photo,title);' in card
assert 'yellow news-category tab' not in card
assert '전체 뉴스 진행' in card
assert 'broadcastProgress' in card and 'totalProgress' in video
assert 'firstNews' in video and 'lastNews' in video
assert 'NewsImageFetcher.fetch(pages.get(i))' in worker
assert 'saveDownloads(made.credits,names.credits())' in worker
assert 'https://commons.wikimedia.org/w/api.php' in fetcher
assert 'allowedLicense' in fetcher
assert '자료 이미지 · Wikimedia Commons' in card
assert '0.7초(700ms)' in ui
print("PASS: v5.30 script-only narration, reference images, credits and full-video progress")
