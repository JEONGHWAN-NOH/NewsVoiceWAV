# v5.37 Gallery News Images — Implementation and acceptance checklist

Base: feature/v5.36-local-news-images. Preserve the v5.36 branch and its APK.

## User-approved behavior
1. In the script editor, provide a clearly labeled "사진 추가 / 갤러리 선택" action for each news item, using Android's system photo picker (no broad media-storage permission).
2. Persist a local copy of each selected image in app-private storage and associate it with the news item ID, subtitle, and original script text. Support replacement and deletion without affecting other items.
3. Preview each item's selected image and subtitle together. Display a clear placeholder if an image has not been selected; never silently show a previous item's image.
4. At the spoken "다음 뉴스입니다" boundary, switch the image and subtitle together to the *next* item. Do not switch on an unrelated occurrence of that phrase within article text.
5. Use the approved photo/subtitle composition: photo region 3:4 aspect ratio and approximately 3:1 photo-to-subtitle horizontal allocation, fitted within 9:16 video without cropping important content.
6. Render the exact same selected image and subtitle in the exported MP4 as in preview, synchronized to the article timeline. Keep the approved intro, narration, and end segment.
7. Support long scripts, repeated previews, missing images, device rotation, and process restart. Avoid duplicate photo assignments.
8. Keep v5.36 functionality and data intact; use preview537 debug application ID for parallel installation.

## Acceptance tests before offering an APK
- Build succeeds and downloadable artifact exists.
- Import 3 distinct photos, assign 3 distinct subtitles, save and reopen.
- Preview shows all three correctly, switching only at article boundaries.
- Export MP4 and verify the same three images/subtitles and audio synchronization.
- Verify no-image fallback, photo replacement, app restart, long narration, and older v5.36 installation remains unchanged.
- Verify APK signing/installability; GitHub CI green alone does not verify installation on Galaxy.

Status: specification only. Feature implementation and device verification are pending.
