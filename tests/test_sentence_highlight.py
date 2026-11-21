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
assert 'meta,activeSentence)' in composer
assert 'NewsSentenceTimeline.visibleRange(' in painter
assert 'story.spokenText()),activeSentence' in painter
assert 'highlight.setAlpha(215)' in painter
assert 'c.drawLine(left,y+layout.size*0.24f,right' in painter
assert 'renderFullArticle(story,index,count,meta,activeSentence)' in painter
assert 'if(!Character.isWhitespace(text.charAt(i)))' in sentences
assert 'visibleRange(List<String> sentences,int activeIndex)' in sentences
assert 'NewsVoiceWAV-v5.16-Sentence-Highlight-debug-apk' in Path(".github/workflows/build-apk.yml").read_text()
print("PASS: sentence-specific measured audio cues update the highlighted full-width card")
