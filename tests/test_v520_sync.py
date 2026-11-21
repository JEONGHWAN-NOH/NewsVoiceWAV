from pathlib import Path

root=Path("app/src/main/java/com/newsvoice/tts")
painter=(root/"NewsCardPainter.java").read_text(encoding="utf-8")
math=(root/"NewsScrollMath.java").read_text(encoding="utf-8")
video=(root/"NewsVideoComposer.java").read_text(encoding="utf-8")
ui=(root/"MainActivity.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
opening=(root/"OpeningMusicA.java").read_text(encoding="utf-8")
manual=(root/"ManualRenderService.java").read_text(encoding="utf-8")
worker=(root/"NewsVoiceWorker.java").read_text(encoding="utf-8")
poster=(root/"NewsCoverExporter.java").read_text(encoding="utf-8")
pipeline=Path(".github/workflows/build-apk.yml").read_text(encoding="utf-8")

assert 'private static final float SCROLL_FONT=56f;' in painter
assert 'private static final float SCROLL_STEP=84f;' in painter
assert 'List<String> rows=wrapComplete(script,p(Color.WHITE,SCROLL_FONT,true),880f);' in painter
assert 'if(total!=NewsSentenceTimeline.visibleLength(script))' in painter
assert 'sentenceStarts=sentenceStarts' in painter
assert 'NewsScrollMath.AudioCurve scrollCurve=null;' in video
assert 'scrollPlan.centeredAudioCurve(' in video
assert 'current.audioStartUs,current.sentenceEndUs):null;' in video
assert 'NewsScrollMath.centeredAudioCurve(voiceStartUs,' in painter
assert 'scrollCurve.delayedPosition(us,SCROLL_DELAY_US)' in video
assert 'private final long[] timesUs;' in math
assert 'private final double[] pixels,velocity;' in math
assert 'double w1=2*rightDuration+leftDuration;' in math
assert 'm[i]=(w1+w2)/(w1/before+w2/after);' in math
assert 'return clamp((float)val,0f,lastPixel);' in math
assert 'int lo=0,hi=end;' in math
assert 'FPS=30;' in video
assert 'if(us<6L*US_PER_S)which=-2;' in video
assert 'Paint highlight=' not in painter
assert 'Paint underline=' not in painter
assert 'readAlready=' not in painter
assert 'app_display_name' in gradle
assert "def releaseVersion = '5.31'" in gradle
assert "applicationId 'com.newsvoice.tts.stable'" in gradle
assert 'versionCode 37' in gradle
assert '56px·줄 간격 84px' in ui
assert 'MUSIC_REVISION=2' in opening
assert 'shareLast(true)' in ui and 'shareLast(false)' in ui
assert 'NewsCoverExporter.save(context,coverMeta,' in video
for source in (manual,worker):
    assert 'NewsCoverExporter.coverFile(' in source
    assert 'names.jpg()' in source  # same dated JPEG filename in both render paths
assert 'Bitmap.CompressFormat.JPEG,95,stream' in poster
assert 'NewsVoiceWAV-v5.31-release-unsigned' in pipeline
print("PASS: 56px/84px and voice-first 700ms delayed scroll with existing cover sharing and music")
