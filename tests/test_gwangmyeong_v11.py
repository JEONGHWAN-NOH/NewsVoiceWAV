from pathlib import Path
import re
root = Path(__file__).resolve().parents[1]
def read(rel):
    return (root / rel).read_text(encoding="utf-8")
gradle = read("app/build.gradle")
naming = read("app/src/main/java/com/newsvoice/tts/NewsOutputNaming.java")
publisher = read("app/src/main/java/com/newsvoice/tts/DownloadsPublisher.java")
card = read("app/src/main/java/com/newsvoice/tts/NewsCardPainter.java")
cover = read("app/src/main/java/com/newsvoice/tts/NewsCoverMeta.java")
image = read("app/src/main/java/com/newsvoice/tts/NewsImageFetcher.java")
manifest = read("app/src/main/AndroidManifest.xml")
assert "applicationId 'com.gwangmyeong.news'" in gradle
assert "def releaseVersion = '1.1'" in gradle
assert "versionCode 1" in gradle
assert "GwangmyeongNews-v" in gradle
assert "광명시 뉴스" in naming and "Asia/Seoul" in naming
assert "광명시뉴스" in naming and "필리핀뉴스" not in naming
assert "광명시뉴스" in publisher and "필리핀뉴스" not in publisher
assert "Asia/Seoul" in cover
assert "GWANGMYEONG CITY NEWS" in card
assert "philippines_cover.webp" not in card
assert "PHILIPPINES" not in card and "필리핀" not in card
assert "Gwangmyeong-si Gyeonggi" in image
assert "com.newsvoice.tts.MainActivity" in manifest
print("Gwangmyeong V1.1 isolation checks: OK")
