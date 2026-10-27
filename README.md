# 쏙 SSOK — 세워 둔 내 차 안에서, 내 차 핸들로 주차 연습

> 2026 현대자동차그룹 차량용 앱 개발 공모전 출품작 · Pleos Connect(Android Automotive OS) 앱 · 개인 참가(PM)

## 1. 앱 이름 및 한 줄 소개
**쏙 SSOK** — 세워 둔 내 차 안에서 내 차 핸들을 돌려 가상의 내 차를 주차 칸에 넣어 보는 연습 앱. 후방카메라·사이드미러·어라운드뷰 화면을 내 차 치수로 재현해 "그 화면 읽는 법"을 익히고, 주차 순서는 앱 이름 그대로 **S 신호 · S 자리 · O 돌리기 · K 끝**.
차에서 읽는 신호는 핸들 각도 · 깜빡이 레버 · 브레이크 · 기어(P) · 비상등 다섯 가지뿐(Pleos Vehicle SDK 2.3.2 + AAOS 표준 속성, 제어 없음).

- 소개: https://ssok.hojun.app · 브라우저 시안(앱과 같은 수식): https://ssok.hojun.app/preview · 기획서: https://ssok.hojun.app/plan
- 시연 영상: `docs/submission/쏙_시연영상.mp4` (브라우저 시안 화면 녹화 85초: 홈 → 시연 → 직접 주차(미러 힐끗) → 멈추고 P → 결과 리플레이·간격 cm)

## 2. 실행 방법
```
# 바로 설치 (release APK)
adb install -r apk/쏙_release.apk
adb shell am start -n app.parkingcoach/.MainActivity

# 소스에서 빌드
export ANDROID_HOME=~/android-sdk        # platforms;android-34 · build-tools;34.0.0
gradle :app:assembleDebug                 # app/build/outputs/apk/debug/app-debug.apk
gradle :app:assembleRelease               # SSOK_KEYSTORE·SSOK_KEYSTORE_PASS 환경변수가 있으면 그 키로, 없으면 debug 키로 서명
```
앱을 켜면 오프닝 → 홈(차종 · 오늘의 상황 · 15 스테이지 · 졸업 코스 · 자유 연습) → [시작] 또는 [시연 먼저 보기]. 가로 화면 한 장: 왼쪽 독 · 위 상태 바 · [좌 미러][후방/전방 카메라][어라운드뷰][우 미러] · 아래 클러스터(핸들 · 가상 기어 D/N/R · 비상등 · 브레이크).
**조작(오토 차 주차 그대로)**: 브레이크를 밟은 채 시작 → 비상등 → 깜빡이 레버 위(오른쪽) = D 전진 · 아래(왼쪽) = R 후진 · 가운데 = N(컬럼 변속기와 같은 방향, PM 2026-10-27 — 종전 왼쪽 = D 는 뒤집음)(브레이크를 밟고 있어야 D·R 이 들어감) → 브레이크를 떼면 크리핑으로 움직이고 밟으면 정지 → 칸에 들어오면 브레이크 + N 으로 끝. 가속 페달 없음.

