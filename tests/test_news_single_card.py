from pathlib import Path
p=Path("app/src/main/java/com/newsvoice/tts/NewsCardPainter.java").read_text(encoding="utf-8")
assert "NewsPageSplitter.split(story.spokenText()" in p
assert "text->tryFitNarration(text)!=null" in p
assert 'story.title+"\\n"+chunks.get(i):chunks.get(i)' in p
assert "story.page(text,newsNumber,newsTotal,i+1,chunks.size())" in p
assert "tryFitNarration(prose(story))" in p
assert '"본문 "+story.pageNumber+" / "+story.pageCount' in p
assert "drawArticleArt(" not in p
assert "new ReadingFit(92,658,size,step,lines)" in p
assert "wrapComplete(body,font,891f)" in p
assert "drawWatermark(c,story);" in p
assert "drawProseLine(c,row,layout.x,y,body);" in p
assert "renderBroadcastCover(context,meta,count)" in p
assert "NewsBroadcastSegments.completedNews(story)" in p
print("PASS: story card fits one page or automatically continues without clipping")
