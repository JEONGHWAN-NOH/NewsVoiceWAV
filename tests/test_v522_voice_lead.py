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

assert "SCROLL_FONT=64f;" in paint, "font must be two steps smaller than 72px"
assert "SCROLL_STEP=92f;" in paint, "line leading must be two steps smaller than 100px"
assert "SCROLL_DELAY_US=300_000L;" in composer, "voice lead must be 300 milliseconds"
assert "scrollCurve.delayedPosition(us,SCROLL_DELAY_US)" in composer, "delay must affect text scroll"
assert "current.audioStartUs,current.sentenceEndUs" in composer, "preserve measured sentence WAV cues"
assert "i==0?0L:SCROLL_DELAY_US" in composer, "preserve last sentence on screen"
assert "public float delayedPosition(long playbackUs,long delayUs)" in curve
assert "return position(queryUs);" in curve
assert "64px·줄 간격 92px" in ui
assert "0.3초(300ms)" in ui
assert "FPS=30;" in composer
assert "Paint highlight=" not in paint
assert "Paint underline=" not in paint
assert "if(us<6L*US_PER_S)which=-2;" in composer
assert "NewsCoverExporter.save(context,coverMeta," in composer
assert 'Bitmap.CompressFormat.JPEG,95,stream' in cover
for service in [worker,manual]:
    assert "NewsCoverExporter.coverFile(" in service
assert "MUSIC_REVISION=2" in opening
assert "shareLast(true)" in ui and "shareLast(false)" in ui
assert "def releaseVersion = '5.22'" in gradle
assert "applicationId 'com.newsvoice.tts.autovideo.v522'" in gradle
assert "versionCode 28" in gradle
assert "NewsVoiceWAV-v5.22-debug.apk" in actions
assert "NewsVoiceWAV-v5.22-VoiceFirst300ms-debug-apk" in actions
print("PASS: v5.22 font 64px, 92px leading, voice-first 300ms lag and retained existing features")
