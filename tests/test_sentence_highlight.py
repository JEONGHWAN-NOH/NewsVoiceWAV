from pathlib import Path
root=Path("app/src/main/java/com/newsvoice/tts")
manual=(root/"ManualRenderService.java").read_text(encoding="utf-8")
worker=(root/"NewsVoiceWorker.java").read_text(encoding="utf-8")
composer=(root/"NewsVideoComposer.java").read_text(encoding="utf-8")
painter=(root/"NewsCardPainter.java").read_text(encoding="utf-8")
sentences=(root/"NewsSentenceTimeline.java").read_text(encoding="utf-8")
for source in (manual,worker):
    assert 'NewsSentenceTimeline.split(script)' in source
    assert 'sentenceFrames.add(elapsedSentenceFrames)' in source
    assert 'written.dataLength/(channels*2)' in source
    assert 'NewsVideoComposer.create(context,pages,' in source
    assert 'sentenceFrames,' in source
assert 'sentenceFrames.size()!=waves.size()' in composer
assert 'totalFrames!=part.dataLength/frameBytes' in composer
assert 'activeSentence(long presentationUs)' in composer
assert 'presentationUs<audioStartUs' in composer
assert 'activeSentence!=previousSentence' in composer
assert 'sentenceProgress(us,activeSentence)' in composer
assert 'scrollPixels!=previousScroll' in composer
assert 'NewsCardPainter.renderScrolling(' in composer
assert 'NewsSentenceTimeline.visibleRange(' in painter
assert 'story.spokenText()),activeSentence' in painter
assert 'highlight.setAlpha(225)' in painter
assert 'c.drawLine(left,y+SCROLL_FONT*0.23f,right,' in painter
assert 'renderFullArticle(story,index,count,meta,activeSentence,plan,scrollPixels,reusable)' in painter
assert 'visibleRange(List<String> sentences,int activeIndex)' in sentences
assert 'NewsVoiceWAV-v5.17-Large-Text-Auto-Scroll-debug-apk' in Path(".github/workflows/build-apk.yml").read_text()
print("PASS: sentence-specific measured WAV cues control highlight and scrolling")
