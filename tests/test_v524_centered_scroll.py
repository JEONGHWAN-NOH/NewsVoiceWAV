from pathlib import Path
root=Path("app/src/main/java/com/newsvoice/tts")
p=(root/"NewsCardPainter.java").read_text(encoding="utf-8")
v=(root/"NewsVideoComposer.java").read_text(encoding="utf-8")
m=(root/"NewsScrollMath.java").read_text(encoding="utf-8")
ui=(root/"MainActivity.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
workflow=Path(".github/workflows/build-apk.yml").read_text(encoding="utf-8")
music=(root/"OpeningMusicA.java").read_text(encoding="utf-8")
poster=(root/"NewsCoverExporter.java").read_text(encoding="utf-8")

assert "SCROLL_FONT=56f;" in p
assert "SCROLL_STEP=84f;" in p
assert "SCROLL_TOP=1110f;" in p
assert "float maxOffset=Math.max(0f,(rows.size()-1)*SCROLL_STEP);" in p
assert "return NewsScrollMath.centeredAudioCurve(voiceStartUs," in p
assert "sentenceEndsUs,sentenceStarts,rowLengths,SCROLL_STEP,maxOffset);" in p
assert "public static AudioCurve centeredAudioCurve(long voiceStartUs," in m
assert "public static float centeredRowOffset(int[] rows,float spokenCharacters," in m
assert "while(row<rowStarts.length&&rowStarts[row]<endChar)" in m
assert "Math.round(" in m
assert "speeds[i]=(w1+w2)/(w1/leftSpeed+w2/rightSpeed);" in m
assert "scrollCurve=current!=null?scrollPlan.centeredAudioCurve(" in v
assert "current.audioStartUs,current.sentenceEndUs):null;" in v
assert "scrollCurve.delayedPosition(us,SCROLL_DELAY_US)" in v
assert "SCROLL_DELAY_US=700_000L;" in v
assert "long gapFrames=rate*3L/4;" in v
assert "long tailFrames=rate*4L/5;" in v
assert "56px·줄 간격 84px" in ui
assert "0.7초(700ms)" in ui
assert "Paint highlight=" not in p
assert "Paint underline=" not in p
assert "FPS=30;" in v
assert "if(us<4L*US_PER_S)which=-2;" in v
assert "MUSIC_REVISION=2" in music
assert "NewsCoverExporter.save(context,coverMeta," in v
assert "Bitmap.CompressFormat.JPEG,95,stream" in poster
assert "shareLast(true)" in ui and "shareLast(false)" in ui
assert "def releaseVersion = '5.36'" in gradle
assert "applicationId 'com.newsvoice.tts.stable'" in gradle
assert "versionCode 42" in gradle
assert "NewsVoiceWAV-v5.36-debug.apk" in workflow
assert "NewsVoiceWAV-v5.36-release-unsigned" in workflow
print("PASS: v5.36 56px/84px and cached, variable-speed row-centered WAV sync with 700ms voice lead")
