from pathlib import Path

root=Path("app/src/main/java/com/newsvoice/tts")
p=(root/"NewsCardPainter.java").read_text(encoding="utf-8")
math=(root/"NewsScrollMath.java").read_text(encoding="utf-8")
video=(root/"NewsVideoComposer.java").read_text(encoding="utf-8")
ui=(root/"MainActivity.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
music=(root/"OpeningMusicA.java").read_text(encoding="utf-8")
poster=(root/"NewsCoverExporter.java").read_text(encoding="utf-8")
worker=(root/"NewsVoiceWorker.java").read_text(encoding="utf-8")
manual=(root/"ManualRenderService.java").read_text(encoding="utf-8")
workflow=Path(".github/workflows/build-apk.yml").read_text(encoding="utf-8")

assert 'private static final float SCROLL_FONT=56f;' in p
assert 'private static final float SCROLL_STEP=84f;' in p
assert 'NewsSentenceTimeline.visibleLength(script)' in p
assert 'if(boundaries[boundaries.length-1]!=total)' in p
assert 'private static final long SCROLL_DELAY_US=700_000L;' in video
assert 'scrollCurve.delayedPosition(us,SCROLL_DELAY_US)' in video
assert 'public float delayedPosition(long playbackUs,long delayUs)' in math
assert 'return position(queryUs);' in math
assert 'if(delayUs<0)' in math
assert 'i==0?0L:SCROLL_DELAY_US' in video
assert 'current.audioStartUs,current.sentenceEndUs):null;' in video
assert 'FPS=30;' in video
assert 'if(us<6L*US_PER_S)which=-2;' in video
assert '56px·줄 간격 84px' in ui
assert '0.7초(700ms)' in ui
assert 'Paint highlight=' not in p
assert 'Paint underline=' not in p
assert 'MUSIC_REVISION=2' in music
assert 'NewsCoverExporter.save(context,coverMeta,' in video
assert 'Bitmap.CompressFormat.JPEG,95,stream' in poster
assert 'shareLast(true)' in ui and 'shareLast(false)' in ui
assert 'NewsCoverExporter.coverFile(' in worker
assert 'NewsCoverExporter.coverFile(' in manual
assert "def releaseVersion = '5.26'" in gradle
assert "applicationId 'com.newsvoice.tts.autovideo.v525'" in gradle
assert 'versionCode 32' in gradle
assert 'NewsVoiceWAV-v5.26-CenteredVoice700ms-debug-apk' in workflow
print("PASS: 56px body, 84px leading, measured WAV alignment with 700ms scroll lag and complete final card")
