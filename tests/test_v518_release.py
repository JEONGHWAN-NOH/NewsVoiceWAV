from pathlib import Path

root=Path("app/src/main/java/com/newsvoice/tts")
manifest=Path("app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
composer=(root/"NewsVideoComposer.java").read_text(encoding="utf-8")
painter=(root/"NewsCardPainter.java").read_text(encoding="utf-8")
opening=(root/"OpeningMusicA.java").read_text(encoding="utf-8")
ui=(root/"MainActivity.java").read_text(encoding="utf-8")
workflow=Path(".github/workflows/build-apk.yml").read_text(encoding="utf-8")

assert 'android:label="@string/app_display_name"' in manifest
assert '뉴스보이스 WAV 5.14' not in manifest
assert "def releaseVersion = '5.29'" in gradle
assert "applicationId 'com.newsvoice.tts.stable'" in gradle
assert 'versionName releaseVersion' in gradle
assert "resValue 'string', 'app_display_name'" in gradle
assert 'outputFileName = "NewsVoiceWAV-v${releaseVersion}-${variant.buildType.name}.apk"' in gradle
assert 'getString(R.string.app_display_name)' in ui
assert 'FPS=30;' in composer
assert 'SCROLL_DELAY_US=700_000L;' in composer
assert 'Math.round(scrollPlan.offset' not in composer
assert 'float zoom=1.0f;' in composer
assert 'Paint highlight=' not in painter
assert 'Paint underline=' not in painter
assert 'readAlready=' not in painter
assert 'Paint body=p(Color.WHITE,SCROLL_FONT,true)' in painter
assert 'MUSIC_REVISION=2' in opening
assert 'final double[] lead=' in opening
assert 'double kick=' in opening
assert 'double snare=' in opening
assert 'double hat=' in opening
assert 'boolean builtin=AppPrefs.get(context).getBoolean("opening_a_builtin",true)' in opening
assert 'appendOpeningMusic(out,introWav' in composer
assert 'NewsVoiceWAV-v5.29-release-unsigned' in workflow
print("PASS: v5.29 keeps upbeat auto opener and consistent app version 5.29")
