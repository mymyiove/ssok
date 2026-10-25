# 쏙 SSOK — Stop · Steer · See · OK

**세워 둔 내 차 안에서, 내 차 핸들로 주차 연습.** 후방카메라·어라운드뷰 화면을 재현해 "그 화면 읽는 법"까지 익힙니다.
Pleos Connect(Android Automotive OS) 차량용 앱 · 2026 현대자동차그룹 차량용 앱 개발 공모전 출품 준비.

- 소개·미리보기·설명서: https://ssok.hojun.app (배포 전이면 `site/index.html`)
- 브라우저 시안(앱과 같은 수식): `site/preview/index.html`

## 왜

차에는 후방카메라와 어라운드뷰가 있지만 사람들은 그 화면을 못 읽습니다(후방카메라만 있어도 정지 더미와 56% 충돌 · 후진 시간의 8~12%만 화면 주시 · 4카메라는 15~18회 연습 뒤에야 안정). 그리고 세워 둔 차에서는 그 화면이 꺼져 연습할 곳이 없습니다. 주차 사고는 물적 사고의 30.2%, 그중 53.8%가 후진입니다.

## 어떻게 (기어 P 전용)

| 입력 | Pleos Vehicle SDK 2.3.2 | 역할 |
|---|---|---|
| 핸들 ±540° | `SteeringWheel.registerSteeringWheelAngle` · `CAR_DRIVE` | 가상 차 조향 (바퀴 최대 35°) |
| 깜빡이 왼쪽 / 오른쪽 / OFF | `TurnSignal.registerTurnSignalState` · `CAR_LIGHTS` | 전진 / 후진 / 정지 (속도 0.6 m/s 고정) |
| 브레이크 | `Brake.registerBrakePedalStatus` · `CAR_DRIVE` | 정지 |
| 기어 | AAOS `GEAR_SELECTION` (`CAR_POWERTRAIN`) + Pleos `Gear`(deprecated) | P가 아니면 멈춤 |

제어(`CONTROL_CAR_*`)·카메라·위치는 쓰지 않습니다. 서드파티 앱은 카메라 영상을 받을 수 없고 후방 모니터는 R에서만, 어라운드뷰는 P에서 꺼지므로 화면을 **재현**합니다.

## 구조

```
app/src/main/kotlin/app/parkingcoach/
  vehicle/   VehicleInputSource · PleosVehicleSource(SDK) · AaosGearWatcher · SimulatedVehicleSource(PC용 패널)
  sim/       CarSpec(아반떼·그랜저 치수) · CarModel(저속 자전거 모델, 540°↔35°) · Scenario(후면 직각·기둥·평행) · Geometry(OBB)
  game/      GameEngine(규칙·코칭·기록) · Coach(TTS)
  render/    TopView(어라운드뷰 탑뷰) · RearCamView(후방카메라 핀홀 투영, 근평면 클리핑) · ReplayView(핸들 기록)
  MainActivity  시작 화면(차종·코스) → 연습 → 완료(리플레이)
app/src/main/java/app/parkingcoach/vehicle/VehicleAccess.java   Vehicle 도메인 getter 우회(Kotlin 메타데이터에서 private)
docs/      geometry-preview.py / .png (같은 수식으로 그린 시작 자세 검증), sian.html
site/      ssok.hojun.app 정적 사이트 (worker.js + wrangler.toml, GitHub Actions 배포)
```

가이드라인 색·거리(노란 조향 연동 · 파란 중립 · 빨간 경고 · 0.5/1/2.3 m)는 현대 취급설명서, 조향 기하는 그랜저로 검증된 KAIS 2015 논문 방식(핸들 ±540° ↔ 바퀴 35°, R = 휠베이스 / tan δ).

## 빌드·시연

```
export ANDROID_HOME=~/android-sdk        # platforms;android-34 · build-tools;34.0.0 (android.car 포함)
gradle :app:testDebugUnitTest :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
adb forward tcp:8088 tcp:8088            # Pleos Virtual Console → http://127.0.0.1:8088
```

Pleos 차량 서비스가 없는 기기에서는 화면 패널(슬라이더·버튼)로 같은 신호를 넣는 시뮬레이터 모드로 뜹니다.
Pleos 에뮬레이터 2.1.0(API 34 Automotive 이미지)의 Virtual Console 카드: Steering Angle · Turn Signal State · Brake Pedal · Gear Selection.

### 1주차 실측 항목 (에뮬레이터)
- 조향각 콜백의 단위·부호·범위·주기 → `CarModel(steeringSign)` 보정
- AAOS `GEAR_SELECTION`·Pleos `Gear` 중 에뮬레이터에서 실제로 오는 쪽
- 화면 해상도(Connect-S 12.9"/14.6"/17")와 portrait/landscape

## 안전 조건
정지 상태에서 핸들을 가득 돌려 오래 잡고 있으면 EPS가 과부하 보호로 들어가고 타이어·조향계에 부담이 됩니다. 앱은 5분 세션 제한, 가득 유지 요구 없음, 가상 차가 움직일 때만 돌리도록 안내, 기어 P 아니면 정지. Pleos 정책상 정차 전용 미디어 앱(DO 미선언)으로 등록합니다.

## 사이트 배포 (ssok.hojun.app)
`site/`가 바뀌어 main에 푸시되면 GitHub Actions가 `wrangler deploy`로 Cloudflare Worker(정적 자산)에 올립니다.
필요한 Secrets: `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID` (물때 저장소와 같은 값). 도메인 `ssok.hojun.app`은 `wrangler.toml`의 `custom_domain = true`로 자동 생성됩니다. 심사 전까지 `X-Robots-Tag: noindex`.
