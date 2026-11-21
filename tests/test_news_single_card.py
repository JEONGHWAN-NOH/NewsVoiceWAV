from pathlib import Path
s=Path("app/src/main/java/com/newsvoice/tts/NewsCardPainter.java").read_text(encoding="utf-8")
assert "result.add(story.page(story.narration,newsNumber,newsTotal,1,1));" in s
assert "NewsPageSplitter.split(article.narration" not in s
assert "String icon=block==0" not in s
assert "fitStory(story);" in s
assert "drawArticleArt(c,story,layout.wide)" not in s, "old left art column must be removed"
assert "drawArticleArt(" not in s, "no picture-only column"
assert "new ReadingFit(92,658,size,step,lines)" in s, "full-width readable script"
assert "wrapComplete(body,font,891f)" in s, "text uses full available width"
assert "drawWatermark(c,story);" in s, "subtle watermark but no reserved space"
assert "한 화면의 읽기 좋은 글씨 크기를 초과" in s, "do not truncate oversize scripts"
assert "fitStory(story); // Same text measurement" in s
assert "이 기사의 아나운서" not in s or "한 화면" in s
assert "drawProseLine(c,row,layout.x,y,body);" in s
assert "renderCityCover(meta,count,stage)" in s and "philippines_cover.webp" not in s
assert "NewsBroadcastSegments.completedNews(story)" in s
print("Single-card screen and full script preflight passed")
