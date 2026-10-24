# 주차 연습 (Pleos Connect) — 2026 HMG 차량용 앱 개발 공모전 출품 준비

주차장에 세워 둔 내 차 안에서, **내 차 핸들**을 돌려 내 차 치수의 가상 차를 주차한다.
화면은 실제 후방카메라·어라운드뷰 화면을 재현해 "그 화면 읽는 법"까지 익힌다.

## 조작 (기어 P 전용)
| 입력 | 신호 (Pleos Vehicle SDK 2.3.2) | 역할 |
|---|---|---|
| 핸들 ±540° | `SteeringWheel.registerSteeringWheelAngle` (CAR_DRIVE) | 가상 차 조향 |
| 깜빡이 왼쪽 / 오른쪽 | `TurnSignal.registerTurnSignalState` (CAR_LIGHTS) | 전진 / 후진 (속도 고정 0.6 m/s) |
| 브레이크 | `Brake.registerBrakePedalStatus` (CAR_DRIVE) | 정지 |
| 기어 | `Gear.registerGearSelection` (CAR_DRIVE) | P 가 아니면 멈춤 |

Pleos 차량 서비스가 없는 기기에서는 화면 패널(슬라이더·버튼)로 같은 신호를 흘려 넣는 시뮬레이터 모드로 뜬다.

## 구조
- `vehicle/` 입력 소스: `PleosVehicleSource`(SDK) · `SimulatedVehicleSource`
- `sim/` 운동학: `CarModel`(저속 자전거 모델, 조향비 540°↔35°), `CarSpec`(아반떼·그랜저 치수), `Scenario`(후면 직각·기둥·평행), `Geometry`(OBB 충돌)
- `game/GameEngine` 규칙·코칭·기록(리플레이용 trace)
- `render/TopView`(어라운드뷰 탑뷰 재현) · `render/RearCamView`(후방카메라 핀홀 투영 + 노란/파란/빨간 가이드라인, 0.5/1/2.3 m)

## 빌드
```
export ANDROID_HOME=~/android-sdk   # platforms;android-34, build-tools;34.0.0
gradle :app:testDebugUnitTest :app:assembleDebug
```
Pleos 에뮬레이터(2.1.0, API 34 Automotive 이미지)에 설치하고 Virtual Console(`adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088) 의
Steering Angle · Turn Signal State · Brake Pedal · Gear Selection 카드로 시연한다.

## 1주차 실측 항목 (에뮬레이터)
- 조향각 콜백의 단위·부호·범위·주기 → `CarModel(steeringSign)` 보정
- Gear 도메인(문서 미기재, AAR 존재) 이 에뮬레이터에서 이벤트를 주는지
- 화면 해상도(Connect-S 12.9"/14.6"/17") 와 portrait/landscape 레이아웃
