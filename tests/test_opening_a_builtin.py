from pathlib import Path
a=Path("app/src/main/java/com/newsvoice/tts/OpeningMusicA.java").read_text(encoding="utf-8")
v=Path("app/src/main/java/com/newsvoice/tts/NewsVideoComposer.java").read_text(encoding="utf-8")
u=Path("app/src/main/java/com/newsvoice/tts/MainActivity.java").read_text(encoding="utf-8")
assert "DURATION=6" in a
assert "header(out);" in a
assert "valid(dest)" in a
assert 'new File(context.getFilesDir(),"newsvoice_opening_A.wav")' in a
assert "OpeningMusicA.ensureInstalled(context)" in v
assert "appendOpeningMusic(out,introWav" in v
assert "OpeningMusicA.ensureInstalled(this)" in u
assert 'putBoolean("opening_a_builtin",false)' in u
assert "else writeSilence(out,introFrames" not in v
print("PASS: original A-type fanfare is auto-created; imported A WAV takes priority")
