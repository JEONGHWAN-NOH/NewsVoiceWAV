from pathlib import Path
src = Path("app/src/main/java/com/newsvoice/tts")
gradle = Path("app/build.gradle").read_text(encoding="utf-8")
naming = (src/"NewsOutputNaming.java").read_text(encoding="utf-8")
card = (src/"NewsCardPainter.java").read_text(encoding="utf-8")
image = (src/"NewsImageFetcher.java").read_text(encoding="utf-8")
cover = (src/"NewsCoverMeta.java").read_text(encoding="utf-8")
manual = (src/"ManualRenderService.java").read_text(encoding="utf-8")
worker = (src/"NewsVoiceWorker.java").read_text(encoding="utf-8")
publisher = (src/"DownloadsPublisher.java").read_text(encoding="utf-8")
script = (src/"NewsBroadcastSegments.java").read_text(encoding="utf-8")
assert "applicationId 'com.gwangmyeong.news.v540'" in gradle
assert "def releaseVersion = '1.3'" in gradle
assert "GwangmyeongNews-v${releaseVersion}" in gradle
assert 'DIRECTORY_NAME="광명시 뉴스"' in naming
assert "Asia/Seoul" in naming and "Asia/Seoul" in cover
assert "광명시뉴스" in naming and "광명시뉴스" in publisher
assert "광명시 뉴스" in manual and "광명시 뉴스" in worker
assert "광명시 종합 뉴스 브리핑" in script
assert "GWANGMYEONG CITY" in card and "광명시 주요 뉴스" in card
assert 'getAssets().open("gwangmyeong_cover.webp")' in card
assert "Gwangmyeong Station" in image
assert "Philippines" not in image
assert "뉴스보이스 WAV" not in gradle
print("PASS: Gwangmyeong independent edition based on NewsVoiceWAV v5.40")
