from pathlib import Path

root=Path("app/src/main/java/com/newsvoice/tts")
video=(root/"NewsVideoComposer.java").read_text(encoding="utf-8")
poster=(root/"NewsCoverExporter.java").read_text(encoding="utf-8")
downloads=(root/"DownloadsPublisher.java").read_text(encoding="utf-8")
manual=(root/"ManualRenderService.java").read_text(encoding="utf-8")
worker=(root/"NewsVoiceWorker.java").read_text(encoding="utf-8")
ui=(root/"MainActivity.java").read_text(encoding="utf-8")
manifest=Path("app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
workflow=Path(".github/workflows/build-apk.yml").read_text(encoding="utf-8")

# The first decoded frame is a full-news cover and it remains unchanged for six seconds.
assert 'if(us<6L*US_PER_S)which=-2;' in video
assert 'cardStart=0;cardEnd=6L*US_PER_S;' in video
assert 'fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);' in video
assert 'float zoom=1.0f;' in video
assert 'FPS=30;' in video
# Scroll follows real WAV sentence boundaries with smoothed speed changes.
assert 'scrollPlan.centeredAudioCurve(' in video
assert 'scrollCurve.delayedPosition(us,SCROLL_DELAY_US)' in video
assert 'current.audioStartUs,current.sentenceEndUs):null;' in video
assert 'Math.round(scrollPlan.offset' not in video
# The JPEG is derived from the same cover renderer and SAME metadata as the MP4.
assert 'NewsCoverMeta coverMeta=NewsCoverMeta.now();' in video
assert 'NewsCoverExporter.save(context,coverMeta,' in video
assert 'NewsCardPainter.render(context,null,0,articleCount,0,meta)' in poster
assert 'Bitmap.CompressFormat.JPEG,95,stream' in poster
assert 'video.getName().substring(0,video.getName().length()-4)+"_cover.jpg"' in poster
assert 'image/jpeg' in downloads
assert 'wav|mp4|jpg' in downloads
for source in (manual,worker):
    assert 'NewsCoverExporter.coverFile(' in source
    assert 'names.jpg()' in source  # dated public output keeps the real JPEG poster
    assert 'last_cover_uri' in source
assert 'shareLast(true)' in ui
assert 'shareLast(false)' in ui
assert 'Intent.ACTION_SEND' in ui
assert 'Intent.FLAG_GRANT_READ_URI_PERMISSION' in ui
assert '카카오톡이 결정하므로 100% 고정은 보장되지 않습니다.' in ui
assert "def releaseVersion = '5.31'" in gradle
assert "applicationId 'com.newsvoice.tts.stable'" in gradle
assert 'android:label="@string/app_display_name"' in manifest
assert 'NewsVoiceWAV-v5.31-release-unsigned' in workflow
print("PASS: v5.19 uses a full 6s first-frame cover, exact JPEG poster, native share and uninterrupted scroll")
