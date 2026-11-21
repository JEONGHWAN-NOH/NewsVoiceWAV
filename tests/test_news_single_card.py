from pathlib import Path
s=Path("app/src/main/java/com/newsvoice/tts/NewsCardPainter.java").read_text(encoding="utf-8")
assert "NewsPageSplitter.split(body, part->" in s
assert "story.title+" in s and "sections.get(i)" in s
assert "fitStory(page);" in s
assert "drawArticleArt(" not in s
assert "new ReadingFit(92,658,size,step,lines)" in s
assert "wrapComplete(body,font,891f)" in s
assert "drawWatermark(c,story);" in s
assert "drawProseLine(c,row,layout.x,y,body);" in s
assert "renderCityCover(meta,count,stage)" in s
assert "NewsBroadcastSegments.completedNews(story)" in s
print("PASS: auto pagination and full-width script retention")
