# 쏙 SSOK — Stop · Steer · See · OK

**세워 둔 내 차 안에서, 내 차 핸들로 주차 연습.** 후방카메라·어라운드뷰 화면을 재현해 "그 화면 읽는 법"까지 익힙니다.
Pleos Connect(Android Automotive OS) 차량용 앱 · 2026 현대자동차그룹 차량용 앱 개발 공모전 출품 준비.

- 소개·미리보기·설명서: https://ssok.hojun.app (배포 전이면 `site/index.html`)
- 기획서(접수양식 02 순서 8절 + 예상 질문): https://ssok.hojun.app/plan/ (`site/plan/index.html`)
- 브라우저 시안(앱과 같은 수식): `site/preview/index.html`

## 왜

차에는 후방카메라와 어라운드뷰가 있지만 사람들은 그 화면을 못 읽습니다(후방카메라만 있어도 정지 더미와 56% 충돌 · 후진 시간의 8~12%만 화면 주시 · 4카메라는 15~18회 연습 뒤에야 안정). 그리고 세워 둔 차에서는 그 화면이 꺼져 연습할 곳이 없습니다. 주차 사고는 물적 사고의 30.2%, 그중 53.8%가 후진입니다.

## 어떻게 (기어 P 전용)

| 입력 | Pleos Vehicle SDK 2.3.2 | 역할 |
|---|---|---|
| 핸들 ±540° | `SteeringWheel.registerSteeringWheelAngle` · `CAR_DRIVE` | 가상 차 조향 (바퀴 최대 35°) |
| 깜빡이 왼쪽 / 오른쪽 / OFF | `TurnSignal.registerTurnSignalState` · `CAR_LIGHTS` | 방향: D 전진 / R 후진 / 없음 |
| 브레이크 | `Brake.registerBrakePedalStatus` · `CAR_DRIVE` | **밟은 채 시작 → 떼면 크리핑(0.6 m/s)으로 움직이고 밟으면 정지** — 오토 차 주차 그대로, 가속 페달 없음. **가상 기어 D·R 은 브레이크를 밟고 있어야 들어간다(시프트 록, N 은 언제나)**, 밟은 채 핸들·기어 조작은 자유. SDK 는 밟음/뗌만 주므로 실차는 꽉/뗌 — 시안은 깊이 0~1(살살 = 느리게, 60% 부터 정지, `brakeDepth`) |
| 기어 | AAOS `GEAR_SELECTION` (`CAR_POWERTRAIN`) + Pleos `Gear`(deprecated) | P가 아니면 멈춤 |

제어(`CONTROL_CAR_*`)·카메라·위치는 쓰지 않습니다. 서드파티 앱은 카메라 영상을 받을 수 없고 후방 모니터는 R에서만, 어라운드뷰는 P에서 꺼지므로 화면을 **재현**합니다.

## 구조

```
app/src/main/kotlin/app/parkingcoach/
  vehicle/   VehicleInputSource · PleosVehicleSource(SDK) · AaosGearWatcher · SimulatedVehicleSource(PC용 패널)
  sim/       CarSpec(아반떼·그랜저 치수) · CarModel(저속 자전거 모델, 540°↔35°) · Scenario(스테이지 빌더: 유형×난이도×옆 차×좌우, 돌발) · Geometry(OBB)
  game/      GameEngine(규칙·코칭·돌발·기록) · Coach(TTS)
  render/    TopView(어라운드뷰 탑뷰) · RearCamView(후방/전방카메라 핀홀 투영, 근평면 클리핑) · ReplayView(핸들 기록)
  MainActivity  시작 화면(차종·스테이지 또는 자유 연습) → 연습 → 완료(리플레이 · 다음 스테이지)
app/src/main/java/app/parkingcoach/vehicle/VehicleAccess.java   Vehicle 도메인 getter 우회(Kotlin 메타데이터에서 private)
docs/      geometry-preview.py / .png (같은 수식으로 그린 시작 자세 검증), sian.html
site/      ssok.hojun.app 정적 사이트 (worker.js + wrangler.toml, GitHub Actions 배포)
site/preview/sim.js   브라우저·node 공용 시뮬레이션(Kotlin sim/·game/ 과 같은 수식, 스테이지 표의 정본). 같은 seed(상황 번호)면 앱과 같은 상황
site/preview/view.js  카메라(핀홀 + 어안 + 지하주차장 천장·조명 + 입체 차·기둥 띠 + 노이즈)·탑뷰(위에서 본 차 그림) 그리기 — Kotlin render/ 로 옮길 기준
tests/sim.test.mjs    `node tests/sim.test.mjs` — 15 스테이지 × 2 차종을 연수 공식대로 조작해 충돌 없이 들어가는지, 돌발 규칙을 검사
```

