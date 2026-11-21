# NewsVoice WAV v5.4 – pastel multi-page synced narration
- Same 1080 x 1920 H.264/AAC output and selected Android Korean TTS engine.
- One news article can span multiple slides with no text omitted: page content is checked for exact character preservation (ignoring whitespace).
- A page receives its own TTS WAV, and the slide starts ~0.3 seconds before that WAV and stays visible until narration ends.
- Text is dark navy on an opaque off-white panel. Soft topical watermark art is very faint; backgrounds are cream / light sky / lilac / pale mint.
- 4 sec title + 2 sec overview as before, intro silent (licensed opening music is not bundled).
- Main news: use === on its own line to separate articles.
- A 120-page safety limit and a 12-minute video limit apply.
- v5.4 installs separately from v5.3 (different applicationId).
- A successful GitHub build does not validate real-device MediaCodec performance or actual TTS speech alignment, which require testing on Galaxy.