## 3. 테스트 환경 및 필요한 설정
| 항목 | 값 |
|---|---|
| 대상 플랫폼 | Pleos Connect (Android Automotive OS), minSdk 28 · targetSdk 34, 가로 화면 |
| 검증한 환경 | Robolectric 실행 테스트(SDK 34: 앱 생성 → 주차 완료 → 기록 저장 → 다음 스테이지, 터치) · Paparazzi 화면 스냅샷(1280×720) · release APK 서명 검증. 대상 기기는 Pleos 에뮬레이터 2.1.0(API 34 Automotive 이미지) |
| SDK | `ai.pleos.playground:Vehicle:2.3.2` (nexus-playground.pleos.ai) — 조향각 `SteeringWheel`, 깜빡이 `TurnSignal`, 브레이크 `Brake`, 기어 AAOS `GEAR_SELECTION`, 비상등 AAOS `HAZARD_LIGHTS_STATE` |
| 권한(매니페스트) | `pleos.car.permission.CAR_DRIVE`, `pleos.car.permission.CAR_LIGHTS`, `android.car.permission.CAR_POWERTRAIN`, `android.car.permission.CAR_EXTERIOR_LIGHTS` — Pleos 에뮬레이터·차량에서는 사전 승인 권한이라 런타임 요청이 없습니다 |
| 차량 신호 넣기(에뮬레이터) | `adb forward tcp:8088 tcp:8088` → Pleos Virtual Console http://127.0.0.1:8088 — Steering Angle · Turn Signal State · Brake Pedal · Gear Selection · Hazard Lights |
| 차량 서비스가 없는 기기 | 자동으로 **화면 조작 모드**: 핸들을 끌어 돌리고(두 번 누르면 가운데), D/N/R 버튼, 브레이크 가로 슬라이더(누른 자리가 강도 · 60% 넘겨 떼면 정지 고정 · 왼쪽 톡 = 풀림), 비상등 버튼. 상단 줄에 `화면 조작` 표시 |
| 네트워크·계정·서버 | 없음. 기록은 기기 안(SharedPreferences). 외부 API·카메라·위치 미사용 |
| 안전 정책 | 기어 P 에서만 동작(P 를 벗어나면 가드 화면), 한 세션 5분, 핸들 가득 유지를 요구하는 코스 없음, 화면에 '연습 화면' 표시 |
| 빌드 도구 | Gradle 8.14 · AGP 8.10.1 · Kotlin 2.0.21 · JDK 17 |

```
gradle :app:testDebugUnitTest            # 단위 18개(스테이지 기하 = sim.js 기준값) + Robolectric 실행 테스트 2개 + 스냅샷 5장
gradle :app:recordPaparazziDebug         # 화면 스냅샷 다시 찍기 → app/src/test/snapshots/images/
node tests/sim.test.mjs                  # 브라우저 시뮬레이션 쪽 같은 검사(15 스테이지 × 3 차종 시연 완주)
```

## 4. APK 파일 위치
`apk/쏙_release.apk` (release 빌드, versionName 1.0.0, 패키지 `app.parkingcoach`). 용량이 넘치면 GitHub Releases 에 같은 파일을 올립니다.

---

## 왜

차에는 후방카메라와 어라운드뷰가 있지만 사람들은 그 화면을 못 읽습니다(후방카메라만 있어도 정지 더미와 56% 충돌 · 후진 시간의 8~12%만 화면 주시 · 4카메라 주차 화면은 15회 연습 뒤에야 안정(UMTRI 2004)). 그리고 세워 둔 차에서는 그 화면이 꺼져 연습할 곳이 없습니다. 주차 사고는 물적 사고의 30.2%, 그중 53.8%가 후진입니다.

## 어떻게 (기어 P 전용)

| 입력 | Pleos Vehicle SDK 2.3.2 | 역할 |
|---|---|---|
| 핸들 ±540° | `SteeringWheel.registerSteeringWheelAngle` · `CAR_DRIVE` | 가상 차 조향 (바퀴 최대 35°) |
| 깜빡이 왼쪽 / 오른쪽 / OFF | `TurnSignal.registerTurnSignalState` · `CAR_LIGHTS` | 방향: D 전진 / R 후진 / 없음 |
| 브레이크 | `Brake.registerBrakePedalStatus` · `CAR_DRIVE` | **밟은 채 시작 → 떼면 크리핑(0.6 m/s)으로 움직이고 밟으면 정지** — 오토 차 주차 그대로, 가속 페달 없음. **가상 기어 D·R 은 브레이크를 밟고 있어야 들어간다(시프트 록, N 은 언제나)**, 밟은 채 핸들·기어 조작은 자유. SDK 는 밟음/뗌만 주므로 실차는 꽉/뗌 — 시안은 **가로 페달 슬라이더**(PM 2026-10-25): 누른 자리가 강도(왼쪽 약하게 · 오른쪽 세게), 60% 정지선 아래는 누르는 동안만, 정지선을 넘겨 밟고 떼면 멈춘 채 고정(오토홀드, 마우스 하나로 밟고 기어 넣기), 톡 치면 풀림. 엔진은 깊이 0~1(`brakeDepth`) |
| 기어 | AAOS `GEAR_SELECTION` (`CAR_POWERTRAIN`) + Pleos `Gear`(deprecated) | P가 아니면 멈춤 |

