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
assert 'android:label="광명시 뉴스 WAV 1.2"' in manifest
assert "com.newsvoice.tts.gwangmyeong.v1" in gradle
assert "GwangmyeongNews" in worker and "GwangmyeongNews" in manual
assert "pages.get(i).spokenText()" in worker and "SpeechCueBuilder.make(context" in worker
assert "pages.get(i).spokenText()" in manual and "SpeechCueBuilder.make(context" in manual
assert "NewsPageSplitter.split(body, part->" in card
print("PASS: Gwangmyeong branding, KST, independent storage, body-only narration, auto page")

composer=(base/"NewsVideoComposer.java").read_text()
cues=(base/"SpeechCueBuilder.java").read_text()
ui=(base/"MainActivity.java").read_text()
assert 'bundledOpeningA(context)' in composer and 'appendOpeningMusic(out,introWav' in composer
assert 'new File(context.getFilesDir(),"newsvoice_opening_A.wav")' not in composer
assert 'activeSentence' in composer and 'sentenceEndUs' in composer
assert 'drawActiveSentence(c,row,layout,y,seen' in card
assert 'String sentence' in cues
assert 'A타입 6초 오프닝' in ui
