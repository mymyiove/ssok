# 쏙 화면 규칙 (2026-10-28 PM "앱 개발 기존 규칙 찾아서 우리 것에도 적용해 규칙화")

화면·글을 고치기 전에 읽는다. 숫자로 잴 수 있는 것은 `node tests/ui-rules.cjs`(Playwright, 시안을 직접 띄워 잼)가 막는다.
시안(`site/preview`)을 먼저 고치고, 확정되면 앱(Kotlin)에 같은 값으로 옮긴다.

## 0. 기준 화면
- 차 화면 = 2560×1440 을 2배로 본 **1280×720(dp 기준)**. 규칙 숫자는 이 크기에서 잰다. 1920×1080 · 2560×1440 도 깨지지 않게.
- 폰(가로 1000×461)은 웹 시연용. 누름·글자 숫자 규칙은 면제, 대신 겹침·잘림·가로 스크롤 금지.

## 1. 공식 기준에서 온 것 (출처는 맨 아래)
| # | 규칙 | 값 | 출처 |
|---|---|---|---|
| C1 | 글자 최소 | 12sp(px). 핵심 정보(코치·경고·기어·속도)는 18 이상 | Pleos typography · intro |
| C2 | 대비 | 글자·아이콘 4.5:1 이상(낮·밤 둘 다) | Design for Driving, 체크리스트 G-SH-334 |
| C3 | 누름 영역 | 64×64 이상(보이는 크기가 작으면 누름 영역을 넓힌다) · 서로 겹치지 않게 | Car app quality UX-1·UX-2 |
| C4 | 반응 | 누르면 0.25초 안에 눈에 보이는 변화(+ 소리나 진동) | Pleos intro · DfD |
| C5 | 꺼 둔 요소 | 눌리지 않거나, 눌리면 왜 안 되는지 그 자리에서 보인다 | QG GB-1 |
| C6 | 주차 전용 | 기어 P 에서만 · UX 제한(CarUxRestrictions)이 오면 화면·소리 즉시 멈춤 | QG DD-2·3, Pleos driver distraction |
| C7 | 소리 | 오디오 포커스를 받고 낸다 · 내비 채널 쓰지 않음 · 다른 소리가 오면 낮추거나 멈춤 | AOSP audio focus · QG DD-1 |
| C8 | 밤 | 모든 화면·창에 어두운 테마, 밤에는 어두운 바탕 + 밝은 글자 | DfD · G-SH-335 |
| C9 | 뒤로 | 시스템 뒤로 버튼이 없어도 막다른 화면이 없다(모든 화면에 나가는 길) | QG AN-1 · AAOS parked |
| C10 | 시스템 바 | 바 크기·위치를 가정하지 않는다(가장자리 조작부는 안쪽 여백) | QG AR-1 |
| C11 | 글 길이 | 한 덩어리 3줄 이하 · 한 줄 짧게 | Pleos intro |

## 2. 우리 규칙 (PM 결정, SkillLens 에서 정한 원칙 중 쏙에 맞는 것)
| # | 규칙 |
|---|---|
| R1 | **사용자 중심**: 화면마다 "지금 무엇을 해야 하나"가 맨 앞(코치 줄 · 주 버튼 하나). |
| R2 | **글자 최소화**: 화면 글은 이름과 핵심 한 마디. 설명은 도움말(?)·팁에만. 제목은 명사 한 마디. |
| R3 | **버튼 이름 5글자 이하**(공백 빼고). 동사로. |
| R4 | **상태는 그림으로도 보인다**: 켬/끔 · 전체 화면 · 선택됨 — 글자만 바뀌는 상태 금지(폰은 글자가 숨는다). |
| R5 | **거절은 누른 자리에서**: 안 되는 조작은 누른 것이 흔들리고, 해야 할 것(대개 브레이크)이 깜빡인다. |
| R6 | **잃는 동작은 확인창**: 진행 중 연습 · 코스 · 저장 안 한 기록을 날리는 동작만 묻는다(잃을 게 없으면 묻지 않는다). 창이 떠 있는 동안 연습은 멈춘다. |
| R7 | **정렬**: 같은 줄의 끝선이 맞는다(로고 · 제목 · 오른쪽 제목 / 기어 · 브레이크 아래 끝). |
| R8 | **세 표시**: 펼치기 ▾/▸ · 다른 화면으로 가는 줄 › · 칩 글자는 가운데. |
| R9 | **등급 말 없이 숫자**: 기록은 시간·실수. '잘함/못함' 같은 평가 말을 만들지 않는다(완료 '쏙!'은 브랜드). |
| R10 | **줄표(—) 금지**: 화면 글에 쓰지 않는다. 쉼표나 줄바꿈으로. |
| R11 | **같은 것은 같은 말 · 같은 그림**: 시안과 앱 문구·아이콘이 같다. 같은 동작은 같은 이름. |
| R12 | **대사(음성)는 끊기지 않는다**: 급한 말(돌발·충돌)만 끊고, 나머지는 끝낸 뒤 가장 최근 것 하나. |
| R13 | **고치면 잰다**: 폰·1280·1920 캡처 전후 비교. 의견은 측정값으로. |
| R14 | **새 부품은 기존 부품 먼저**: 버튼·칩·카드·확인창은 있는 것을 쓴다. 새 모양이 필요하면 PM 확인. |

## 3. 출처
- QG: https://developer.android.com/docs/quality-guidelines/car-app-quality
- DfD: https://developers.google.com/cars/design/design-foundations/visual-principles
- 체크리스트: https://docs.partner.android.com/drivingux/foundations/ux-checklist/general-ux-checklist
- AAOS parked: https://developer.android.com/training/cars/parked/automotive-os
- AOSP audio focus: https://source.android.com/docs/automotive/audio/audio-focus
- NHTSA 2013(운전 = P 가 아닐 때, P 작업은 2초/12초 기준 밖): https://www.govinfo.gov/content/pkg/FR-2013-04-26/html/2013-09883.htm
- Pleos intro: https://document.pleos.ai/en/docs/connect/guide/docs-design/Introduction/intro
- Pleos typography: https://document.pleos.ai/en/docs/connect/guide/docs-design/foundations/typography
- Pleos driver distraction: https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline
- 구글 주차 전용 앱은 글자 24sp 기준이지만, 플랫폼(Pleos)이 12sp 최소를 주므로 Pleos 를 따른다(누름 영역은 Pleos 숫자가 없어 구글 64dp).
