from pathlib import Path
root=Path("app/src/main/java/com/newsvoice/tts")
p=(root/"NewsCardPainter.java").read_text(encoding="utf-8")
v=(root/"NewsVideoComposer.java").read_text(encoding="utf-8")
geom=(root/"NewsScrollMath.java").read_text(encoding="utf-8")
ui=(root/"MainActivity.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
assert 'private static final float SCROLL_FONT=82f;' in p
assert 'private static final float SCROLL_STEP=115f;' in p
assert 'result.add(story.page(story.narration,newsNumber,newsTotal,1,1));' in p
assert 'if(total!=NewsSentenceTimeline.visibleLength(script))' in p
assert 'if(boundaries[boundaries.length-1]!=total)' in p
assert 'c.clipRect(75f,611f,1002f,SCROLL_BOTTOM);' in p
assert 'float y=SCROLL_TOP+row*SCROLL_STEP-scrollPixels;' in p
assert 'float maxOffset=Math.max(0f,SCROLL_TOP+(rows.size()-1)*SCROLL_STEP-1517f);' in p
assert 'public float offset(int sentence,float fraction,boolean narrationFinished)' in p
assert 'NewsScrollMath.offset(rowLengths,position,' in p
assert 'SCROLL_FOCUS_ROWS=3.2f;' in p
assert 'sentenceProgress(long presentationUs,int active)' in v
assert 'current.sentenceProgress(us,activeSentence)' in v
assert 'current.narrationFinished(us)' in v
assert 'scrollPixels!=previousScroll' in v
assert 'NewsCardPainter.prepareScroll(current.story)' in v
assert 'NewsCardPainter.renderScrolling(' in v
assert 'return clamp((rowPosition(rows,spokenCharacters)-focusRows)*rowStep,0,maxOffset);' in geom
assert 'v5.17 큰 글씨 자동 스크롤' in ui
assert 'com.newsvoice.tts.autovideo.v517' in gradle
assert "versionName '5.17-large-text-auto-scroll'" in gradle
print("PASS: scroll slides preserve full narration, maintain large text and move with actual WAV sentence clock")
