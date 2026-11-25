# NewsVoice Radio Lite v4.0

Independent Android application ID: `com.newsvoice.radio.v4`. Android 10 and later. Older versions are not modified.

Import a ZIP with one UTF-8 SRT and one MP3 or select both separately. Save/share the paired ZIP. Playback uses Android MediaPlayer; caption positions follow the playback clock with a 700 ms delay. Caption rows scroll to the upper third of the reading area. Black background, white text, adjustable font size and playback speed. Foreground playback service is designed to continue when the screen is off. No internet permission, image/video feature, or permanent WAV files.

## Production
Android plays supplied MP3/SRT; it does not synthesize speech. Generate the approved recording externally (for example, This Voice Voice), divided into caption-sized segments. Provide the approved opening audio and segment recordings in a JSON file: `{"opening":"opening.wav","segments":[{"audio":"news01.wav","text":"첫 번째 뉴스입니다."}]}`. The Windows producer combines a six-second opener and the recordings, calculates timestamps from PCM sample counts, encodes mono MP3 at 48 kbps, and saves MP3/SRT in one ZIP. TTS voice quality and speech creation remain under the external voice app's control.

Run `pwsh -File Make-Broadcast.ps1 -Manifest manifest.json -OutputZip broadcast.zip -Ffmpeg <absolute-ffmpeg-path>`.

Build using JDK 17, Android SDK 35 and Gradle 8.9: `gradle :app:assembleDebug`. Install the debug APK from `app/build/outputs/apk/debug/`. This source requires Android device verification before release approval.
