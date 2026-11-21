from pathlib import Path
s=Path("app/src/main/java/com/newsvoice/tts/NewsCardPainter.java").read_text(encoding="utf-8")
assert "result.add(story.page(story.narration,newsNumber,newsTotal,1,1));" in s
assert "NewsPageSplitter.split(article.narration" not in s
assert "String icon=block==0" not in s
assert "fitStory(story);" in s
assert "fitStory(story); // Same text measurement" in s
assert "이 기사의 아나운서" not in s or "한 화면" in s
assert "drawProseLine(c,row,layout.x,y,body);" in s
assert "renderBroadcastCover(context,meta,count)" in s
assert "NewsBroadcastSegments.completedNews(story)" in s
print("Single-card screen and full script preflight passed")
