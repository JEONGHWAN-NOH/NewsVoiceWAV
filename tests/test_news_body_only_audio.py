from pathlib import Path
worker=Path("app/src/main/java/com/newsvoice/tts/NewsVoiceWorker.java").read_text(encoding="utf-8")
manual=Path("app/src/main/java/com/newsvoice/tts/ManualRenderService.java").read_text(encoding="utf-8")
sections=Path("app/src/main/java/com/newsvoice/tts/NewsBroadcastSegments.java").read_text(encoding="utf-8")
story=Path("app/src/main/java/com/newsvoice/tts/NewsStory.java").read_text(encoding="utf-8")
painter=Path("app/src/main/java/com/newsvoice/tts/NewsCardPainter.java").read_text(encoding="utf-8")
ui=Path("app/src/main/java/com/newsvoice/tts/MainActivity.java").read_text(encoding="utf-8")
assert "String spokenText()" in story
assert "return pos<0?\"\":text.substring(pos+1).trim();" in story
assert "result.append(s.spokenText());" in sections
assert 'pages.get(i).spokenText()' in manual and 'SpeechCueBuilder.make(context' in manual
assert 'pages.get(i).spokenText()' in worker and 'SpeechCueBuilder.make(context' in worker
assert "return story.spokenText();" in painter
assert "광명시 뉴스 첫 줄에는 화면에 표시할 제목을 쓰고" in ui
assert 'synthesize(pages.get(i).narration' not in manual
assert 'synthesize(pages.get(i).narration' not in worker
print("PASS: all preview/WAV/MP4 voice paths exclude news titles")
