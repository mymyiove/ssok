# 쏙 SSOK

세워 둔 내 차 안에서, 내 차 핸들로 하는 주차 연습 앱.
2026 현대자동차그룹 차량용 앱 개발 공모전 출품작 · Pleos Connect(Android Automotive OS)

- 소개 페이지: https://ssok-parking.pages.dev
- 브라우저로 해 보기(앱과 같은 수식): https://ssok-parking.pages.dev/preview
- 숏폼: https://youtube.com/shorts/JU3MM_fx0pE

## 무엇을 하나
핸들 각도 · 브레이크 · 비상등 · 기어(P) · 깜빡이를 읽어, 내 차 치수로 그린 가상 차를 주차 칸에 넣어 봅니다(기어 D·R 은 화면 레버로). 후방카메라·어라운드뷰·사이드미러 화면을 읽는 법을 익히는 것이 목적이고, 주차 순서는 이름 그대로 **S Signal · S Stop · O Orbit · K Keep** 입니다. 차를 제어하지 않고 읽기만 합니다.

## 실행
```
adb install -r apk/정호준_쏙_release.apk
adb shell am start -n app.parkingcoach/.MainActivity
```
소스에서 빌드: `gradle :app:assembleRelease` (JDK 17 · Android SDK 34)

## 테스트 환경
| 항목 | 값 |
|---|---|
| 플랫폼 | Pleos Connect (AAOS), minSdk 28 · targetSdk 34, 가로 화면 |
| 확인한 기기 | Pleos Connect 에뮬레이터 2.1.0 (API 34 Automotive), 화면 2560×1440 · 앱 창 1682×1210 px · 160 dpi |
| SDK | `ai.pleos.playground:Vehicle:2.3.2` + AAOS `GEAR_SELECTION`·`HAZARD_LIGHTS_STATE` |
| 권한 | `pleos.car.permission.CAR_DRIVE` · `CAR_LIGHTS`, `android.car.permission.CAR_POWERTRAIN` · `CAR_EXTERIOR_LIGHTS` (사전 승인 권한) |
| 차량 신호 넣기 | `adb forward tcp:8088 tcp:8088` → Virtual Console http://127.0.0.1:8088 |
| 차량 서비스가 없을 때 | 화면 조작 모드(핸들 끌기 · D/N/R 버튼 · 브레이크 슬라이더) |
| 네트워크·계정 | 없음. 기록은 기기 안에만 |

```
gradle :app:testDebugUnitTest   # 단위 · 실행(Robolectric) · 화면 스냅샷 테스트
node tests/sim.test.mjs         # 15 스테이지 × 3 차종 시연 완주
```

## APK
`apk/정호준_쏙_release.apk` (release 서명, versionName 1.0.0, 패키지 `app.parkingcoach`)

## 구조
```
app/      Kotlin 앱 (vehicle 차량 신호 · sim 차 모델·스테이지 · game 규칙·코치 · render 화면)
site/     소개 페이지와 브라우저 시안(preview/sim.js · view.js = 앱과 같은 수식)
tests/    시뮬레이션·글자 수 검사
docs/     기획서 원본·제출물, 개발 노트(docs/dev-notes.md)
```

## 오픈소스 · 글꼴 · 소리
| 구성 요소 | 쓰는 곳 | 라이선스 |
|---|---|---|
| Pleos Connect Vehicle SDK 2.3.2 | 앱 | Pleos Playground 이용 조건 |
| AndroidX core-ktx · appcompat, Kotlin 표준 라이브러리 | 앱 | Apache License 2.0 |
| JUnit 4 · Robolectric · Paparazzi | 테스트만 | EPL 1.0 · MIT · Apache 2.0 |
| Playwright | 시안 검사(개발용) | Apache 2.0 |
| Asta Sans · Pretendard | 웹 시안 글꼴 | SIL Open Font License 1.1 |
| 연주곡 3곡(`app/src/main/assets/music`) | 앱 음악 | 팀이 직접 만든 곡 |

효과음·경고음은 파일 없이 코드로 합성합니다. 앱 화면 글꼴은 차량 시스템 글꼴을 씁니다.

## 안전
기어 P 에서만 동작하고, 주행 제한 신호가 오면 화면과 소리를 바로 멈춥니다. 한 세션 5분, 핸들을 끝까지 돌린 채 버티는 코스는 없습니다.