제어(`CONTROL_CAR_*`)·카메라·위치는 쓰지 않습니다. 서드파티 앱은 카메라 영상을 받을 수 없고 후방 모니터는 R에서만, 어라운드뷰는 P에서 꺼지므로 화면을 **재현**합니다.

## 구조

```
app/src/main/kotlin/app/parkingcoach/
  vehicle/   VehicleInputSource · PleosVehicleSource(SDK) · AaosGearWatcher · AaosHazardWatcher · SimulatedVehicleSource(서비스 없을 때)
  sim/       CarSpec(아반떼·그랜저·투싼 치수) · CarModel(저속 자전거 모델, 540°↔35°) · Scenario(스테이지 빌더: 유형×난이도×옆 차×좌우, 돌발, 환경) · Geometry(OBB)
  game/      GameEngine(규칙·코칭·돌발·기록) · DemoDriver(시연) · Coach(TTS) · Sfx(합성 효과음 · 진동)
  render/    Scene(카메라 수식·장면: 핀홀+어안, 지하 천장·조명, 현대차 꼴 입체 차, 기둥·벽, 사람·카트, 밤·비, 8방향 센서 — view.js 를 그대로 옮김)
             CameraView(후방/전방) · MirrorView(좌우 사이드미러 · 내 차 옆구리 사실 묘사 · BCW) · TopView(어라운드뷰 + PDW 호) · ReplayView(탑뷰 리플레이 + 공식 궤적) · WheelView(플랫 핸들) · ClusterView(슬림 클러스터 패널) · BrakeBarView(가로로 넓은 브레이크 페달)
  MainActivity  시안과 같은 한 장: 독 · 상태 바 · [좌 미러][카메라][어라운드뷰][우 미러] · 클러스터(슬림 패널 / 비상등 · 핸들 + 브레이크 페달 · 컬럼 레버 R/N/D) + 오프닝 · 홈(오늘의 상황 · 15 스테이지 · 졸업 코스 · 자유 연습) · 결과 · 팁 · 거울 맞추기 · P 가드
app/src/main/java/app/parkingcoach/vehicle/VehicleAccess.java   Vehicle 도메인 getter 우회(Kotlin 메타데이터에서 private)
app/src/test/kotlin/app/parkingcoach/shot/ScreenShotTest.kt    **화면 스냅샷(Paparazzi 1.3.5 · layoutlib)**: 에뮬레이터 없이 네 화면·홈·결과를 1280×720 PNG 로 — `gradle :app:recordPaparazziDebug`, 결과 app/src/test/snapshots/images/
docs/      geometry-preview.py / .png (같은 수식으로 그린 시작 자세 검증), sian.html, submission/(접수양식 DOCX · 캡처)
site/      ssok.hojun.app 정적 사이트 (worker.js + wrangler.toml, GitHub Actions 배포)
site/preview/sim.js   브라우저·node 공용 시뮬레이션(Kotlin sim/·game/ 과 같은 수식, 스테이지 표의 정본). 같은 seed(상황 번호)면 앱과 같은 상황
site/preview/view.js  카메라·거울·탑뷰·핸들 그리기 — Kotlin render/Scene.kt·TopView.kt 와 같은 수식(2026-10-26 포팅 완료)
tests/sim.test.mjs    `node tests/sim.test.mjs` — 15 스테이지 × 3 차종을 연수 공식대로 조작해 충돌 없이 들어가는지, 돌발 규칙을 검사
tests/copy-lint.mjs   `node tests/copy-lint.mjs` — **소개 페이지 글자 수 법칙**(PM 2026-10-25 '한 화면당 최대 글자수'): 한 화면 = 섹션 하나, 공백·문장부호 뺀 글자만 센다. 제목 ≤ 16자 · 부제 ≤ 40자 · 카드 하나 ≤ 60자 · 섹션 전체 ≤ 360자(카드 격자 섹션 ≤ 520자). 문구를 고치면 꼭 돌린다
```

