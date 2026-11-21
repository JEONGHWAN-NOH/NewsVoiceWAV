from pathlib import Path
a=Path("app/src/main/java/com/newsvoice/tts/OpeningMusicA.java").read_text(encoding="utf-8")
v=Path("app/src/main/java/com/newsvoice/tts/NewsVideoComposer.java").read_text(encoding="utf-8")
u=Path("app/src/main/java/com/newsvoice/tts/MainActivity.java").read_text(encoding="utf-8")
assert "DURATION=6" in a
assert 'MUSIC_REVISION=2' in a
assert 'final double[] lead=' in a
assert 'double kick=' in a
assert 'double snare=' in a
assert 'double hat=' in a
assert 'putInt("opening_a_revision",MUSIC_REVISION)' in a
assert 'boolean builtin=AppPrefs.get(context).getBoolean("opening_a_builtin",true)' in a
assert "header(out);" in a
assert "valid(dest)" in a
assert 'new File(context.getFilesDir(),"newsvoice_opening_A.wav")' in a
assert "OpeningMusicA.ensureInstalled(context)" in v
assert "appendOpeningMusic(out,introWav" in v
assert "OpeningMusicA.ensureInstalled(this)" in u
assert 'putBoolean("opening_a_builtin",false)' in u
assert "else writeSilence(out,introFrames" not in v
print("PASS: upbeat original six-second music is auto-created; imported WAV takes priority")
