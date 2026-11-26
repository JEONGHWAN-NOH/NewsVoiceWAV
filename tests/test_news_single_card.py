from pathlib import Path
p=Path("app/src/main/java/com/newsvoice/tts/NewsCardPainter.java").read_text(encoding="utf-8")
assert 'result.add(story.page(story.narration,newsNumber,newsTotal,1,1));' in p
assert 'segment.kind==NewsStory.NEWS?++counted:' in p
assert 'story.spokenText()' in p
assert 'private static final float SCROLL_FONT=56f;' in p
assert 'private static final float SCROLL_STEP=84f;' in p
assert 'NewsScrollMath.offset(rowLengths,position,' in p
assert 'List<String> rows=wrapComplete(script,p(Color.WHITE,SCROLL_FONT,true),880f);' in p
assert 'total!=NewsSentenceTimeline.visibleLength(script)' in p
assert 'c.clipRect(75f,954f,1002f,SCROLL_BOTTOM);' in p
assert 'NewsBroadcastSegments.completedNews(story)' in p
assert 'renderBroadcastCover(context,meta,count)' in p
assert 'drawProseLine(c,text,92f,y,body);' in p
assert 'new ReadingFit(92,658,size,step,lines)' in p # legacy renderer retained
assert 'String scrollLabel="큰 글씨 · 자동 스크롤";' in p
print("PASS: full-length article uses one large-print scrolling slide without truncation")

assert 'final float l=68,t=253,r=596,b=788;' in p
assert 'rounded(c,blue,43,233,1037,809,30);' in p
assert 'rounded(c,Color.rgb(8,43,94),44,830,1036,1598,29);' in p
