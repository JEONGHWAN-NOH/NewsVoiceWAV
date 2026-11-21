# 뉴스보이스 WAV 자동 생성 앱 v5.0-beta

이 프로젝트는 사용자가 승인한 **뉴스보이스 WAV 음성으로 필리핀 뉴스 MP4를 만드는 작업의 첫 단계**입니다. 기존 안드로이드 소스 프로젝트를 바탕으로 TTS 자동화, 긴 대본 분할, WAV 결합, 선택적 HTTPS 서버 연동을 추가했습니다.

## 구현 기능

1. 갤럭시의 설치된 한국어 TTS 엔진으로 뉴스 대본을 미리 읽을 수 있습니다. 여성 음성 여부는 운영체제에서 제공되는 Voice metadata만으로 단정할 수 없으므로 **직접 미리 듣고 확인**해야 합니다.
2. 긴 뉴스 대본(최대 60,000자)을 1,800자 이내 구간으로 분할한 뒤 `TextToSpeech.synthesizeToFile()`로 WAV를 생성합니다. 각 WAV의 형식/샘플레이트가 일치하면 하나로 병합합니다. 일부 엔진은 WAV를 출력하지 않을 수 있으므로 형식을 검사하고 오류를 표시합니다.
3. 음성 파일은 갤럭시 **내 파일 > 다운로드 > NewsVoiceWAV** 폴더로 저장합니다 (Android 10 이상).
4. 서버 정보와 인증키를 설정하면 WorkManager가 약 15분 간격으로 HTTPS 서버의 새 대본을 확인해 WAV를 저장하고 서버로 업로드합니다. 정확한 정각 실행을 보장하지 않습니다.
5. 서버와 연결하지 않아도 원고를 앱에 입력해 **긴 원고 WAV 자동 생성** 버튼으로 생성할 수 있습니다.
6. 서버 샘플은 새 원고 대기열, 임대(lease), WAV 업로드, 완료 처리를 제공합니다. 단, 뉴스 자동 취재·팩트체크·대본 완성·영상 렌더링 및 기존 ChatGPT 예약 작업과 서버의 자동 연결은 이번 단계에서 구현하지 않았습니다.

## 중요한 제한

- **이 ZIP은 APK가 아닌 Android Studio용 소스 프로젝트**입니다. 현재 작업 환경에는 Android SDK/Gradle이 없어 실제 갤럭시 설치용 APK를 만들거나 기기에서 실행 검증하지 못했습니다.
- 휴대폰 전원이 꺼지거나 배터리 정책으로 백그라운드 작업이 제한되면 자동 생성이 지연됩니다.
- 서버가 없는 상태에서는 수동 WAV 생성 기능부터 사용해야 합니다.
- `server` 샘플은 개발용입니다. 기본 HTTP 서버를 인터넷에 직접 노출하지 마십시오. 운영 시에는 TLS 인증서가 적용된 HTTPS 역방향 프록시, 접근제어 및 별도의 비밀키 관리가 필요합니다.
- Android TTS 서비스에 한국어 음성이 설치되어 있어야 하며, 사용자가 선택한 음성 ID를 자동화에서 고정합니다. 선택 음성이 없으면 다른 음성으로 몰래 바꾸지 않고 실패 처리합니다.
- 기존 뉴스 영상의 비율(1080×1920), 카드 디자인, 오프닝, 환율 제외 등 **승인된 편집 기준은 변경하지 않았습니다**. MP4 자동 조립은 후속 단계입니다.

## 빌드: Android Studio

1. 프로젝트 압축을 해제하고 `NewsVoiceWAV_Auto_v5` 폴더를 Android Studio에서 엽니다.
2. Android SDK 35, Android Gradle Plugin 8.7.3, Gradle 8.9, JDK 17이 필요합니다 (Gradle sync 시 설치 가능).
3. `Build > Build Bundle(s) / APK(s) > Build APK(s)` 실행.
4. 출력 위치: `app/build/outputs/apk/debug/app-debug.apk`.
5. 갤럭시에서 기존 앱과 서명키가 충돌하면 기존 앱 데이터를 확인하고 필요 시 제거 후 새 APK를 설치합니다. 동일한 `applicationId`만으로 기존 데이터가 보존되지는 않습니다.
6. 앱 실행 → 한국어 목소리 선택 → '미리 듣기' → 뉴스 대본 붙여넣기 → '긴 원고 WAV 자동 생성 및 다운로드 저장'.
7. 휴대폰 **내 파일 > 다운로드 > NewsVoiceWAV**에서 WAV 파일을 찾아 재생해 확인합니다.

