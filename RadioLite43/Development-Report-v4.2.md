# NewsVoice Radio Lite v4.2 개발 보고 (2026-11-26)

독립 앱 ID `com.newsvoice.radio.v42`, Android 10 이상. 기존 v4.1 및 과거 버전 보존.

- TXT 대본 직접 열기, UTF-8/BOM·UTF-16 BOM·MS949 한글 처리 유지.
- 한국어 오프라인 TTS 대본을 문장별 WAV로 임시 합성, 실제 샘플 수 기준 자막 시간 계산. A형 오프닝 6초 기본 적용. 외부 MP3/SRT도 지원(이미 포함된 오프닝과 타이밍 유지).
- 360×640 세로 화면, 검은 배경·흰 연속 본문, 상단 33% 읽기 위치, 0.7초 스크롤 보정. 이미지·제목 제거.
- H.264 12fps 65kbps 목표(크기 초과 시 낮춰 재시도), AAC 48kbps 모노, 5분당 5MB 이하 출력 검사.
- MP4 저장, 미리보기, 카카오톡 공유 버튼, 음성만 공유(MP3 입력 또는 생성된 AAC/M4A) 지원. 앱 자체 인터넷 권한 없음.
- 기기 TTS 음성 품질은 설치 음성에 따르며, This Voice Voice 승인 음원은 외부 파일을 직접 불러와 사용. 외부 MP3와 TXT만 사용할 때 자막 시간은 추정, 정확한 경우 SRT 필요.

검증: Android 35 에뮬레이터에서 APK 설치 및 300초 합성 시험 음원+한국어 자막 MP4 제작 성공. 4,677,260바이트(4.68MB), 360×640, H.264/AAC 트랙 및 자막 프레임 확인. APK 108,649바이트(106KiB), v2 서명 검증 성공. 실제 한국어 TTS 및 카카오톡 전송은 Galaxy 실기기에서 확인 필요.

APK: https://github.com/JEONGHWAN-NOH/NewsVoiceWAV/releases/download/radio-v4.2-45716698414/NewsVoice-Radio-Lite-v4.2.apk

시험 영상: https://github.com/JEONGHWAN-NOH/NewsVoiceWAV/releases/download/radio-v4.2-45716698414/RadioLite42-5min-test.mp4

SHA256 APK: 278a26b1332696106092a226f6565d15425fb11d326b5b07c94b7b84d3dd7779
