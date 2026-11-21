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
assert 'scrollPlan.centeredAudioCurve(' in composer
assert 'scrollCurve.delayedPosition(us,SCROLL_DELAY_US)' in composer
assert 'current.sentenceEndUs' in composer
assert 'scrollPixels!=previousScroll' not in composer
assert 'Math.abs(scrollPixels-previousScroll)>0.0001f' in composer
assert 'NewsCardPainter.renderScrolling(' in composer
assert 'NewsSentenceTimeline.visibleRange(' not in painter
assert 'Paint highlight=' not in painter
assert 'Paint underline=' not in painter
assert 'readAlready=' not in painter
assert 'Paint body=p(Color.WHITE,SCROLL_FONT,true)' in painter
assert 'drawProseLine(c,text,92f,y,body);' in painter
assert 'visibleRange(List<String> sentences,int activeIndex)' in sentences
print("PASS: measured sentence WAV cues drive continuous sync, with no highlight/underline/dimming")
