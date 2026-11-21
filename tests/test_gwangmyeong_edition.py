from pathlib import Path
base=Path("app/src/main/java/com/newsvoice/tts")
card=(base/"NewsCardPainter.java").read_text()
meta=(base/"NewsCoverMeta.java").read_text()
segments=(base/"NewsBroadcastSegments.java").read_text()
publisher=(base/"DownloadsPublisher.java").read_text()
manifest=Path("app/src/main/AndroidManifest.xml").read_text()
gradle=Path("app/build.gradle").read_text()
worker=(base/"NewsVoiceWorker.java").read_text()
manual=(base/"ManualRenderService.java").read_text()
assert 'ZoneId.of("Asia/Seoul")' in meta
assert "renderCityCover(meta,count,stage)" in card
assert "GWANGMYEONG  |  MORNING NEWS" in card
assert "GWANGMYEONG  |  AFTERNOON NEWS" in card
assert "philippines_cover.webp" not in card
assert "광명시 종합 뉴스 브리핑" in segments
assert "GwangmyeongNews" in publisher
assert 'android:label="광명시 뉴스 WAV 1.1"' in manifest
assert "com.newsvoice.tts.gwangmyeong.v1" in gradle
assert "GwangmyeongNews" in worker and "GwangmyeongNews" in manual
assert "synthesize(pages.get(i).spokenText()" in worker
assert "synthesize(pages.get(i).spokenText()" in manual
assert "NewsPageSplitter.split(body, part->" in card
print("PASS: Gwangmyeong branding, KST, independent storage, body-only narration, auto page")