**실차 / 시뮬 모드**: Pleos 차량 서비스가 있으면 핸들·깜빡이·브레이크·비상등·기어를 차에서 읽고 화면의 핸들·기어·브레이크·비상등은 표시만(누르면 '실차에서는 …으로' 안내), 없으면 같은 부품을 직접 조작한다(핸들 드래그 · D/N/R · 브레이크 슬라이더 · 비상등). 상단 줄 '실차 신호 / 화면 조작'.

## 스테이지 (초급 5 · 중급 5 · 고급 5 + 자유 연습)

| # | 난이도 | 코스 | 조건 |
|---|---|---|---|
| 1~5 | 초급 | 후면 · 후면(양옆 차) · 전면 · 평행 · 사선 45° | 확장형 칸 2.6×5.2, 차로 6.5 m, 평행 틈 차 길이 + 2.4 m |
| 6~10 | 중급 | 후면 · 전면(한 번 빼기) · 후면 + **보행자 돌발** · 평행 · 사선 | 법정 일반형 2.5×5.0, 차로 6.0 m, 평행 틈 + 1.9 m |
| 11~15 | 고급 | 후면 왼쪽 칸 · 후면 기둥(2.3 m 칸) + **아이** · 전면 기둥 · 평행 좁은 틈 + **카트** · 후면 왼쪽 기둥 차로 5.5 m 돌발 2회 | 2019 이전 칸 2.3×5.0, 평행 틈 + 1.6 m, 시작 0.6 m 어긋남, 정렬 4° |

