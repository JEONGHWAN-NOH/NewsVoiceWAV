from pathlib import Path

root=Path("app/src/main/java/com/newsvoice/tts")
card=(root/"NewsCardPainter.java").read_text(encoding="utf-8")
video=(root/"NewsVideoComposer.java").read_text(encoding="utf-8")
ui=(root/"MainActivity.java").read_text(encoding="utf-8")
math=(root/"NewsScrollMath.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
workflow=Path(".github/workflows/build-apk.yml").read_text(encoding="utf-8")
opening=(root/"OpeningMusicA.java").read_text(encoding="utf-8")
poster=(root/"NewsCoverExporter.java").read_text(encoding="utf-8")

assert "SCROLL_FONT=60f;" in card
assert "SCROLL_STEP=88f;" in card
assert "SCROLL_DELAY_US=700_000L;" in video
assert "scrollCurve.delayedPosition(us,SCROLL_DELAY_US)" in video
assert "public float delayedPosition(long playbackUs,long delayUs)" in math
assert "return position(queryUs);" in math
assert "current.audioStartUs,current.sentenceEndUs" in video
# A longer voice-first delay must not cause the previous article to be cut off.
assert "long gapFrames=rate*3L/4;" in video, "750ms pre-roll needed for 700ms scroll completion"
assert "long tailFrames=rate*4L/5;" in video, "final 800ms tail needed for 700ms scroll"
assert "i==0?0L:SCROLL_DELAY_US" in video, "defer next card transition 700ms"
assert "60px·줄 간격 88px" in ui
assert "0.7초(700ms)" in ui
assert "Paint highlight=" not in card
assert "Paint underline=" not in card
assert "FPS=30;" in video
assert "if(us<6L*US_PER_S)which=-2;" in video
assert "MUSIC_REVISION=2" in opening
assert "NewsCoverExporter.save(context,coverMeta," in video
assert "Bitmap.CompressFormat.JPEG,95,stream" in poster
assert "shareLast(true)" in ui and "shareLast(false)" in ui
assert "def releaseVersion = '5.23'" in gradle
assert "applicationId 'com.newsvoice.tts.autovideo.v523'" in gradle
assert "versionCode 29" in gradle
assert "NewsVoiceWAV-v5.23-VoiceFirst700ms-debug-apk" in workflow
print("PASS: v5.23 60px text, 88px line spacing and 700ms voice lead with safe scene transitions")
