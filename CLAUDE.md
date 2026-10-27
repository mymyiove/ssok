# 쏙 SSOK — 이 저장소에서 일하는 Claude 에게

Pleos Connect(Android Automotive) 주차 연습 앱. 2026 현대차그룹 차량용 앱 개발 공모전 출품(접수 11.15, 2차 PT 12.3~4). 사용자는 PM(존댓말, 비판적 의견, 짧고 솔직하게). 제품·화면 규칙은 README.md 에 다 있다. 먼저 README 를 읽는다.

## 규칙
- 커밋 메시지 끝: `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>` 줄 + `Claude-Session: <이 세션 주소>` 줄. 모델 식별자는 코드·주석·문서에 넣지 않는다.
- 키스토어·비밀번호·토큰은 저장소에 넣지 않는다(release 서명은 환경변수 SSOK_KEYSTORE·SSOK_KEYSTORE_PASS·SSOK_KEY_ALIAS). `.jks` 는 .gitignore.
- 브라우저 시안(`site/preview/sim.js`·`view.js`)과 앱(`app/.../sim`·`game`·`render`)은 같은 수식. 한쪽을 고치면 다른 쪽도, 그리고 `node tests/sim.test.mjs` + `gradle :app:testDebugUnitTest`.
- 사용자에게 .md 파일을 결과물로 내밀지 않는다. 보고는 대화로, 화면은 캡처로.

## 에뮬레이터 실측 절차 (사용자 PC, Pleos Connect v2.1.0 AVD · API 34 · Cold boot)
1. `adb devices` 로 에뮬레이터 확인 → `gradle :app:assembleDebug` → `adb install -r app/build/outputs/apk/debug/app-debug.apk` → `adb shell am start -n app.parkingcoach/.MainActivity`.
2. `adb forward tcp:8088 tcp:8088` → Virtual Console http://127.0.0.1:8088 (Motion 도메인: Gear · Brake Pedal · Steering Wheel Angle · Turn Signal). 브라우저 자동화(Playwright 등)로 카드를 조작해도 된다.
3. 확인할 것(코드로는 확정 못 한 것들):
   - 앱이 Pleos 모드("실차 신호")로 뜨는지, 오류(⚠)가 상단 줄에 없는지(`PleosVehicleSource` lastError).
   - 조향각 부호: Virtual Console 에서 핸들을 오른쪽(+)으로 돌렸을 때 화면 핸들·노란선이 오른쪽으로 가야 한다. 2.1.0 은 반대로 와서 `PleosVehicleSource.STEER_SIGN = -1`(2026-10-26, README '에뮬레이터 실측').
   - Virtual Console 은 HTTP 로도 조작된다: `PUT /vehicle/controls?speed=0&accelerator=0&brake=<0-100>&steering=<deg>` · `/vehicle/turn-signal?mode=Off|BlinkLeft|BlinkRight` · `/vehicle/lights?feature=hazard&value=true` · `/vehicle/gear?gear=Park|Drive`. 1초마다 `POST /browser/session?client=<아무거나>` 를 보내야 `emulator_inactive` 가 안 난다.
   - 이 PC(4코어·16 GB)는 Studio·Chrome·Gradle 과 에뮬레이터를 같이 돌리면 system_server 가 워치독에 죽는다. 빌드는 에뮬레이터를 끄고, 실측 땐 다른 것을 닫는다.
   - 조향각 범위·단위(도 단위 ±540 인지), 콜백 주기.
   - 깜빡이 LEFT → 가상 기어 D, RIGHT → R, OFF → N. 브레이크 밟음/뗌. 기어가 P 가 아니면 가드 화면.
   - 비상등: AAOS `HAZARD_LIGHTS_STATE` 가 에뮬레이터에서 오는지(`AaosHazardWatcher`). 안 오면 비상등은 화면 버튼으로 두고 보고.
   - 화면 크기: Pleos 앱 창은 2560×1440 전체가 아니라 오른쪽 창일 수 있다. 글자·핸들·브레이크가 너무 작거나 크면 `activity_main.xml` 의 dp 와 `onboard` 크기를 그 창 기준으로 맞추고 캡처로 비교. 기준 시안: https://ssok.hojun.app/preview
   - 소리(Sfx)·TTS(Coach)·진동이 나는지. 프레임이 끊기지 않는지(`adb shell dumpsys gfxinfo app.parkingcoach`).
4. 캡처: `adb exec-out screencap -p > docs/submission/app-run/emu-<이름>.png`, 녹화: `adb shell screenrecord --time-limit 60 /sdcard/r.mp4` → `adb pull`.
5. 고친 것은 커밋·푸시(main). release APK 는 `gradle :app:assembleRelease` → `apk/쏙_release.apk` 로 복사해 함께 커밋.