**매번 다른 상황**: 시작 위치(초급 ±0.15 m·±2° → 고급 ±0.4 m·±4°)·옆 차의 칸 안 자리·차 색·지하/지상이 seed(상황 번호)로 바뀐다. [다시]는 같은 상황, [새 상황]은 다른 seed. 앱과 시안이 같은 LCG 를 써서 상황 번호가 같으면 같은 장면.
**쏙 공식 S·S·O·K**(앱 이름 = 주차 순서): **S 신호**(브레이크 밟은 채 비상등, 밟은 채 D) → **S 자리**(칸을 지나쳐 멈추고 밟은 채 R — 카메라가 후방으로) → **O 돌리기**(서서 핸들 끝까지, 살살 떼며 노란선이 칸에 들어오면 풀기) → **K 끝**(나란히, 브레이크, 레버 가운데 N = 실차 P). 코치 단계 칩이 이 글자를 단다. **마무리 규칙**: 칸에 들어와도(`inSlot`) 브레이크를 밟고 N 이어야 PARKED. 카메라·가이드라인은 **들어간 기어**(`snap.gear`)를 따른다 — 레버 ≠ 기어.
**조작 이름**: 깜빡이 레버 = **가상 기어**(위·오른쪽 깜빡이 = D 전진 · 아래·왼쪽 = R 후진 · OFF = N, 컬럼 변속기 방향). 실차 기어는 P 그대로(상태 바). 웹 시안은 화면의 큰 핸들을 돌리고(드래그), D/N/R 버튼과 BRAKE 버튼(누르고 있으면 밟음, 밟힌 상태에서 톡 치면 뗌), 비상등 버튼(E)을 쓴다.
**주차 흐름(접근 → 자리 잡기 → 넣기)**: 시작 위치는 공식 자리보다 7 m 앞(`APPROACH`), 즉 주차 칸이 앞에 보이는 지점이다. 브레이크 밟은 채 → **비상등** → D 로 칸을 지나쳐 공식 자리(`formulaX`)까지 → 멈추고 R → 공식대로 넣는다. 전면·사선은 D 그대로 들어간다. 비상등을 한 번도 켜지 않고 핸들을 잠그면 패널티 2초. **실차 비상등 = AAOS 표준 속성 `HAZARD_LIGHTS_STATE`**(`vehicle/AaosHazardWatcher.kt`, 권한 `android.car.permission.CAR_EXTERIOR_LIGHTS`) — Pleos SDK TurnSignal 은 OFF/LEFT/RIGHT 뿐이지만 vehicle-data 1.1.11 `PleosPropertyIds` 에 HAZARD_LIGHTS_STATE(193)·SWITCH 가 있어 AAOS 통로로 읽는다(2026-10-25 AAR 확인). 차종이 지원하지 않으면 오류 한 줄 남기고 시뮬 토글로. 카메라 화면은 가상 기어를 따라간다(R 후방 · D 전방).
**화면(가로 전용)**: Pleos Connect 센터 디스플레이 꼴 — 왼쪽 독(홈·연습·기록·팁·소리·전체 화면) · 위 상태 바(스테이지 · [시연][다시][⋯] · 시계) · **위 줄 [후방/전방 카메라(크게)][어라운드뷰]** · **아래 줄 [좌 미러][클러스터][우 미러]**(2026-10-26 플레오스 심플 v4 + '진짜 차처럼'): 클러스터 = **슬림 클러스터 패널**(핸들 앞 9.9인치 꼴 검정 유리 바: BRAKE 게이지 · 깜빡이 ◀ ▶ · 속도 km/h · P R N D · TIME · 핸들 각도 막대, `view.js drawCluster` = `ClusterView`) 아래(PM 2026-10-26 그림: 왼손 핸들 · 오른손 브레이크) **왼쪽 핸들(크게, 패널 아래를 살짝 가림) · 컬럼 레버 · 오른쪽 위 비상등 / 아래 가로로 넓은 브레이크 페달 패드(누른 자리 = 강도, 왼쪽 살살 → 오른쪽 세게, PM 2026-10-25)**, 세 덩어리는 같은 위선·아랫선(패널 아래 10 / 줄 바닥 = 미러 바닥), 미러 0.7 · 클러스터 2. **기어(PM 2026-10-27 그림, `.gblock` = `StalkView`)**: ▲ 버튼 · 세로 블록 D(위)·N·R(아래, 현재 단 흰 글자, 널링 질감) · ▼ 버튼. ▲ = D 쪽 한 단, ▼ = R 쪽 한 단, 블록을 끌어도 한 단씩(디텐트 26px), 글자 탭 = 그 단. 방향은 현대 컬럼식(투싼 2025 매뉴얼: 위로 돌리면 D · 아래로 R)과 같다. 실차 모드는 P 고정(건드리면 "실차에서는 깜빡이 레버로"). **P 버튼 = 주차 끝**(PM 2026-10-27 "유저가 다 됐다고 P 누르면 그때 종료"): 멈춘 상태에서 P 를 누르면 결과 — 칸 안이면 쏙, 아니면 **"아직이에요"** 카드(각 틀어짐·옆 치우침·칸 밖, 기록 없음). 움직이는 중이면 패널 팝업 "멈춘 뒤 P를 누르세요". 저절로 끝나는 건 시연뿐(`autoPark`). **음악**(⏮ ▶ ⏭, PM이 SUNO 로 만든 연주곡 mp3 를 `app/src/main/assets/music/`·`site/preview/music/`(+ index.html `MUSIC` 목록)에 넣는다): 코치 음성·경고음이 날 땐 1.5초 0.2 로 더킹. 라디오 카드(위 곡 이름 · 아래 ⏮ ▶ ⏭), 곡 3개(PM SUNO: easy parking · quiet parking lot · concrete silence). **힐끗 보기**: 미러를 한 번 누르면 1초 1.55배로 커졌다 돌아온다(끌면 각도 조정, 두 번 누르면 원위치). **R 연동 미러**: R 을 넣으면 양쪽 미러가 내려가며 패널에 "R 연동 미러 · 양쪽 내림" 1.5초, ⋯ 에서 양쪽·오른쪽·끔. **P 소리·진동**: 끝낼 때 딸깍 + 짧은 진동. **내려서 보면**: 결과 카드에 왼쪽·오른쪽·앞(뒤) 간격 cm(옆 차·기둥·벽·주차선 중 가까운 것, `gaps()`). 브레이크를 안 밟고 변속하면 **클러스터 패널 가운데 팝업 "브레이크를 밟고 변속하세요"** 2초(카메라 토스트 아님). **미러는 그 자리에서 끌어 각도 조정**(가로 = 바깥·안쪽, 세로 = 위·아래, 두 번 누르면 원위치, PM 2026-10-27). 미러(`drawMirror`: 볼록 78°, 바깥 18°, R 이면 15° 하향)는 유리가 줄 높이를 꽉 채워 위는 패널, 아래는 페달과 한 선이고 라벨은 안쪽 알약. 거울 안 내 차는 상자가 아니라 **실제로 보이는 옆구리**(도어 패널·어깨선·이음선·손잡이·창 띠 B/C 필러·뒷바퀴 아치·타이어·휠·돌아간 램프, `drawOwnSide`) + 유리 틴트·반사. 색은 전부 토큰(라이트 기본, 차의 밤 모드면 다크)이고 클러스터 패널만 늘 검정. 폰은 가로로 돌려야 하며 세로면 안내 화면(세로 보기 선택 가능). 소리는 Web Audio 합성(주차 거리 경고 — 0.3 m 안은 연속음, **후진 경고음 띠띠띠**(R 로 움직이는 동안 0.65초마다), 돌발 경보, 충돌, 완료 차임, 기어 클릭. 깜빡이·비상등 틱톡은 뺐다 — PM 2026-10-25), 독에서 끔. **음성 코치**(Web Speech ko-KR): 코치 문장·돌발·시연 자막을 읽는다(소리와 같이 꺼짐). **폰 진동**: 거리 경고·충돌·돌발.
**2026-10-25 저녁 추가(PM '전체 다')**: ① 탑뷰 **주차 거리 경고 호**(현대 PDW 꼴, 8방향 센서 × 띠 3개, `view.js sensors`) ② 진동 ③ **결과 리플레이 = 탑뷰 애니메이션**(내 궤적 파란 실선 · 공식 궤적 흰 점선 = 같은 상황을 시연 운전자가 간 길, `drawReplayTop`, 2.5배속, [▶ 다시 보기]) ④ 핸들 터치 영역 +26px ⑤ **거울 맞추기 연습**(독 🪞: 오른쪽 미러를 끌어 아래·바깥 각을 맞춘다, 통과 = 차체 1/5 · 뒷바퀴 · 주차선) ⑥ **후측방 경고(BCW)**: 그쪽 옆·뒤 0.5~3.5 m 에 차·사람이 있으면 거울 안쪽 주황 아이콘 점멸 ⑦ **오늘의 상황**(홈 맨 위, seed = 날짜, 스테이지 = 날짜 % 15, 기록은 날짜별) ⑧ **밤·비 환경**(고급만: 지하 60 · 지상 20 · 밤 12 · 비 8 %, `envOf` — JS·Kotlin 같은 식, LCG 첫 값 쏠림 때문에 워밍업 한 번 추가) ⑨ 음성 코치 ⑩ **PWA**(`manifest.webmanifest` · `sw.js` · `icon.svg`, 가로 고정, https 에서만 등록).
**차종·옆 차**: 내 차 = 아반떼(4.77×1.86) · 그랜저(5.05×1.88) · **투싼**(4.64×1.87, SUV). 옆 차·앞뒤 차도 이 셋 중 seed 로 고르고 실제 치수로 놓는다(`obs[].model`, 평행 앞뒤 차는 범퍼 선 기준이라 틈은 같다). 그리기는 현대차 꼴 — 세단은 보닛 길고 캐빈 뒤로, SUV 는 캐빈 길고 루프랙, 앞 가로 한 줄 램프(심리스 호라이즌, 아반떼는 두 토막) · 뒤 빨간 띠(`drawCar3D`·`drawCarTop`). 전면 보정 공식의 꺾는 각은 35°(40° 는 그랜저 옆 차에서 8 cm 모자람 — 60 조합 실측).
**초보 팁**(독 💡): 시트·핸들 → 룸미러(뒷유리 전체 가운데) → 사이드미러 평소(차체 1/4~1/5) → 주차 때(아래로 내려 뒷바퀴+주차선) → 후방카메라 선 읽기 → 주차 전 순서.
**기록·리더보드**: 기록 = 주행 시간 + 패널티 초(충돌 5 · 사람/카트 사고 15 · 공식보다 많은 전후진 보정 3 · 비상등 안 켬 2). 속도가 크리핑 고정이라 시간 = 경로·멈춤·보정의 합이지 '빨리 달리기'가 아니다. 무충돌·사고 0·돌발 전원 멈춤 = CLEAN. 스테이지 × 차종별 이 기기 TOP 10(시안 localStorage · 앱 SharedPreferences), 이름 8자. 오프닝(로고 → 신호·자리·돌리기·끝) → 홈 → 연습 → 결과 흐름.
**시연(튜토리얼)**: [시연 먼저 보기]를 누르면 같은 상황(옆 차·색)에서 차가 공식대로 스스로 주차한다(`sim.js Demo` = `game/DemoDriver.kt`). 핸들 아이콘·깜빡이·브레이크가 같이 움직이고 단계마다 자막, 돌발이 나오면 시연도 멈춘다. 핸들·레버를 건드리면 시연이 끝나고 같은 상황을 직접 한다. 시연 운전자는 15 스테이지 × 2 차종 × seed 3개를 모두 통과하는지 테스트로 확인한다.
**코치**: 초급 켬 · 중급 [힌트]를 누를 때만 3초 · 고급 끔(돌발·충돌 경고는 늘). 공식을 받아 적게 하지 않고 **끝난 뒤 공식과 비교**한다 — 핸들을 끝까지 돌린 자리가 공식 자리보다 몇 m 이르거나 늦었는지, 전후진 보정 횟수, 핸들 방향 뒤집기.
치수는 주차장법 시행규칙(주차구획·차로 너비). 돌발(보행자 1.3 m/s · 아이 2.4 m/s · 카트 0.9 m/s)은 옆에서 나와 경로 한가운데 잠깐 멈춰 선다 — 움직이는 채로 닿으면 사고, 멈추면 통과.
전면 직각은 옆 차가 있으면 6 m 차로에서 한 번에 들어가는 기하가 없어(앞 바깥 모서리가 옆 차를 쓴다) 차로 6.5 m + '지나쳐서 35° 꺾고 2 m 빼기'를 공식으로 둔다(40° 는 그랜저 옆 차에서 8 cm 모자람). 평행은 세 차종 모두 45°-45° 공식이 한 번에 통하는 최소 틈이 차 길이 + 1.6 m 라 고급이 그 값이다.