## 스테이지 (초급 5 · 중급 5 · 고급 5 + 자유 연습)

| # | 난이도 | 코스 | 조건 |
|---|---|---|---|
| 1~5 | 초급 | 후면 · 후면(양옆 차) · 전면 · 평행 · 사선 45° | 확장형 칸 2.6×5.2, 차로 6.5 m, 평행 틈 차 길이 + 2.4 m |
| 6~10 | 중급 | 후면 · 전면(한 번 빼기) · 후면 + **보행자 돌발** · 평행 · 사선 | 법정 일반형 2.5×5.0, 차로 6.0 m, 평행 틈 + 1.9 m |
| 11~15 | 고급 | 후면 왼쪽 칸 · 후면 기둥(2.3 m 칸) + **아이** · 전면 기둥 · 평행 좁은 틈 + **카트** · 후면 왼쪽 기둥 차로 5.5 m 돌발 2회 | 2019 이전 칸 2.3×5.0, 평행 틈 + 1.6 m, 시작 0.6 m 어긋남, 정렬 4° |

**매번 다른 상황**: 시작 위치(초급 ±0.15 m·±2° → 고급 ±0.4 m·±4°)·옆 차의 칸 안 자리·차 색·지하/지상이 seed(상황 번호)로 바뀐다. [다시]는 같은 상황, [새 상황]은 다른 seed. 앱과 시안이 같은 LCG 를 써서 상황 번호가 같으면 같은 장면.
**쏙 공식 S·S·O·K**(앱 이름 = 주차 순서): **S 신호**(브레이크 밟은 채 비상등, 밟은 채 D) → **S 자리**(칸을 지나쳐 멈추고 밟은 채 R — 카메라가 후방으로) → **O 돌리기**(서서 핸들 끝까지, 살살 떼며 노란선이 칸에 들어오면 풀기) → **K 끝**(나란히, 브레이크, 레버 가운데 N = 실차 P). 코치 단계 칩이 이 글자를 단다. **마무리 규칙**: 칸에 들어와도(`inSlot`) 브레이크를 밟고 N 이어야 PARKED. 카메라·가이드라인은 **들어간 기어**(`snap.gear`)를 따른다 — 레버 ≠ 기어.
**조작 이름**: 깜빡이 레버 = **가상 기어**(왼쪽 D 전진 · 오른쪽 R 후진 · OFF = N). 실차 기어는 P 그대로(상태 바). 웹 시안은 화면의 큰 핸들을 돌리고(드래그), D/N/R 버튼과 BRAKE 버튼(누르고 있으면 밟음, 밟힌 상태에서 톡 치면 뗌), 비상등 버튼(E)을 쓴다.
**주차 흐름(접근 → 자리 잡기 → 넣기)**: 시작 위치는 공식 자리보다 7 m 앞(`APPROACH`), 즉 주차 칸이 앞에 보이는 지점이다. 브레이크 밟은 채 → **비상등** → D 로 칸을 지나쳐 공식 자리(`formulaX`)까지 → 멈추고 R → 공식대로 넣는다. 전면·사선은 D 그대로 들어간다. 비상등을 한 번도 켜지 않고 핸들을 잠그면 패널티 2초(실차의 비상등 신호는 SDK 가 OFF/LEFT/RIGHT 만 주므로 1주차 실측 뒤 정함 — 지금은 웹 버튼 · 앱은 시뮬레이터 토글). 카메라 화면은 가상 기어를 따라간다(R 후방 · D 전방).
**화면(가로 전용)**: Pleos Connect 센터 디스플레이 꼴 — 왼쪽 독(홈·연습·기록·팁·소리·전체 화면) · 위 상태 바(스테이지 · 비상등 ▲▲ · 기어 P R N D · 시계) · 가운데 **[좌 미러][카메라][어라운드뷰][우 미러]**(`view.js` `drawMirror`: 볼록 78°, 바깥 18°, R 이면 15° 하향 = R 연동 미러, 유리는 실제 승용차 거울 비율 **17:10** 하우징 안) · 아래 클러스터(핸들·가상 기어·BRAKE·비상등·깜빡이·숫자). 폰은 가로로 돌려야 하며 세로면 안내 화면(세로 보기 선택 가능). 소리는 Web Audio 합성(깜빡이·비상등 틱톡, 주차 거리 경고, 돌발 경보, 충돌, 완료 차임, 기어 클릭), 독에서 끔.
**차종·옆 차**: 내 차 = 아반떼(4.77×1.86) · 그랜저(5.05×1.88) · **투싼**(4.64×1.87, SUV). 옆 차·앞뒤 차도 이 셋 중 seed 로 고르고 실제 치수로 놓는다(`obs[].model`, 평행 앞뒤 차는 범퍼 선 기준이라 틈은 같다). 그리기는 현대차 꼴 — 세단은 보닛 길고 캐빈 뒤로, SUV 는 캐빈 길고 루프랙, 앞 가로 한 줄 램프(심리스 호라이즌, 아반떼는 두 토막) · 뒤 빨간 띠(`drawCar3D`·`drawCarTop`). 전면 보정 공식의 꺾는 각은 35°(40° 는 그랜저 옆 차에서 8 cm 모자람 — 60 조합 실측).
**초보 팁**(독 💡): 시트·핸들 → 룸미러(뒷유리 전체 가운데) → 사이드미러 평소(차체 1/4~1/5) → 주차 때(아래로 내려 뒷바퀴+주차선) → 후방카메라 선 읽기 → 주차 전 순서.
**기록·리더보드**: 기록 = 주행 시간 + 패널티 초(충돌 5 · 사람/카트 사고 15 · 공식보다 많은 전후진 보정 3 · 비상등 안 켬 2). 속도가 크리핑 고정이라 시간 = 경로·멈춤·보정의 합이지 '빨리 달리기'가 아니다. 무충돌·사고 0·돌발 전원 멈춤 = CLEAN. 스테이지 × 차종별 이 기기 TOP 10(시안 localStorage · 앱 SharedPreferences), 이름 8자. 오프닝(로고 → Stop·Steer·See·OK) → 홈 → 연습 → 결과 흐름.
**시연(튜토리얼)**: [시연 먼저 보기]를 누르면 같은 상황(옆 차·색)에서 차가 공식대로 스스로 주차한다(`sim.js Demo` = `game/DemoDriver.kt`). 핸들 아이콘·깜빡이·브레이크가 같이 움직이고 단계마다 자막, 돌발이 나오면 시연도 멈춘다. 핸들·레버를 건드리면 시연이 끝나고 같은 상황을 직접 한다. 시연 운전자는 15 스테이지 × 2 차종 × seed 3개를 모두 통과하는지 테스트로 확인한다.
**코치**: 초급 켬 · 중급 [힌트]를 누를 때만 3초 · 고급 끔(돌발·충돌 경고는 늘). 공식을 받아 적게 하지 않고 **끝난 뒤 공식과 비교**한다 — 핸들을 끝까지 돌린 자리가 공식 자리보다 몇 m 이르거나 늦었는지, 전후진 보정 횟수, 핸들 방향 뒤집기.
치수는 주차장법 시행규칙(주차구획·차로 너비). 돌발(보행자 1.3 m/s · 아이 2.4 m/s · 카트 0.9 m/s)은 옆에서 나와 경로 한가운데 잠깐 멈춰 선다 — 움직이는 채로 닿으면 사고, 멈추면 통과.
전면 직각은 옆 차가 있으면 6 m 차로에서 한 번에 들어가는 기하가 없어(앞 바깥 모서리가 옆 차를 쓴다) 차로 6.5 m + '지나쳐서 40° 꺾고 2 m 빼기'를 공식으로 둔다. 평행은 두 차종 모두 45°-45° 공식이 한 번에 통하는 최소 틈이 차 길이 + 1.6 m 라 고급이 그 값이다.

가이드라인 색·거리(노란 조향 연동 · 파란 중립 · 빨간 경고 · 0.5/1/2.3 m)는 현대 취급설명서, 조향 기하는 그랜저로 검증된 KAIS 2015 논문 방식(핸들 ±540° ↔ 바퀴 35°, R = 휠베이스 / tan δ).

## 빌드·시연

```
export ANDROID_HOME=~/android-sdk        # platforms;android-34 · build-tools;34.0.0 (android.car 포함)
gradle :app:testDebugUnitTest :app:assembleDebug   # 단위 테스트 13개(스테이지 기하는 sim.js 기준값과 대조)
node tests/sim.test.mjs                  # 브라우저 시뮬레이션 쪽 같은 검사
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
