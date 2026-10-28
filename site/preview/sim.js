/* 쏙 SSOK — 시뮬레이션 핵심(브라우저·node 공용). Kotlin sim/·game/ 과 같은 수식.
 * 좌표계: 미터, +x 오른쪽, +y 위(탑뷰), heading 0 = +x. 핸들 + = 시계방향(우회전).
 * 포함: 차종 치수 · 기하(OBB) · 저속 자전거 모델 · 난이도/스테이지 빌더 · 돌발(보행자) · 게임 엔진 */
(function (root, factory) {
  if (typeof module === "object" && module.exports) module.exports = factory();
  else root.SSOK = factory();
})(typeof self !== "undefined" ? self : this, function () {
  const PI = Math.PI, rad = d => d * PI / 180;

  // ---- 차종 ----
  const SPECS = {
    AVANTE:   { key: "AVANTE",   name: "디 올 뉴 아반떼", length: 4.765, width: 1.855, wheelbase: 2.750, rearOverhang: 1.050, maxWheelDeg: 35, maxSteer: 540, shape: "sedan", roofH: 1.42, hoodH: 0.82 },
    GRANDEUR: { key: "GRANDEUR", name: "더 뉴 그랜저",   length: 5.050, width: 1.880, wheelbase: 2.895, rearOverhang: 1.150, maxWheelDeg: 35, maxSteer: 540, shape: "sedan", roofH: 1.46, hoodH: 0.85 },
    TUCSON:   { key: "TUCSON",   name: "디 올 뉴 투싼",   length: 4.640, width: 1.865, wheelbase: 2.755, rearOverhang: 0.955, maxWheelDeg: 35, maxSteer: 540, shape: "suv",   roofH: 1.665, hoodH: 1.02 },
  };
  const MODELS = ["AVANTE", "GRANDEUR", "TUCSON"];   // 옆 차도 현대차 셋 중 하나(seed 로 고른다, 정본은 가운데 = 그랜저)
  const ratio = s => s.maxSteer / s.maxWheelDeg;
  const frontOverhang = s => s.length - s.wheelbase - s.rearOverhang;
  const minR = s => s.wheelbase / Math.tan(rad(s.maxWheelDeg));

  // ---- 기하 ----
  function obb(c, h, hx, hy) { return { c, h, hx, hy }; }
  function axesOf(b) { return [[Math.cos(b.h), Math.sin(b.h)], [-Math.sin(b.h), Math.cos(b.h)]]; }
  function corners(b) { const [ax, ay] = axesOf(b);
    return [[1, 1], [1, -1], [-1, -1], [-1, 1]].map(([sx, sy]) => [b.c[0] + ax[0] * sx * b.hx + ay[0] * sy * b.hy, b.c[1] + ax[1] * sx * b.hx + ay[1] * sy * b.hy]); }
  function intersects(a, b) { const A = corners(a), B = corners(b);
    for (const ax of [...axesOf(a), ...axesOf(b)]) { const pa = A.map(p => p[0] * ax[0] + p[1] * ax[1]), pb = B.map(p => p[0] * ax[0] + p[1] * ax[1]);
      if (Math.max(...pa) < Math.min(...pb) || Math.max(...pb) < Math.min(...pa)) return false; } return true; }
  function inside(a, o) { const ax = axesOf(o); return corners(a).every(p => { const d = [p[0] - o.c[0], p[1] - o.c[1]];
    return Math.abs(d[0] * ax[0][0] + d[1] * ax[0][1]) <= o.hx + 1e-9 && Math.abs(d[0] * ax[1][0] + d[1] * ax[1][1]) <= o.hy + 1e-9; }); }
  /** 점과 OBB 사이 거리(안이면 0) */
  function distPoint(b, p) { const [ax, ay] = axesOf(b), d = [p[0] - b.c[0], p[1] - b.c[1]];
    const dx = Math.max(Math.abs(d[0] * ax[0] + d[1] * ax[1]) - b.hx, 0), dy = Math.max(Math.abs(d[0] * ay[0] + d[1] * ay[1]) - b.hy, 0); return Math.hypot(dx, dy); }
  function mirrorObb(b) { return obb([b.c[0], -b.c[1]], -b.h, b.hx, b.hy); }
  function bboxOf(boxes, margin) { let x0 = 1e9, y0 = 1e9, x1 = -1e9, y1 = -1e9;
    boxes.forEach(b => corners(b).forEach(p => { x0 = Math.min(x0, p[0]); y0 = Math.min(y0, p[1]); x1 = Math.max(x1, p[0]); y1 = Math.max(y1, p[1]); }));
    return { c: [(x0 + x1) / 2, (y0 + y1) / 2], hx: (x1 - x0) / 2 + margin, hy: (y1 - y0) / 2 + margin }; }

  // ---- 운동 모델 ----
  function wheelRad(s, steer) { const d = Math.max(-s.maxWheelDeg, Math.min(s.maxWheelDeg, -steer / ratio(s))); return rad(d); }
  function step(s, p, steer, v, dt) { const d = wheelRad(s, steer), ds = v * dt;
    if (Math.abs(d) < 1e-6) return { x: p.x + ds * Math.cos(p.h), y: p.y + ds * Math.sin(p.h), h: p.h };
    const r = s.wheelbase / Math.tan(d), dth = ds / r, cx = p.x - r * Math.sin(p.h), cy = p.y + r * Math.cos(p.h), th = p.h + dth;
    return { x: cx + r * Math.sin(th), y: cy - r * Math.cos(th), h: th }; }
  function body(s, p) { const ca = (s.wheelbase + frontOverhang(s) - s.rearOverhang) / 2;
    return obb([p.x + ca * Math.cos(p.h), p.y + ca * Math.sin(p.h)], p.h, s.length / 2, s.width / 2); }
  function rearCorners(s, p) { const bx = p.x - s.rearOverhang * Math.cos(p.h), by = p.y - s.rearOverhang * Math.sin(p.h), sx = -Math.sin(p.h) * s.width / 2, sy = Math.cos(p.h) * s.width / 2; return [[bx + sx, by + sy], [bx - sx, by - sy]]; }
  function frontCorners(s, p) { const f = s.wheelbase + frontOverhang(s), bx = p.x + f * Math.cos(p.h), by = p.y + f * Math.sin(p.h), sx = -Math.sin(p.h) * s.width / 2, sy = Math.cos(p.h) * s.width / 2; return [[bx + sx, by + sy], [bx - sx, by - sy]]; }
  /** 예상 경로(가이드라인): 후진이면 뒷범퍼, 전진이면 앞범퍼 좌/우 모서리의 궤적 */
  function path(s, p, steer, reverse, range, n) { const L = [], R = []; let q = p; const v = reverse ? -1 : 1, pick = reverse ? rearCorners : frontCorners;
    for (let i = 0; i <= n; i++) { const [l, r] = pick(s, q); L.push(l); R.push(r); q = step(s, q, steer, v, range / n); } return [L, R]; }
  function bumper(s, p, reverse) { const d = reverse ? -s.rearOverhang : s.wheelbase + frontOverhang(s); return [p.x + d * Math.cos(p.h), p.y + d * Math.sin(p.h)]; }

  // ---- 난이도 · 유형 · 옵션 ----
  // 치수 근거: 주차장법 시행규칙 — 일반형 2.5×5.0(2019~), 확장형 2.6×5.2, 2019 이전 일반형 2.3×5.0, 평행 2.0×6.0,
  // 차로 너비 직각 6.0 · 45° 대향 3.5 · 60° 대향 4.5 · 평행 3.3. 초급은 넓게, 중급은 법정, 고급은 구축 주차장(좁은 칸)·어긋난 시작.
  const TIERS = {
    // 초급은 법정 칸보다 넓은 연습용 칸(PM 2026-10-27 "주차칸이 너무 좁다, 움직일 공간이 더 넓어야"): 폭 3.0 = 장애인 칸(3.3) 가까이, 차로 9 m, 구역 여유 room 3 m
    "초급": { slotW: 3.0, slotL: 5.5, lane: 9.0, backGap: 1.5, gapExtra: 3.0, aLane: 6.5, tol: 10, offset: 0.0, room: 3.0, note: "넓은 칸 3.0×5.5 · 차로 9 m" },
    // 중급 = 요즘 아파트 확장형(PM 2026-10-27 "요즘 아파트 주차장은 좀 더 넓게 짓는다 — 좁은 곳은 고급에"), 고급 = 법정 최소 일반형, 옛 구축 2.3 m 는 고급 기둥 칸(12·15번)에만
    "중급": { slotW: 2.7, slotL: 5.3, lane: 7.5, backGap: 1.0, gapExtra: 2.4, aLane: 5.5, tol: 8, offset: 0.0, note: "아파트 칸 2.7×5.3 · 차로 7.5 m" },
    "고급": { slotW: 2.5, slotL: 5.0, lane: 6.0, backGap: 0.5, gapExtra: 1.8, aLane: 3.8, tol: 5, offset: 0.0, note: "최소 칸 2.5×5.0 · 차로 6 m" },
  };
  const APPROACH = 7.0;   // 공식 자리까지 D 로 다가가는 거리(m): 시작 때 칸이 앞에 보인다
  const KINDS = { perp_rev: "후면 직각주차", perp_fwd: "전면 직각주차", parallel: "평행주차", angled: "사선주차 45°" };
  const NEIGH = { none: "옆 차 없음", cars: "양옆 차", pillar: "기둥 + 양옆 차" };
  const SIDES = { R: "오른쪽 칸", L: "왼쪽 칸" };
  const HAZ = { 0: "돌발 없음", 1: "돌발 1회", 2: "돌발 2회" };
  const HAZ_TYPES = {
    // ahead: 범퍼에서 진행 방향으로 등장 거리, lat: 옆에서 등장 거리, speed: 가로지르는 속도, pause: 경로 한가운데서 멈춰 서는 시간
    walker: { label: "보행자", emoji: "🚶", ahead: 3.0, lat: 3.2, speed: 1.3, pause: 3.0, r: 0.25, height: 1.7 },
    child:  { label: "아이",   emoji: "🧒", ahead: 2.0, lat: 1.8, speed: 2.4, pause: 2.5, r: 0.22, height: 1.1 },
    cart:   { label: "쇼핑카트", emoji: "🛒", ahead: 2.6, lat: 2.6, speed: 0.9, pause: 6.0, r: 0.40, height: 1.0 },
  };

  const STAGES = [
    { no: 1,  tier: "초급", kind: "perp_rev", neighbors: "none",   side: "R", hazards: 0 },
    { no: 2,  tier: "초급", kind: "perp_rev", neighbors: "cars",   side: "R", hazards: 0 },
    { no: 3,  tier: "초급", kind: "perp_fwd", neighbors: "none",   side: "R", hazards: 0 },
    { no: 4,  tier: "초급", kind: "parallel", neighbors: "none",   side: "R", hazards: 0 },
    { no: 5,  tier: "초급", kind: "angled",   neighbors: "none",   side: "R", hazards: 0 },
    { no: 6,  tier: "중급", kind: "perp_rev", neighbors: "cars",   side: "R", hazards: 0 },
    { no: 7,  tier: "중급", kind: "perp_fwd", neighbors: "cars",   side: "R", hazards: 0 },
    { no: 8,  tier: "중급", kind: "perp_rev", neighbors: "cars",   side: "R", hazards: 1, hazType: "walker" },
    { no: 9,  tier: "중급", kind: "parallel", neighbors: "cars",   side: "R", hazards: 0 },
    { no: 10, tier: "중급", kind: "angled",   neighbors: "cars",   side: "R", hazards: 0 },
    { no: 11, tier: "고급", kind: "perp_rev", neighbors: "cars",   side: "L", hazards: 1 },
    { no: 12, tier: "고급", kind: "perp_rev", neighbors: "pillar", side: "R", hazards: 1, hazType: "child", slotW: 2.3 },
    { no: 13, tier: "고급", kind: "perp_fwd", neighbors: "pillar", side: "R", hazards: 1, slotW: 2.5 },
    { no: 14, tier: "고급", kind: "parallel", neighbors: "cars",   side: "R", hazards: 1, hazType: "cart" },
    { no: 15, tier: "고급", kind: "perp_rev", neighbors: "pillar", side: "L", hazards: 2, lane: 5.5, slotW: 2.3, boss: true },
  ];
  function stageTitle(st) {
    const bits = [KINDS[st.kind]];
    if (st.side === "L") bits.push("왼쪽 칸");
    bits.push(st.kind === "parallel" ? (st.neighbors === "none" ? "앞뒤 차 없음" : (st.tier === "고급" ? "좁은 틈" : "앞뒤 차")) : NEIGH[st.neighbors]);
    if (st.lane) bits.push(`차로 ${st.lane} m`);
    if (st.hazards) bits.push(st.hazards > 1 ? `돌발 ${st.hazards}회` : "돌발");
    return (st.no ? `${st.no}. ` : "") + `[${st.tier}] ` + bits.join(" · ");
  }

  // 코칭 문장: {R}/{L} 는 핸들 방향(왼쪽 칸이면 뒤집힘). 깜빡이 말은 고정(왼쪽=전진·오른쪽=후진).
  // cond = 이 단계를 떠나는 조건: X 공식 자리 도달(뒷차축 x) · R 가상 기어 R · F 가상 기어 D · > 핸들 {R} 400°↑ · < 핸들 {L} 400°↑ · 0 핸들 가운데(200°↓) · A 칸과 나란함 · D 칸과 45°
  // 첫 단계는 늘 '비상등 켜고 D 로 다가가 자리 잡기' — 주차 전 비상등은 뒤차에 알리는 기본 예절
  // 코칭 표(PM 2026-10-27 "글자 극단적으로 줄여": 코치 ≤ 22자 · 연습 ≤ 24자 · 완료 이름 ≤ 8자, tests/ui-copy-lint.mjs 가 막는다) = Kotlin Scenario.COACH
  const COACH = {
    perp_rev: [["비상등 켜고 D. 뒷차축이 옆 칸 지날 때까지", "X", "뒷차축이 옆 칸을 지날 때까지 가요", "자리 잡기"], ["멈추고 R. 핸들 {R} 끝까지", ">", "멈추고 R, 핸들을 {R} 끝까지 돌려요", "핸들 끝까지"], ["노란선이 칸 안에 오면 핸들 풀기", "0", "노란선이 칸 안에 오면 핸들을 풀어요", "핸들 풀기"], ["나란하면 핸들 가운데, 뒤 벽 전 브레이크", "A", "나란해지면 핸들 가운데, 브레이크", "나란히"]],
    perp_fwd: [["비상등 켜고 D. 칸 입구가 {R} 미러 옆까지", "X", "칸 입구가 {R} 미러 옆에 올 때까지 가요", "자리 잡기"], ["핸들 {R} 끝까지", ">", "핸들을 {R} 끝까지 돌려요", "핸들 끝까지"], ["노란선이 주차선과 나란하면 풀기", "0", "노란선이 주차선과 나란하면 풀어요", "핸들 풀기"], ["앞 벽 0.5 m, 빨간선 전에 브레이크", "A", "빨간선이 닿기 전에 브레이크", "멈추기"]],
    perp_fwd_tight: [["비상등 켜고 D, 바깥쪽으로 붙여 지나쳐요", "X", "바깥쪽으로 붙여 운전석이 칸 중앙을 지나요", "자리 잡기"], ["핸들 {R} 끝까지, 35°에서 멈춤", ">", "핸들 {R} 끝까지, 35°쯤에서 멈춰요", "핸들 끝까지"], ["R, 핸들 {L} 끝까지, 2 m 빼기", "R", "R 넣고 핸들 {L} 끝까지, 2 m 빼요", "한 번 빼기"], ["다시 D, 핸들 {R} 끝까지", "A", "다시 D, 핸들 {R} 끝까지 돌려요", "다시 들어가기"], ["앞 벽 0.5 m, 빨간선 전에 브레이크", "A", "빨간선이 닿기 전에 브레이크", "멈추기"]],
    parallel: [["비상등 켜고 D. 뒷바퀴가 앞차 뒷범퍼까지", "X", "뒷바퀴가 앞차 뒷범퍼 선까지 가요", "자리 잡기"], ["R. 핸들 {R} 끝까지, 45°에서 멈춤", "D", "R 넣고 핸들 {R} 끝까지, 45°에서 멈춰요", "45°"], ["핸들 {L} 끝까지, 앞차를 빠져나가며", "<", "핸들 {L} 끝까지, 앞차를 빠져나가요", "핸들 반대로"], ["연석과 나란하면 핸들 가운데, 브레이크", "A", "나란해지면 핸들 가운데, 브레이크", "나란히"]],
    parallel_none: [["비상등 켜고 D. 뒷바퀴가 주차선 앞 끝까지", "X", "뒷바퀴가 주차선 앞 끝까지 가요", "자리 잡기"], ["R. 핸들 {R} 끝까지, 45°에서 멈춤", "D", "R 넣고 핸들 {R} 끝까지, 45°에서 멈춰요", "45°"], ["핸들 {L} 끝까지, 앞 범퍼가 칸 안으로", "<", "핸들 {L} 끝까지, 앞 범퍼가 칸 안으로", "핸들 반대로"], ["연석과 나란하면 핸들 가운데, 브레이크", "A", "나란해지면 핸들 가운데, 브레이크", "나란히"]],
    angled: [["비상등 켜고 D. 칸 입구가 {R} 미러 옆까지", "X", "칸 입구가 {R} 미러 옆에 올 때까지 가요", "자리 잡기"], ["핸들 {R} 끝까지", ">", "핸들을 {R} 끝까지 돌려요", "핸들 끝까지"], ["나란하면(45°) 핸들 가운데, 직진", "A", "나란해지면 핸들 가운데, 직진해요", "나란히"], ["앞 벽 0.5 m, 빨간선 전에 브레이크", "A", "빨간선이 닿기 전에 브레이크", "멈추기"]],
  };
  const FLIP = { ">": "<", "<": ">" };

  const CAR_COLORS = ["#e8e8ea", "#c9ccd2", "#2b2d33", "#6b6f78", "#3d5a99", "#8a2b2b", "#d8d2c2", "#4a5d4f"];
  /** 난이도별 흔들기 폭: 시작 위치(m)·각(°)·옆 차 위치(m). 고급일수록 '공식 자리'에서 멀다. */
  const JITTER = { "초급": { x: 0.15, y: 0.1, h: 2, nb: 0.12 }, "중급": { x: 0.25, y: 0.15, h: 3, nb: 0.15 }, "고급": { x: 0.4, y: 0.2, h: 4, nb: 0.15 } };

  /** 스테이지/자유 옵션 → 시나리오. opt: {kind, tier, neighbors, side, hazards, hazType?, lane?, no?}
   *  seed 를 주면 매번 다른 상황(시작 자세·옆 차 위치·색·지하/지상). 없으면 정본 자리(테스트·공식 검증 기준). */
  function build(spec, opt, seed, flags) {
    const T = TIERS[opt.tier], SW = opt.slotW || T.slotW, SL = T.slotL, R = minR(spec), ro = spec.rearOverhang;
    const rng = seed === undefined || seed === null ? () => 0.5 : lcg(seed), J = JITTER[opt.tier], rnd = (a) => (rng() - 0.5) * 2 * a;
    const env = seed === undefined || seed === null ? "underground" : envOf(rng(), opt.tier);
    const colorOf = () => CAR_COLORS[Math.floor(rng() * CAR_COLORS.length) % CAR_COLORS.length];
    const modelOf = () => MODELS[Math.floor(rng() * MODELS.length) % MODELS.length];
    const lane = opt.lane || (opt.kind === "angled" ? T.aLane : (opt.kind === "perp_fwd" && opt.neighbors !== "none" ? Math.max(T.lane, 6.5) : T.lane));
    const obs = [], marks = []; let slot, start, forward = false, coachKey = opt.kind, formulaX = 0;
    // 옆 차 = 현대차 셋 중 하나, 실제 치수(아반떼 4.77×1.86 · 그랜저 5.05×1.88 · 투싼 4.64×1.87). 평행 앞뒤 차는 차 길이와 무관하게 틈이 같도록 범퍼 선 기준으로 놓는다.
    const car = (c, h, label, bumperAt) => { const u = [Math.cos(h), Math.sin(h)], v = [-Math.sin(h), Math.cos(h)], dl = opt.kind === "parallel" ? rnd(0) : rnd(J.nb * 1.6), dw = rnd(J.nb * 0.6);
      const color = colorOf(), model = modelOf(), M = SPECS[model], hx = M.length / 2, hy = M.width / 2, shift = bumperAt ? bumperAt * hx : 0;
      return { b: obb([c[0] + u[0] * (dl + shift) + v[0] * dw, c[1] + u[1] * (dl + shift) + v[1] * dw], h, hx, hy), label, car: true, color, model }; };
    if (opt.kind === "perp_rev" || opt.kind === "perp_fwd") {
      forward = opt.kind === "perp_fwd";
      if (forward && opt.neighbors !== "none") coachKey = "perp_fwd_tight";
      slot = obb([0, -SL / 2], PI / 2, SL / 2, SW / 2);
      for (const k of [-2, -1, 1, 2]) marks.push(obb([k * SW, -SL / 2], PI / 2, SL / 2, SW / 2));
      obs.push({ b: obb([0, -SL - (T.backGap || 0.4)], 0, 9, 0.25), label: "뒤 벽", car: false });   // 뒤 벽 거리는 난이도별(PM 2026-10-27 "쉬운 난이도는 넓혀야")
      obs.push({ b: obb([0, lane + 0.3], 0, 12, 0.3), label: "벽", car: false });
      // 기둥은 입구 모서리. 후면은 회전 안쪽(오른쪽), 전면은 접근 쪽(왼쪽) — 전면에서 바깥쪽 기둥은 한 번에 들어갈 수 없는 기하.
      const pillarSide = forward ? -1 : 1, hasPillar = opt.neighbors === "pillar";
      if (hasPillar) obs.push({ b: obb([pillarSide * (SW / 2 + 0.5), -0.5], 0, 0.3, 0.3), label: "기둥", car: false });
      if (opt.neighbors !== "none") {
        obs.push(car([-SW - (hasPillar && pillarSide < 0 ? 1.0 : 0), -SL / 2], PI / 2, "옆 차"));
        obs.push(car([SW + (hasPillar && pillarSide > 0 ? 1.0 : 0), -SL / 2], PI / 2, "옆 차"));
        if (seed !== undefined && seed !== null) for (const k of [-2, 2]) if (rng() < 0.7) obs.push(car([k * SW + (hasPillar ? Math.sign(k) * (Math.sign(k) === pillarSide ? 1.0 : 0) : 0), -SL / 2], PI / 2, "주차 차량"));
      }
      // 전면에 옆 차가 있으면 반대쪽(먼 벽 쪽)으로 붙여서 시작한다 — 연수 공식 '전면주차는 바깥쪽으로 최대한 붙여서'
      const y0 = lane / 2 + 0.2 + (forward && opt.neighbors !== "none" ? 0.7 : 0);
      // 후면: 뒷차축이 칸 중심선에서 R 앞에 있으면 가득 꺾은 후진 사분원 뒤 칸에 정렬된다(+0.15 여유). 고급은 offset 만큼 더 지나친 자리.
      // 전면: 뒷차축이 칸 중심선에서 R 뒤에 있을 때 가득 꺾으면 정렬(옆 차 없을 때). 그 1 m 전에서 시작. 옆 차가 있으면 더 지나쳐서 꺾고 한 번 뺀다.
      // 공식대로라면 핸들을 끝까지 돌리는 뒷차축 x: 후면 = R(+0.15), 전면(옆 차 없음) = −R, 전면(옆 차) = −R + 2.5(지나쳐서 꺾고 한 번 뺀다)
      formulaX = forward ? (opt.neighbors === "none" ? -R : -R + 2.5) : R + 0.15;
      // 시작은 그보다 APPROACH 만큼 앞(x 작은 쪽): 칸이 앞에 보이는 자리에서 D 로 다가가 자리를 잡는다
      start = { x: formulaX - APPROACH, y: y0, h: 0 };
    } else if (opt.kind === "parallel") {
      const gap = spec.length + T.gapExtra, W = 2.3, cy = -W / 2;                 // 평행 칸 폭 2.3(법정 2.0 + 연석 여유)
      slot = obb([0, cy], 0, gap / 2, W / 2);
      obs.push({ b: obb([0, -W - 0.15], 0, 12, 0.15), label: "연석", car: false });
      if (opt.neighbors !== "none") { obs.push(car([gap / 2 + 0.1, cy], 0, "앞차", 1)); obs.push(car([-(gap / 2 + 0.1), cy], 0, "뒷차", -1)); }
      else coachKey = "parallel_none";
      // 45° 두 번 꺾는 공식: 옆으로 2·R(1−cos45°) 만큼 들어간다 → 시작 y 를 거기에 맞춘다. 뒷차축 = 앞차 뒷범퍼 선(= 칸 앞 끝).
      formulaX = gap / 2;
      start = { x: formulaX - APPROACH, y: cy + 2 * R * (1 - Math.cos(PI / 4)), h: 0 };
    } else { // angled 45°, 전진 진입
      forward = true;
      const h = -PI / 4, u = [Math.cos(h), Math.sin(h)], cy = -(SL / 2 + SW / 2) * Math.SQRT1_2;   // 칸 윗 모서리가 y=0 (차로 가장자리)
      slot = obb([0, cy], h, SL / 2, SW / 2);
      const pitch = SW * Math.SQRT2;
      for (const k of [-2, -1, 1, 2]) marks.push(obb([k * pitch, cy], h, SL / 2, SW / 2));
      if (opt.neighbors !== "none") { obs.push(car([-pitch, cy], h, "옆 차")); obs.push(car([pitch, cy], h, "옆 차")); if (seed !== undefined && seed !== null) for (const k of [-2, 2]) if (rng() < 0.7) obs.push(car([k * pitch, cy], h, "주차 차량")); }
      obs.push({ b: obb([0, cy - (SL / 2) * Math.SQRT1_2 - SW / 2 * Math.SQRT1_2 - (T.backGap || 0.3)], 0, 14, 0.25), label: "뒤 벽", car: false });
      obs.push({ b: obb([0, lane + 0.3], 0, 14, 0.3), label: "벽", car: false });
      // 45° 가득 꺾으면 y 가 R(1−cos45°) 내려가고 x 가 R·sin45° 나아간다. 그 끝이 칸 축 위에 오도록 꺾는 점을 잡고 1 m 전에서 시작.
      const y0 = lane / 2 + 0.2, yEnd = y0 - R * (1 - Math.SQRT1_2);
      const sAxis = -(yEnd - cy) / Math.SQRT1_2, xEnd = sAxis * Math.SQRT1_2;     // 칸 축 위 점 (cx=0 기준)
      formulaX = xEnd - R * Math.SQRT1_2;
      start = { x: formulaX - APPROACH, y: y0, h: 0 };
    }
    if (seed !== undefined && seed !== null) { const dx = rnd(J.x), dy = rnd(J.y), dh = rad(rnd(J.h)); if (!(flags && flags.noStartJitter)) start = { x: start.x + dx, y: start.y + dy, h: start.h + dh }; }
    let coach = COACH[coachKey].map(([t, c, g, o]) => [t, c, g, o]);   // [코치 문장, 조건, 연습하기 문장(~해 보세요), 완료 이름] — Kotlin CoachStep 과 같다(PM 2026-10-27 "연습 모드 대사가 달라야")
    const flipSide = opt.side === "L";
    if (flipSide) { slot = mirrorObb(slot); obs.forEach(o => o.b = mirrorObb(o.b)); for (let i = 0; i < marks.length; i++) marks[i] = mirrorObb(marks[i]);
      start = { x: start.x, y: -start.y, h: -start.h }; coach = coach.map(([t, c, g, o]) => [t, FLIP[c] || c, g, o]); }
    const Rw = flipSide ? "왼쪽" : "오른쪽", Lw = flipSide ? "오른쪽" : "왼쪽";
    const rl = x => x.replace(/\{R\}/g, Rw).replace(/\{L\}/g, Lw); coach = coach.map(([t, c, g, o]) => [rl(t), c, rl(g), o]);
    const walls = obs.filter(o => o.b.hx > 5), rest = [slot, ...obs.filter(o => o.b.hx <= 5).map(o => o.b), ...marks, body(spec, start), body(spec, { x: formulaX, y: start.y, h: 0 })];
    const bb = bboxOf(rest, 0.4);
    if (walls.length) { let y0 = bb.c[1] - bb.hy, y1 = bb.c[1] + bb.hy; walls.forEach(w => corners(w.b).forEach(p => { y0 = Math.min(y0, p[1]); y1 = Math.max(y1, p[1]); })); bb.c = [bb.c[0], (y0 + y1) / 2]; bb.hy = (y1 - y0) / 2 + 0.2; }
    return { title: opt.title || stageTitle(opt), kind: opt.kind, tier: opt.tier, forward, slot, obs, marks, start, coach,
      tol: T.tol, hazards: opt.hazards || 0, hazType: opt.hazType || null, bounds: bb, tierNote: T.note, env, seed: seed ?? null, lane, formulaX, approach: APPROACH, room: T.room || 1.5 };
  }
  function collides(sc, b) { const o = sc.obs.find(o => intersects(o.b, b)); if (o) return o;
    // 연습 구역(화면 범위 + 1.5 m) 밖도 막는다 — 끝없이 후진해 사라지지 않게
    const B = sc.bounds; if (!inside(b, obb(B.c, 0, B.hx + (sc.room || 1.5), B.hy + (sc.room || 1.5)))) return { label: "연습 구역 끝", car: false }; return null; }
  function headingDiff(sc, h) { const dh = ((h - sc.slot.h) % PI + PI) % PI; return Math.min(dh, PI - dh); }
  function parked(sc, b, h) { return headingDiff(sc, h) < rad(sc.tol) && inside(b, obb(sc.slot.c, sc.slot.h, sc.slot.hx + 0.3, sc.slot.hy + 0.15)); }

  // ---- 엔진 (Kotlin GameEngine 과 같은 규칙) ----
  // 입력: {steer, signal: "OFF"|"LEFT"|"RIGHT", brake(true/false 또는 밟은 깊이 0~1), gearP, hazardLights}.
  // 실차 규칙(PM 2026-10-25): ① 가상 기어 D·R 은 브레이크를 밟고 있어야 들어간다(N 은 언제나) ② 카메라·가이드라인은 들어간 기어를 따른다
  // ③ 브레이크를 살살 밟으면 느리게 간다(깊이 60% 부터 정지 — 실차 SDK 는 밟음/뗌만 주므로 실차는 0/1) ④ 들어온 뒤 브레이크 밟고 N(레버 OFF) 이어야 끝.
  // 규칙(PM 2026-10-25): 오토 차 주차처럼 '브레이크를 밟은 채 시작 → 깜빡이로 방향(왼쪽 D 전진·오른쪽 R 후진) → 브레이크를 떼면 크리핑 속도(0.6 m/s)로 움직이고, 밟으면 선다'. 가속 페달 없음.
  // 브레이크를 한 번 밟기 전에는 깜빡이를 올려도 움직이지 않는다(실차에서 P 를 빼려면 브레이크를 밟아야 하는 것과 같다).
  /** 환경: 지하 60% · 지상 40%. 고급은 지상 몫을 지상 20 · 밤 12 · 비 8 로 나눈다(카메라 선 읽기가 진짜 어려운 상황) */
  function envOf(r, tier) { if (r < 0.6) return "underground"; if (tier !== "고급") return "outdoor"; return r < 0.8 ? "outdoor" : r < 0.92 ? "night" : "rain"; }
  const ENV_LABEL = { underground: "지하", outdoor: "지상", night: "지상 · 밤", rain: "지상 · 비" };
  /** 조사 이/가: 마지막 글자에 받침이 있으면 '이', 없으면 '가' (보행자가 · 아이가 · 쇼핑카트가) */
  const ga = w => w + ((w.charCodeAt(w.length - 1) - 0xAC00) % 28 ? "이" : "가");
  const brakeDepth = b => typeof b === "number" ? Math.max(0, Math.min(1, b)) : (b ? 1 : 0);
  const BRAKE_STOP = 0.6;   // 이 깊이부터 완전 정지
  const GEAR_HINT = "기어를 P에 두면 계속할 수 있어요";
  // 깜빡이 레버 = 가상 기어(PM 2026-10-27, 컬럼 레버와 같은 방향): 레버 위(오른쪽 깜빡이) = D 전진 · 아래(왼쪽) = R 후진 · 가운데 = N. 종전(10-25) 왼쪽 = D 는 뒤집었다.
  const sigOfGear = g => g === "D" ? "RIGHT" : g === "R" ? "LEFT" : "OFF";
  // LCG(Kotlin Lcg 와 같은 상수). 첫 값은 작은 seed 에서 늘 0.24~0.3 이라(지하만 나옴) 한 번 버리고 시작한다(워밍업, Kotlin 도 같다)
  function lcg(seed) { let s = (seed >>> 0) || 1; s = (s * 1664525 + 1013904223) >>> 0; return () => (s = (s * 1664525 + 1013904223) >>> 0) / 4294967296; }
  class Engine {
    constructor(spec, sc, seed) { this.spec = spec; this.speed = 0.6; this.limit = 300; this.seed = seed || 7; this.load(sc); }
    load(sc) { this.sc = sc; this.reset(); }
    reset() {
      const S = this; S.pose = { ...S.sc.start }; S.phase = "READY"; S.hits = 0; S.elapsed = 0; S.trace = []; S.coach = 0; S.stepAt = 0;
      S.hint = S.sc.coach[0][0]; S.lastV = 0; S.lastReverse = !S.sc.forward; S.travelled = 0;
      S.hazard = null; S.hazStats = { total: 0, hit: 0, avoided: 0 }; S.rng = lcg(S.seed);
      S.firstLock = null; S.dirChanges = 0; S.steerFlips = 0; S._lastDir = 0; S._lastSteerSign = 0;
      S.started = false; S.hint = "브레이크 밟고 시작. 비상등 켜고 D"; S.hazLightsEver = false; S.hazLightsBeforeLock = false;
      S.gear = "N"; S.gearRefused = false; S.inSlot = false; S.brakeD = 1; if (S.autoPark === undefined) S.autoPark = true;   // 화면이 끈다(사람은 P 로 끝)
      const types = Object.keys(HAZ_TYPES);
      S.hazPlan = []; for (let i = 0; i < (S.sc.hazards || 0); i++) S.hazPlan.push({ at: 1.2 + S.rng() * 1.3 + i * 2.2, type: S.sc.hazType || types[Math.floor(S.rng() * types.length)] });
    }
    spawnHazard(plan, v) {
      const H = HAZ_TYPES[plan.type], rev = v < 0, p = this.pose, dir = rev ? -1 : 1;
      const fwd = [Math.cos(p.h) * dir, Math.sin(p.h) * dir], nrm = [-Math.sin(p.h), Math.cos(p.h)], side = this.rng() < 0.5 ? 1 : -1;
      const bp = bumper(this.spec, p, rev), o = [bp[0] + fwd[0] * H.ahead, bp[1] + fwd[1] * H.ahead];
      this.hazard = { type: plan.type, ...H, pos: [o[0] + nrm[0] * H.lat * side, o[1] + nrm[1] * H.lat * side], vel: [-nrm[0] * H.speed * side, -nrm[1] * H.speed * side],
        origin: o, nrm, side, pauseLeft: H.pause, life: (2 * H.lat + 1.0) / H.speed + H.pause, wasStopped: false };
      this.hazStats.total++; this.hint = `${H.emoji} ${H.label}! 멈춰요.`; this.flash = H.label;
    }
    tick(inp, dt) {
      const S = this; S.flash = null;
      if (S.phase === "PARKED" || S.phase === "ENDED") return S.snap(inp);
      if (!inp.gearP) { if (S.hint !== GEAR_HINT) S.hintBeforeGear = S.hint; S.hint = GEAR_HINT; S.lastV = 0; return S.snap(inp); }
      if (S.hint === GEAR_HINT) S.hint = S.hintBeforeGear;   // P 로 돌아오면 하던 안내로(시작 전이면 시작 안내, Kotlin gearBlocked 와 같다)
      if (inp.hazardLights) { S.hazLightsEver = true; if (!S.firstLock) S.hazLightsBeforeLock = true; }
      const bd = brakeDepth(inp.brake), braked = bd >= BRAKE_STOP; S.brakeD = bd; S.gearRefused = false;
      // 가상 기어: 레버가 가리키는 기어로 바꾸되 D·R 은 브레이크를 밟고 있어야(실차 시프트 록). N 은 언제나.
      const want = inp.signal === "RIGHT" ? "D" : inp.signal === "LEFT" ? "R" : "N";
      if (want !== S.gear) { if (want === "N" || braked) S.gear = want; else S.gearRefused = true; }
      if (S.gear === "R") S.lastReverse = true; else if (S.gear === "D") S.lastReverse = false;   // 카메라·가이드라인은 들어간 기어를 따른다(R 이면 후방)
      if (!S.started) { if (braked) { S.started = true; S.hint = S.sc.coach[0][0]; } else { S.lastV = 0; return S.snap(inp); } }
      // 끝내기 = P 버튼(PM 2026-10-27 "P 가 종료", 멈춘 상태에서만, finish()). 시연(autoPark)만 들어온 뒤 브레이크 + N 이면 저절로 끝.
      if (S.autoPark && S.inSlot && braked && S.gear === "N") { S.phase = "PARKED"; S.hint = S.doneText(); return S.snap(inp); }
      const creep = S.speed * Math.max(0, 1 - bd / BRAKE_STOP);   // 살살 밟으면 느리게
      const v = S.gear === "D" ? creep : S.gear === "R" ? -creep : 0;
      // 돌발: 움직이는 중 계획된 거리에서 등장, 옆에서 가로질러 간다
      if (S.hazard) {
        const hz = S.hazard; hz.life -= dt;
        // 경로 한가운데(옆 거리 0 을 지나는 순간) 에 pause 초 동안 멈춰 선다 — 안 멈추면 닿는다
        const crossed = hz.crossed || false;
        if (!crossed) { const next = [hz.pos[0] + hz.vel[0] * dt, hz.pos[1] + hz.vel[1] * dt];
          const s0 = (hz.pos[0] - hz.origin[0]) * hz.nrm[0] + (hz.pos[1] - hz.origin[1]) * hz.nrm[1], s1 = (next[0] - hz.origin[0]) * hz.nrm[0] + (next[1] - hz.origin[1]) * hz.nrm[1];
          if (Math.sign(s0) !== Math.sign(s1)) { hz.crossed = true; hz.pos = [hz.origin[0], hz.origin[1]]; } else hz.pos = next; }
        else if (hz.pauseLeft > 0) hz.pauseLeft -= dt;
        else hz.pos = [hz.pos[0] + hz.vel[0] * dt, hz.pos[1] + hz.vel[1] * dt];
        const d = distPoint(body(S.spec, S.pose), hz.pos);
        if (d <= hz.r + 0.1) { if (v !== 0) { S.hits++; S.hazStats.hit++; S.phase = "HIT"; S.hint = `${hz.emoji} ${hz.label} 충돌! 보이면 바로 브레이크`; S.hazard = null; S.lastV = 0; return S.snap(inp); } }
        else if (v === 0 && d < 3.0) hz.wasStopped = true;
        if (S.hazard && hz.life <= 0) { S.hazard = null; S.hazStats.avoided++; S.hint = hz.wasStopped ? "잘 멈췄어요. 이어서 가요" : "지나갔어요. 보이면 바로 멈춰요"; S.stepAt = S.elapsed; }
      } else if (v !== 0 && S.hazPlan.length && S.travelled >= S.hazPlan[0].at) S.spawnHazard(S.hazPlan.shift(), v);

      if (v !== 0) {
        if (S.phase === "READY") S.phase = "DRIVING";
        const nx = step(S.spec, S.pose, inp.steer, v, dt), b = body(S.spec, nx), hit = collides(S.sc, b);
        if (hit) { if (S.phase !== "HIT") S.hits++; S.phase = "HIT"; S.hint = `${hit.label}에 닿았어요. 반대로 빼요`; }
        else { S.pose = nx; S.phase = "DRIVING"; S.elapsed += dt; S.travelled += Math.abs(v) * dt; S.trace.push({ t: S.elapsed, steer: inp.steer, v, pose: { x: nx.x, y: nx.y, h: nx.h } }); S.lastReverse = v < 0;
          if (!S.firstLock && Math.abs(inp.steer) > 500) S.firstLock = { ...S.pose, travelled: S.travelled };
          const dir = Math.sign(v); if (S._lastDir && dir !== S._lastDir) S.dirChanges++; S._lastDir = dir;
          const ss = Math.abs(inp.steer) > 150 ? Math.sign(inp.steer) : 0; if (ss && S._lastSteerSign && ss !== S._lastSteerSign) S.steerFlips++; if (ss) S._lastSteerSign = ss;
          if (!S.hazard) S.advanceCoach(inp);
          S.inSlot = parked(S.sc, b, S.pose.h); if (S.inSlot) S.hint = S.autoPark ? "들어왔어요. 브레이크 밟고 N" : "들어왔어요. 다 되면 멈추고 P"; }
      } else if (S.phase === "HIT") S.phase = "DRIVING";
      S.lastV = v;
      if (S.elapsed > S.limit) S.hint = "5분 지났어요. 오늘은 여기까지";
      return S.snap(inp);
    }
    advanceCoach(inp) {
      const S = this, c = S.sc.coach, cond = c[S.coach] && c[S.coach][1], st = inp.steer, hd = headingDiff(S.sc, S.pose.h);
      let go = false;
      switch (cond) {
        case "X": go = S.pose.x >= S.sc.formulaX - 0.25; break;
        case "R": go = S.gear === "R"; break; case "F": go = S.gear === "D"; break;
        case ">": go = st > 400; break; case "<": go = st < -400; break;
        case "0": go = Math.abs(st) < 200 && S.elapsed - S.stepAt > 1.0; break;
        case "A": go = hd < rad(12); break; case "D": go = hd > rad(35) && hd < rad(55); break;
      }
      if (go && S.coach < c.length - 1) { S.coach++; S.stepAt = S.elapsed; }
      S.hint = c[Math.min(S.coach, c.length - 1)][0];
    }
    /** 기록(리더보드용): 주행 시간 + 패널티 초. 충돌 5 · 사람/카트 사고 15 · 비상등 안 켬 2. 전후진 보정은 감점하지 않는다(시간에 이미 들어가고, 연수 때도 몇 번이든 고쳐 넣으라고 배운다 — PM 2026-10-28). 클린 = 충돌 0 · 사고 0 · 돌발 전원 멈춤 */
    score() {
      const S = this, sc = S.sc, expect = sc.kind === "perp_fwd" && sc.coach.length === 5 ? 2 : (sc.forward ? 0 : 1);
      const corrections = Math.max(0, S.dirChanges - expect), noHazLights = !S.hazLightsBeforeLock, pen = S.hits * 5 + S.hazStats.hit * 15 + (noHazLights ? 2 : 0);
      // 정확도 우선(리서치 2026-10-27: 시간 기록 중심이면 급하게 꺾는 습관 — 시간은 절반만, 정렬 패널티 = 각 1° 0.3초 + 치우침 10 cm 0.3초). Kotlin score() 와 같다
      const u = [Math.cos(sc.slot.h), Math.sin(sc.slot.h)], across = (S.pose.x - sc.slot.c[0]) * -u[1] + (S.pose.y - sc.slot.c[1]) * u[0];
      const angDeg = Math.round(headingDiff(sc, S.pose.h) * 180 / PI), offCm = Math.round(Math.abs(across) * 100), align = Math.round((Math.min(angDeg, 20) * 0.3 + Math.min(offCm, 100) / 10 * 0.3) * 10) / 10;
      const drive = Math.round(S.elapsed * 0.5 * 10) / 10;
      return { drive, elapsed: S.elapsed, pen, align, angDeg, offCm, total: drive + pen + align, hits: S.hits, hazHit: S.hazStats.hit, corrections, noHazLights, clean: S.hits === 0 && S.hazStats.hit === 0 && S.hazStats.total === S.hazStats.avoided && !noHazLights };
    }
    /** 끝난 뒤 피드백: 공식과 비교(핸들을 처음 끝까지 돌린 자리), 보정 횟수, 핸들 뒤집기 */
    review() {
      const S = this, out = [], sc = S.sc;
      if (S.firstLock && sc.formulaX !== undefined) { const d = (S.firstLock.x - sc.formulaX) * (sc.forward ? 1 : -1) * (sc.kind === "parallel" ? -1 : 1);
        // d > 0 = 공식 자리보다 더 지나쳐서(늦게) 꺾음
        out.push(Math.abs(d) < 0.3 ? "핸들을 끝까지 돌린 자리가 공식 자리와 거의 같았어요." : d > 0 ? `핸들을 끝까지 돌린 자리가 공식 자리보다 ${d.toFixed(1)} m 늦었어요.` : `핸들을 끝까지 돌린 자리가 공식 자리보다 ${(-d).toFixed(1)} m 일렀어요.`); }
      else if (!S.firstLock) out.push("핸들을 끝까지 돌리지 않고 들어갔어요. 좁은 곳에서는 가득 꺾어야 회전반경이 가장 작아요.");
      out.push(S.hazLightsBeforeLock ? "주차 전에 비상등을 켰어요. 뒤차가 알아요." : "주차 전에 비상등을 안 켰어요(+2초). 뒤차에 '여기 댈게요'를 알려 주세요.");
      const expect = sc.kind === "perp_fwd" && sc.coach.length === 5 ? 2 : (sc.forward ? 0 : 1);   // 접근(D) 뒤 R 로 바꾸는 한 번은 기본
      out.push(S.dirChanges <= expect ? (expect === 2 ? "전후진 한 번 빼기만으로 들어갔어요." : "자리 잡고 한 번에 들어갔어요.") : `전후진을 ${S.dirChanges}번 바꿨어요(보정 ${S.dirChanges - expect}회).`);
      if (S.steerFlips > (sc.kind === "parallel" ? 1 : expect ? 2 : 0) + 1) out.push(`핸들 방향을 ${S.steerFlips}번 뒤집었어요. 한 번 정한 방향을 끝까지 쓰는 게 깔끔해요.`);
      return out;
    }
    /** P 버튼(PM 2026-10-27 "유저가 다 됐다고 P 누르면 그때 종료"): 멈춘 상태에서만. 칸 안이면 PARKED(쏙), 아니면 ENDED(아직) — 왜 아닌지는 failText(). 돌려주는 값 = "ok" | "moving" | "idle" */
    finish() { const S = this; if (S.phase === "PARKED" || S.phase === "ENDED") return "idle"; if (!S.started) return "idle"; if (S.lastV !== 0) return "moving";
      if (S.inSlot) { S.phase = "PARKED"; S.hint = S.doneText(); } else { S.phase = "ENDED"; S.hint = S.failText(); } return "ok"; }
    /** 칸에 못 들어간 채 P: 무엇이 모자랐나 — 각(틀어짐) · 칸 밖(앞뒤·옆) */
    failText() { const S = this, sc = S.sc, b = body(S.spec, S.pose), hdDeg = headingDiff(sc, S.pose.h) * 180 / PI, out = [];
      if (hdDeg >= sc.tol) out.push(`차가 칸과 ${hdDeg.toFixed(0)}° 틀어져 있어요(기준 ${sc.tol}°)`);
      const u = [Math.cos(sc.slot.h), Math.sin(sc.slot.h)], v = [-u[1], u[0]], dx = S.pose.x - sc.slot.c[0], dy = S.pose.y - sc.slot.c[1];
      const along = dx * u[0] + dy * u[1], across = dx * v[0] + dy * v[1];   // 뒷차축 기준 칸 가운데에서 앞뒤·옆으로 얼마나
      if (!inside(b, obb(sc.slot.c, sc.slot.h, sc.slot.hx + 0.3, sc.slot.hy + 0.15))) { if (Math.abs(across) > sc.slot.hy * 0.5) out.push(`옆으로 ${Math.abs(across).toFixed(1)} m 치우쳤어요`); else out.push(`칸 밖이에요(${Math.abs(along) > sc.slot.hx ? "덜 들어갔어요" : "앞뒤가 걸쳐 있어요"})`); }
      return out.length ? "아직이에요. " + out.join(" · ") + "." : "아직이에요. 조금만 더 맞춰 보세요."; }
    /** 내려서 보면(PM 2026-10-27 아이디어 ④): 끝난 자세에서 왼쪽·오른쪽·앞(또는 뒤) 간격(cm). 옆 = 차 옆면 세 점 → 가장 가까운 장애물(옆 차·기둥·벽) 또는 주차선 중 가까운 것. 앞/뒤 = 범퍼 → 벽·연석·앞 선 */
    gaps() { const S = this, s = S.spec, p = S.pose, sc = S.sc, b = body(s, p), u = [Math.cos(p.h), Math.sin(p.h)], v = [-Math.sin(p.h), Math.cos(p.h)];
      const at = (du, dv) => [b.c[0] + u[0] * b.hx * du + v[0] * b.hy * dv, b.c[1] + u[1] * b.hx * du + v[1] * b.hy * dv];
      const slotU = [Math.cos(sc.slot.h), Math.sin(sc.slot.h)], slotV = [-slotU[1], slotU[0]];
      const lineGap = (pt, sideSign) => { const d = (pt[0] - sc.slot.c[0]) * slotV[0] + (pt[1] - sc.slot.c[1]) * slotV[1]; return sc.slot.hy - sideSign * d; };   // 그쪽 주차선까지(왼쪽 = +v 쪽)
      const side = sign => { let best = 1e9, what = "주차선"; for (const du of [0.8, 0, -0.8]) { const pt = at(du, sign); for (const o of sc.obs) { const d = distPoint(o.b, pt); if (d < best) { best = d; what = o.label === "기둥" ? "기둥" : o.car ? "옆 차" : o.label || "벽"; } } const lg = lineGap(pt, sign); if (lg < best) { best = lg; what = "주차선"; } } return { cm: Math.max(0, Math.round(best * 100)), what }; };
      const endSign = sc.forward ? 1 : -1, nose = at(endSign, 0); let fb = 1e9, fwhat = "주차선";
      for (const o of sc.obs) { const d = distPoint(o.b, nose); if (d < fb) { fb = d; fwhat = o.car ? "앞 차" : o.label || "벽"; } }
      const dl = sc.slot.hx - ((nose[0] - sc.slot.c[0]) * slotU[0] + (nose[1] - sc.slot.c[1]) * slotU[1]) * (((nose[0] - sc.slot.c[0]) * slotU[0] + (nose[1] - sc.slot.c[1]) * slotU[1]) >= 0 ? 1 : -1); if (dl < fb) { fb = dl; fwhat = "칸 끝 선"; }
      return { left: side(1), right: side(-1), end: { cm: Math.max(0, Math.round(fb * 100)), what: fwhat, label: sc.forward ? "앞" : "뒤" } }; }
    doneText() { const h = this.hazStats, base = this.hits === 0 ? "쏙! 한 번도 부딪히지 않았어요." : `쏙! ${this.hits}번 부딪혔어요. 기록에서 어디서 각을 잘못 잡았나 보세요.`;
      return h.total ? `${base} 돌발 ${h.total}회 중 ${h.avoided}회 멈춤${h.hit ? ` · ${h.hit}회 사고` : ""}.` : base; }
    snap(inp) { const S = this; return { phase: S.phase, pose: S.pose, body: body(S.spec, S.pose), steer: inp.steer, v: S.lastV, hint: S.hint, hits: S.hits, elapsed: S.elapsed,
      coach: S.coach, coachN: S.sc.coach.length, reverse: S.gear === "R" ? true : S.gear === "D" ? false : S.lastReverse, hazard: S.hazard, hazStats: S.hazStats, flash: S.flash,
      coachText: S.sc.coach[Math.min(S.coach, S.sc.coach.length - 1)][0], dirChanges: S.dirChanges, started: S.started, hazLights: !!inp.hazardLights,
      gear: S.gear, gearRefused: S.gearRefused, inSlot: S.inSlot, brakeD: S.brakeD }; }
  }

  // ---- 시연 운전자(튜토리얼): 공식대로 스스로 주차한다. 테스트의 풀이와 같은 규칙, 핸들은 사람처럼 돌린다(1000°/s). ----
  // 단계마다 자막을 낸다. opt 는 스테이지 옵션(kind·tier·neighbors·side). 시작 자세는 공식 자리(flags.noStartJitter)여야 한다.
  class Demo {
    constructor(S, spec, sc, opt) { this.S = S; this.spec = spec; this.sc = sc; this.opt = opt; this.T = TIERS[opt.tier]; this.side = opt.side === "L" ? -1 : 1; this.phase = 0; this.t0 = 0; this.steer = 0; this.caption = ""; this.done = false; this.hold = 0.6;
      this.R = opt.side === "L" ? "왼쪽" : "오른쪽"; this.L = opt.side === "L" ? "오른쪽" : "왼쪽"; }
    /** 엔진 상태를 보고 이번 틱 입력을 정한다 */
    step(eng, dt) {
      const { sc, opt, T, side } = this, hd = headingDiff(sc, eng.pose.h), trav = eng.travelled, R = this.R, L = this.L;
      let target = 0, signal = "OFF", cap = this.caption;
      if (eng.phase === "PARKED") { this.done = true; this.caption = "쏙! 이제 직접 해 보세요"; return { steer: 0, signal: "OFF", brake: true, caption: this.caption, done: true }; }
      // 실수 보기(오류 훈련, 리서치 2026-10-27): 후면 직각에서 핸들을 25° 남기고 일찍 풀어 비뚤게 들어가는 모습을 한 번 보여 준다. DemoDriver 와 같다
      if (opt.mistake === "early" && (eng.phase === "HIT" || (this.mT !== undefined && trav - this.mT > 2.2))) { this.done = true; this.caption = "핸들을 일찍 풀면 이렇게 비뚤어져요. 이제 바르게 해 보세요"; return { steer: 0, signal: "OFF", brake: true, hazardLights: true, caption: this.caption, done: true }; }
      if (eng.inSlot) { this.caption = "들어왔어요. 브레이크 밟고 N"; return { steer: this.steer, signal: "OFF", brake: true, hazardLights: true, caption: this.caption, done: false }; }
      if (this.hold > 0) { this.hold -= dt; this.caption = "브레이크 밟고 비상등, D 넣고 살살 떼요"; return { steer: 0, signal: sigOfGear("D"), brake: true, hazardLights: true, caption: this.caption, done: false }; }
      if (eng.hazard) { this.caption = `${eng.hazard.emoji} ${ga(eng.hazard.label)} 먼저 브레이크, 지나가면 이어서`; return { steer: this.steer, signal: this.arrived ? (sc.forward ? sigOfGear("D") : sigOfGear("R")) : sigOfGear("D"), brake: true, hazardLights: true, caption: this.caption, done: false }; }
      // 접근: 공식 자리까지 D 로. 후진 코스는 거기서 멈추고 R.
      if (this.phase === 0 && !this.arrived) {
        if (eng.pose.x < sc.formulaX) { this.caption = opt.kind === "perp_rev" ? "D 로 천천히, 뒷차축이 옆 칸 지날 때까지" : opt.kind === "parallel" ? "D 로 앞차 옆에, 뒷바퀴가 뒷범퍼 선까지" : opt.kind === "angled" ? "D 로 천천히, 칸 입구가 미러 옆까지" : opt.neighbors === "none" ? "D 로 천천히, 칸 입구 선이 미러 옆까지" : "D 로 바깥쪽에 붙여, 운전석이 칸 중앙까지";
          return { steer: this.steer = 0, signal: sigOfGear("D"), brake: false, hazardLights: true, caption: this.caption, done: false }; }
        this.arrived = true; this.stopHold = 0.5;
      }
      if (this.stopHold > 0) { this.stopHold -= dt; this.caption = sc.forward ? "여기! 핸들을 끝까지 돌려요" : "여기서 멈추고 R, 서서 핸들 끝까지"; return { steer: this.steer, signal: sc.forward ? "LEFT" : "RIGHT", brake: true, hazardLights: true, caption: this.caption, done: false }; }
      switch (opt.kind) {
        case "perp_rev": signal = sigOfGear("R");
          if (this.phase === 0) this.phase = 1;
          if (this.phase === 1) { target = 540 * side; cap = `뒷차축이 지났어요. 핸들 ${R} 끝까지`; if (hd < rad(opt.mistake === "early" ? 25 : 0.7)) { this.phase = 2; if (opt.mistake === "early") this.mT = trav; } }
          if (this.phase === 2) { target = 0; cap = opt.mistake === "early" ? "25° 남았는데 핸들을 풀어요 — 일찍 푼 실수" : "나란해졌어요. 핸들 가운데, 직진 후진"; }
          break;
        case "angled": signal = sigOfGear("D");
          if (this.phase === 0) this.phase = 1;
          if (this.phase === 1) { target = 540 * side; cap = `칸 입구가 미러 옆! 핸들 ${R} 끝까지`; if (hd < rad(0.7)) this.phase = 2; }
          if (this.phase === 2) { target = 0; cap = "나란해졌어요(45°). 핸들 가운데, 직진"; }
          break;
        case "perp_fwd":
          if (opt.neighbors === "none") { signal = sigOfGear("D");
            if (this.phase === 0) this.phase = 1;
            if (this.phase === 1) { target = 540 * side; cap = `칸 입구가 미러 옆! 핸들 ${R} 끝까지`; if (hd < rad(0.7)) this.phase = 2; }
            if (this.phase === 2) { target = 0; cap = "나란해졌어요. 핸들 가운데, 앞 벽 전까지"; }
          } else { signal = sigOfGear("D"); const h = -eng.pose.h * side;
            if (this.phase === 0) this.phase = 1;
            if (this.phase === 1) { target = 540 * side; cap = `운전석이 칸 중앙을 지났어요. 핸들 ${R}으로 끝까지, 35°까지만.`; if (h >= rad(35)) { this.phase = 2; this.t0 = trav; } }
            if (this.phase === 2) { target = -540 * side; signal = sigOfGear("R"); cap = `35°! 핸들 ${L} 끝까지, R 로 2 m 빼요`; if (trav - this.t0 >= 2.0) this.phase = 3; }
            if (this.phase === 3) { target = 540 * side; cap = `다시 D, 핸들 ${R} 끝까지`; if (hd < rad(0.7)) this.phase = 4; }
            if (this.phase === 4) { target = 0; cap = "나란해졌어요. 핸들 가운데, 앞 벽 전까지"; }
          }
          break;
        case "parallel": signal = sigOfGear("R");
          if (this.phase === 0) this.phase = 1;
          if (this.phase === 1) { target = 540 * side; cap = `핸들 ${R} 끝까지, 45°까지 후진`; if (hd > rad(44.5)) this.phase = 2; }
          if (this.phase === 2) { target = -540 * side; cap = `45°! 핸들 ${L} 끝까지, 계속 후진`; if (hd < rad(0.7)) this.phase = 3; }
          if (this.phase === 3) { target = 0; cap = "연석과 나란해요. 핸들 가운데, 브레이크"; }
          break;
      }
      // 돌발이 보이면 멈춘다 — 시연에서도 같은 규칙
      let brake = false; if (eng.hazard) { brake = true; cap = `${eng.hazard.emoji} ${ga(eng.hazard.label)} 먼저 브레이크, 지나가면 이어서`; }
      // 핸들은 사람처럼 900°/s 로 돌리되, 연수 공식대로 '멈춰서 돌리고 움직인다'(끝까지 돌릴 때는 서서) — 그래야 평행·전면 보정이 정확히 맞는다
      const maxStep = 900 * dt, gap = target - this.steer; this.steer += Math.max(-maxStep, Math.min(maxStep, gap));
      if (Math.abs(gap) > 8) brake = true;   // 핸들이 목표에 거의 닿을 때까지는 서서 돌린다(옆 차가 큰 차일 때 여유가 8 cm 뿐)
      if (sigOfGear(eng.gear) !== signal) brake = true;   // 기어를 바꿀 땐 밟고 바꾼다(실차 시프트 록)
      this.caption = cap;
      return { steer: this.steer, signal, brake, hazardLights: true, caption: cap, done: false };
    }
  }

  return { SPECS, MODELS, TIERS, KINDS, ENV_LABEL, envOf, NEIGH, SIDES, HAZ, HAZ_TYPES, STAGES, CAR_COLORS, JITTER, Demo, stageTitle, build, collides, parked, headingDiff,
    obb, corners, intersects, inside, distPoint, bboxOf, ratio, frontOverhang, minR, wheelRad, step, body, rearCorners, frontCorners, path, bumper, Engine };
});