가이드라인 색·거리(노란 조향 연동 · 파란 중립 · 빨간 경고 · 0.5/1/2.3 m)는 현대 취급설명서, 조향 기하는 그랜저로 검증된 KAIS 2015 논문 방식(핸들 ±540° ↔ 바퀴 35°, R = 휠베이스 / tan δ).

## 1주차 실측 항목 (에뮬레이터)
- 조향각 콜백의 단위·부호·범위·주기 → `CarModel(steeringSign)` 보정
- AAOS `GEAR_SELECTION`·Pleos `Gear` 중 에뮬레이터에서 실제로 오는 쪽, `HAZARD_LIGHTS_STATE` 지원 여부
- 화면 해상도(Connect-S 12.9"/14.6"/17")와 portrait/landscape

## 안전 조건
정지 상태에서 핸들을 가득 돌려 오래 잡고 있으면 EPS가 과부하 보호로 들어가고 타이어·조향계에 부담이 됩니다. 앱은 5분 세션 제한, 가득 유지 요구 없음, 가상 차가 움직일 때만 돌리도록 안내, 기어 P 아니면 정지. Pleos 정책상 정차 전용 미디어 앱(DO 미선언)으로 등록합니다.

## 사이트 배포 (ssok.hojun.app)
`site/`가 바뀌어 main에 푸시되면 GitHub Actions가 `wrangler deploy`로 Cloudflare Worker(정적 자산)에 올립니다.
필요한 Secrets: `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID` (물때 저장소와 같은 값). 도메인 `ssok.hojun.app`은 `wrangler.toml`의 `custom_domain = true`로 자동 생성됩니다. 심사 전까지 `X-Robots-Tag: noindex`.
