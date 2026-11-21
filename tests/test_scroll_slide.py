from pathlib import Path
root=Path("app/src/main/java/com/newsvoice/tts")
p=(root/"NewsCardPainter.java").read_text(encoding="utf-8")
v=(root/"NewsVideoComposer.java").read_text(encoding="utf-8")
geom=(root/"NewsScrollMath.java").read_text(encoding="utf-8")
ui=(root/"MainActivity.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
assert 'private static final float SCROLL_FONT=56f;' in p
assert 'private static final float SCROLL_STEP=84f;' in p
assert 'result.add(story.page(story.narration,newsNumber,newsTotal,1,1));' in p
assert 'if(total!=NewsSentenceTimeline.visibleLength(script))' in p
assert 'if(boundaries[boundaries.length-1]!=total)' in p
assert 'c.clipRect(75f,829f,1002f,SCROLL_BOTTOM);' in p
assert 'float y=SCROLL_TOP+row*SCROLL_STEP-scrollPixels;' in p
assert 'float maxOffset=Math.max(0f,(rows.size()-1)*SCROLL_STEP);' in p
assert 'public float offset(int sentence,float fraction,boolean narrationFinished)' in p
assert 'NewsScrollMath.offset(rowLengths,position,' in p
assert 'SCROLL_TOP=1110f;' in p
assert 'sentenceProgress(long presentationUs,int active)' in v
assert 'scrollPlan.centeredAudioCurve(' in v
assert 'current.audioStartUs,current.sentenceEndUs):null;' in v
assert 'scrollCurve.delayedPosition(us,SCROLL_DELAY_US)' in v
assert 'Math.abs(scrollPixels-previousScroll)>0.0001f' in v
assert 'NewsCardPainter.prepareScroll(current.story)' in v
assert 'NewsCardPainter.renderScrolling(' in v
assert 'return clamp((rowPosition(rows,spokenCharacters)-focusRows)*rowStep,0,maxOffset);' in geom
assert 'v5.30 날짜별 자동 저장·이미지·진행률' in ui
assert 'com.newsvoice.tts.stable' in gradle
assert "def releaseVersion = '5.30'" in gradle
assert 'versionName releaseVersion' in gradle
assert 'FPS=30;' in v
assert 'SCROLL_DELAY_US=700_000L;' in v
print("PASS: scroll slides preserve full narration, maintain large text and move with actual WAV sentence clock")
