# NewsVoice Radio Lite v4.1 개발 보고 (2026-11-26)

기존 v4.0의 TXT 파일 열기 및 한글 깨짐 문제를 수정한 독립 시험 설치 버전. 앱 ID `com.newsvoice.radio.v41`, Android 10 이상.

- TXT 직접 열기 및 대본 붙여넣기, UTF-8/BOM, UTF-16 BOM, MS949 자동 판별.
- 휴대전화 한국어 TTS 대본 낭독, 문장별 자막 자동 스크롤, 상단 33% 위치, 속도 조절 및 정지/일시정지.
- MP3/SRT와 ZIP 재생 및 공유 유지. TXT+MP3의 자막 시점은 글자 수 기반 추정이며, 정확한 시간은 별도 SRT 사용.
- 기기 음성은 설치된 음성 엔진에 따름. 외부 승인 음원 MP3 사용 가능. 기기 TTS의 MP3 파일 자동 변환 기능은 미포함.
- 앱 자체 인터넷 권한 없음. 이전 버전 보존.

검증: 실제 한글 UTF-8/BOM, UTF-16, MS949 디코딩, 문장 분할 및 SRT 생성 테스트 성공. APK 빌드·v2 서명 검증 성공. APK 52,707바이트(51.5KiB). 갤럭시 실기기 낭독 시험은 사용자 확인 필요.

APK 다운로드: https://github.com/JEONGHWAN-NOH/NewsVoiceWAV/releases/download/radio-v4.1-45713299410/NewsVoice-Radio-Lite-v4.1.apk

SHA256: ec5e99e6e12421b6a18ea46415719f5da9ddd03330b7e079caffc70175b5a447
