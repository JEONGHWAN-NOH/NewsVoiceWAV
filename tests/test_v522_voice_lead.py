from pathlib import Path

base=Path("app/src/main/java/com/newsvoice/tts")
paint=(base/"NewsCardPainter.java").read_text(encoding="utf-8")
composer=(base/"NewsVideoComposer.java").read_text(encoding="utf-8")
curve=(base/"NewsScrollMath.java").read_text(encoding="utf-8")
ui=(base/"MainActivity.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
actions=Path(".github/workflows/build-apk.yml").read_text(encoding="utf-8")
worker=(base/"NewsVoiceWorker.java").read_text(encoding="utf-8")
manual=(base/"ManualRenderService.java").read_text(encoding="utf-8")
opening=(base/"OpeningMusicA.java").read_text(encoding="utf-8")
cover=(base/"NewsCoverExporter.java").read_text(encoding="utf-8")

assert "SCROLL_FONT=56f;" in paint, "font must be one step smaller than 64px"
assert "SCROLL_STEP=84f;" in paint, "line leading must be one step smaller than 92px"
assert "SCROLL_DELAY_US=700_000L;" in composer, "voice lead must be 700 milliseconds"
assert "scrollCurve.delayedPosition(us,SCROLL_DELAY_US)" in composer, "delay must affect text scroll"
assert "current.audioStartUs,current.sentenceEndUs" in composer, "preserve measured sentence WAV cues"
assert "us>=t.spans.get(i).audioStartUs" in composer, "preserve last sentence on screen"
assert "public float delayedPosition(long playbackUs,long delayUs)" in curve
assert "return position(queryUs);" in curve
assert "56px·줄 간격 84px" in ui
assert "0.7초(700ms)" in ui
assert "FPS=30;" in composer
assert "Paint highlight=" not in paint
assert "Paint underline=" not in paint
assert "if(us<4L*US_PER_S)which=-2;" in composer
assert "NewsCoverExporter.save(context,coverMeta," in composer
assert 'Bitmap.CompressFormat.JPEG,95,stream' in cover
for service in [worker,manual]:
    assert "NewsCoverExporter.coverFile(" in service
assert "MUSIC_REVISION=2" in opening
assert "shareLast(true)" in ui and "shareLast(false)" in ui
assert "def releaseVersion = '5.37'" in gradle
assert "applicationId 'com.newsvoice.tts.stable'" in gradle
assert "versionCode 43" in gradle
assert "NewsVoiceWAV-v5.37-debug.apk" in actions
assert "NewsVoiceWAV-v5.37-release-unsigned" in actions
print("PASS: v5.37 font 56px, 84px leading, voice-first 700ms lag and retained existing features")
