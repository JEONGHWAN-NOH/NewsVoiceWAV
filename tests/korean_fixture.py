"""Create test-only Korean narration and sample counts from the 12-story manuscript."""
from pathlib import Path
import subprocess
import tempfile
import wave
import re

assets = Path('app/src/androidTest/assets')
script = (assets / 'v63.txt').read_text().strip()
sentences = re.split(r'(?<=[.!?])\s+', script)
lengths = []
with tempfile.TemporaryDirectory() as directory, wave.open(str(assets / 'spoken.wav'), 'wb') as out:
    out.setparams((1, 2, 22050, 0, 'NONE', 'not compressed'))
    part = Path(directory) / 'part.wav'
    for sentence in sentences:
        subprocess.run(['espeak-ng', '-v', 'ko', '-s', '300', '-w', str(part), '--stdin'],
                       input=sentence, text=True, check=True)
        with wave.open(str(part)) as source:
            assert source.getparams()[:3] == (1, 2, 22050)
            lengths.append(source.getnframes())
            out.writeframes(source.readframes(source.getnframes()))
(assets / 'frames.txt').write_text('\n'.join(map(str, lengths)))