### GitHub Actions로 APK 빌드

1. 별도의 **비공개** GitHub 저장소 `NewsVoiceWAV`를 만들고 이 프로젝트 전체를 업로드합니다.
2. 기본 브랜치를 `main`으로 설정하면 `.github/workflows/build-apk.yml`이 동작합니다. 또는 GitHub `Actions > Build NewsVoice WAV Automatic APK > Run workflow`를 실행합니다.
3. 테스트와 빌드가 성공하면 Actions 실행 결과의 `NewsVoiceWAV-Auto-v5-debug-apk` artifact에서 APK를 다운로드합니다.
4. 현재 연결된 GitHub 저장소 목록에는 이 앱 전용 저장소가 확인되지 않아 **GitHub 원격 빌드는 실행하지 않았습니다**. 연결된 다른 앱의 저장소를 임의로 변경하지 않았습니다.

## 서버 테스트 및 설정

기본 대기열 서버는 Python 3.10+ 표준 라이브러리만 사용합니다.

```bash
export NV_TOKEN='본인이_생성한_충분히_긴_랜덤_인증키'
export NV_QUEUE_DIR='./newsvoice_jobs'
python3 server/newsvoice_server.py
```

개발용으로 `127.0.0.1:8787`에서 HTTP가 열립니다. **갤럭시 앱은 HTTPS만 받도록 제한**하므로 실제 서버에는 HTTPS 역방향 프록시 또는 HTTPS 호스팅을 연결해야 합니다. 서버 주소를 휴대폰의 앱에 설정하고 동일한 인증키를 입력하세요.

큐 등록 예제(HTTPS 인증서 설정 이후):

```bash
NV_TOKEN='랜덤_인증키' python3 server/enqueue_news.py \
    https://your-server.example 20261121_am_01 article1.txt
```

확정된 원고는 기사별로 `am_01`, `am_02`처럼 별도의 작업으로 등록하는 편이 향후 영상 카드-음성 싱크에 유리합니다. 앱은 새 원고를 확인하면 WAV를 다운로드 폴더에 저장하고 서버 `POST /api/jobs/{id}/audio`로 자동 업로드합니다.

API:
- `POST /api/jobs` : 인증된 발행자가 `{"id":"20261121_am_01","text":"뉴스 원고"}` 입력.
- `GET /api/jobs/next` : 다음 대기 작업 JSON을 반환하거나 204.
- `POST /api/jobs/{id}/audio` : `Content-Type: audio/wav`로 바이너리 WAV 업로드 및 완료 등록.

## 테스트 (SDK 없이 실행 가능)

```bash
mkdir -p /tmp/nvclasses
javac -encoding UTF-8 -d /tmp/nvclasses \
  app/src/main/java/com/newsvoice/tts/WavTools.java tests/WavToolsTest.java
java -cp /tmp/nvclasses WavToolsTest
python3 -m unittest discover -s tests -p 'test_*.py' -v
```

WAV 병합·형식 불일치 탐지·한국어 원고 분할 테스트와 서버의 인증·큐·업로드 흐름 테스트가 포함되어 있습니다. 실제 안드로이드 TTS 동작 및 APK 설치 테스트는 별도로 필요합니다.

## 앱 오류 확인 방법

설치 직후 종료되면 Android Studio의 Logcat에서 `FATAL EXCEPTION`을 확인해 로그를 남겨야 원인을 확정할 수 있습니다. 관련 정보: 휴대폰 모델, 안드로이드 버전, 선택한 TTS 엔진, 앱 실행/저장 시각, `FATAL EXCEPTION` 구간.

## 운영과 보안

- 인증키를 앱 코드·GitHub에 넣지 않습니다. 앱의 로컬 설정에서 입력합니다.
- 해당 예제는 인증키를 Android 전용 앱 설정 저장소에 보관합니다. 완성형 배포 시 Android Keystore 기반 비밀정보 암호화를 추가하는 것을 권장합니다.
- 서버가 대본을 보관하므로 공개하면 안 되는 대본/음성을 보내지 마십시오.
- 무료 비공개 GitHub 저장소의 CI는 계정의 Actions 사용량 정책이 적용될 수 있습니다.
