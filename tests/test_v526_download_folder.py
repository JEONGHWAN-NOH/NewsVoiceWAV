from pathlib import Path

root = Path("app/src/main/java/com/newsvoice/tts")
publisher = (root/"DownloadsPublisher.java").read_text(encoding="utf-8")
manual = (root/"ManualRenderService.java").read_text(encoding="utf-8")
worker = (root/"NewsVoiceWorker.java").read_text(encoding="utf-8")
ui = (root/"MainActivity.java").read_text(encoding="utf-8")
gradle = Path("app/build.gradle").read_text(encoding="utf-8")
workflow = Path(".github/workflows/build-apk.yml").read_text(encoding="utf-8")

# Android MediaStore creates the subdirectory when a new file is inserted.
naming=(root/"NewsOutputNaming.java").read_text(encoding="utf-8")
assert 'public static final String DIRECTORY_NAME="1. 필리핀뉴스";' in naming
assert 'NewsOutputNaming.DIRECTORY_NAME' in publisher
assert '"/"+dateFolder' in publisher
assert 'NewsOutputNaming.folderFor(filename' in publisher
assert 'names.wav()' in manual and 'names.mp4()' in manual
assert 'names.jpg()' in worker and 'names.credits()' in worker
assert 'Environment.DIRECTORY_DOWNLOADS+"/"+DIRECTORY_NAME' in publisher
assert 'MediaStore.Downloads.RELATIVE_PATH' in publisher
assert 'MediaStore.Downloads.IS_PENDING' in publisher
assert 'wav|mp4|jpg|txt' in publisher
assert 'text?"text/plain"' in publisher
assert 'image/jpeg' in publisher and 'audio/wav' in publisher
assert 'video/mp4' in publisher
# Manual and remote work both publish through this one path.
assert 'DownloadsPublisher.publish(context,wav,' in manual
assert 'DownloadsPublisher.publish(context,mp4,' in manual
assert 'DownloadsPublisher.publish(context,cover,' in manual
assert 'DownloadsPublisher.publish(context,credits,' in manual
assert 'Uri uri=DownloadsPublisher.publish(context,source,filename);' in worker
for s in (ui, manual, worker):
    assert '다운로드/NewsVoiceWAV' not in s
    assert '다운로드 → NewsVoiceWAV' not in s
    assert '1. 필리핀뉴스' in s
assert 'applicationId \'com.newsvoice.tts.stable\'' in gradle
assert "def releaseVersion = '5.35'" in gradle, 'APK release version must match v5.35'
assert 'versionCode 41' in gradle
assert 'NewsVoiceWAV-v5.35-debug.apk' in workflow
print("PASS: v5.35 all output formats use Download/1. 필리핀뉴스")
