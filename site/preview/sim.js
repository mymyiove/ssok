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
    AVANTE:   { key: "AVANTE",   name: "디 올 뉴 아반떼", length: 4.765, width: 1.855, wheelbase: 2.750, rearOverhang: 1.050, maxWheelDeg: 35, maxSteer: 540 },
    GRANDEUR: { key: "GRANDEUR", name: "더 뉴 그랜저",   length: 5.050, width: 1.880, wheelbase: 2.895, rearOverhang: 1.150, maxWheelDeg: 35, maxSteer: 540 },
  };
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
    "초급": { slotW: 2.6, slotL: 5.2, lane: 6.5, gapExtra: 2.4, aLane: 4.5, tol: 8, offset: 0.0, note: "확장형 칸 2.6×5.2 · 차로 6.5 m · 평행 틈 차 길이 + 2.4 m" },
    "중급": { slotW: 2.5, slotL: 5.0, lane: 6.0, gapExtra: 1.9, aLane: 3.5, tol: 6, offset: 0.0, note: "법정 일반형 2.5×5.0 · 차로 6.0 m · 평행 틈 + 1.9 m" },
    "고급": { slotW: 2.3, slotL: 5.0, lane: 6.0, gapExtra: 1.6, aLane: 3.5, tol: 4, offset: 0.6, note: "2019 이전 구축 칸 2.3×5.0 · 평행 틈 + 1.6 m(한 번에 들어가는 최소) · 시작 위치 0.6 m 어긋남" },
  };
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
    { no: 12, tier: "고급", kind: "perp_rev", neighbors: "pillar", side: "R", hazards: 1, hazType: "child" },
    { no: 13, tier: "고급", kind: "perp_fwd", neighbors: "pillar", side: "R", hazards: 1, slotW: 2.5 },
    { no: 14, tier: "고급", kind: "parallel", neighbors: "cars",   side: "R", hazards: 1, hazType: "cart" },
    { no: 15, tier: "고급", kind: "perp_rev", neighbors: "pillar", side: "L", hazards: 2, lane: 5.5, boss: true },
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
  // cond = 이 단계를 떠나는 조건: R 후진 깜빡이 · F 전진 깜빡이 · > 핸들 {R} 400°↑ · < 핸들 {L} 400°↑ · 0 핸들 가운데(200°↓) · A 칸과 나란함 · D 칸과 45°
  const COACH = {
    perp_rev: [["오른쪽 깜빡이(후진)를 올리고 핸들은 가운데.", "R"], ["뒷차축이 옆 칸을 지나면 핸들을 {R}으로 끝까지.", ">"],
      ["노란선이 주차선 안으로 들어오면 핸들을 풀기 시작.", "0"], ["차체가 칸과 나란하면 핸들 가운데, 빨간선이 뒤 벽에 닿기 전에 브레이크.", "A"]],
    perp_fwd: [["왼쪽 깜빡이(전진), 핸들은 가운데로 칸 앞까지.", "F"], ["칸 입구 선이 {R} 사이드미러 옆에 오면 핸들을 {R}으로 끝까지.", ">"],
      ["보닛이 칸에 들어가며 노란선이 주차선과 나란해지면 풀기.", "0"], ["앞 벽 0.5 m, 빨간선이 닿기 전에 브레이크.", "A"]],
    // 옆 차가 있으면 전면은 한 번에 안 들어간다(앞 바깥 모서리가 옆 차를 쓴다) → 한 번 빼서 보정하는 것이 공식
    perp_fwd_tight: [["왼쪽 깜빡이(전진), 바깥쪽으로 붙여 칸 앞까지.", "F"], ["운전석이 칸 중앙을 지날 때 핸들을 {R}으로 끝까지.", ">"],
      ["차가 40°쯤 꺾이면 멈추고, 핸들 {L} 끝까지 + 오른쪽 깜빡이(후진)로 2 m 빼기.", "R"],
      ["다시 왼쪽 깜빡이(전진), 핸들 {R} 끝까지. 나란해지면 가운데.", "A"], ["앞 벽 0.5 m, 빨간선이 닿기 전에 브레이크.", "A"]],
    parallel: [["뒷바퀴가 앞차 뒷범퍼 선에 오면 오른쪽 깜빡이(후진).", "R"], ["핸들 {R}으로 끝까지, 차체가 45°가 되면 멈춤.", "D"],
      ["핸들 {L}으로 끝까지, 앞 범퍼가 앞차를 빠져나가며 들어가기.", "<"], ["연석과 나란해지면 핸들 가운데, 브레이크.", "A"]],
    parallel_none: [["뒷바퀴가 주차선 앞 끝에 오면 오른쪽 깜빡이(후진).", "R"], ["핸들 {R}으로 끝까지, 차체가 45°가 되면 멈춤.", "D"],
      ["핸들 {L}으로 끝까지, 앞 범퍼가 주차선 안으로 들어가며.", "<"], ["연석과 나란해지면 핸들 가운데, 브레이크.", "A"]],
    angled: [["왼쪽 깜빡이(전진), 핸들은 가운데.", "F"], ["칸 입구가 {R} 사이드미러 옆에 오면 핸들을 {R}으로 끝까지.", ">"],
      ["차가 칸과 나란해지면(45°) 핸들 가운데, 그대로 직진.", "A"], ["앞 벽 0.5 m, 빨간선이 닿기 전에 브레이크.", "A"]],
  };
  const FLIP = { ">": "<", "<": ">" };

  const CAR_COLORS = ["#e8e8ea", "#c9ccd2", "#2b2d33", "#6b6f78", "#3d5a99", "#8a2b2b", "#d8d2c2", "#4a5d4f"];
  /** 난이도별 흔들기 폭: 시작 위치(m)·각(°)·옆 차 위치(m). 고급일수록 '공식 자리'에서 멀다. */
  const JITTER = { "초급": { x: 0.15, y: 0.1, h: 2, nb: 0.12 }, "중급": { x: 0.25, y: 0.15, h: 3, nb: 0.18 }, "고급": { x: 0.4, y: 0.2, h: 4, nb: 0.25 } };

  /** 스테이지/자유 옵션 → 시나리오. opt: {kind, tier, neighbors, side, hazards, hazType?, lane?, no?}
   *  seed 를 주면 매번 다른 상황(시작 자세·옆 차 위치·색·지하/지상). 없으면 정본 자리(테스트·공식 검증 기준). */
  function build(spec, opt, seed) {
    const T = TIERS[opt.tier], SW = opt.slotW || T.slotW, SL = T.slotL, R = minR(spec), ro = spec.rearOverhang;
    const rng = seed === undefined || seed === null ? () => 0.5 : lcg(seed), J = JITTER[opt.tier], rnd = (a) => (rng() - 0.5) * 2 * a;
    const env = seed === undefined || seed === null ? "underground" : (rng() < 0.6 ? "underground" : "outdoor");
    const colorOf = () => CAR_COLORS[Math.floor(rng() * CAR_COLORS.length) % CAR_COLORS.length];
    const lane = opt.lane || (opt.kind === "angled" ? T.aLane : (opt.kind === "perp_fwd" && opt.neighbors !== "none" ? Math.max(T.lane, 6.5) : T.lane));
    const obs = [], marks = []; let slot, start, forward = false, coachKey = opt.kind, formulaX = 0;
    const car = (c, h, label) => { const u = [Math.cos(h), Math.sin(h)], v = [-Math.sin(h), Math.cos(h)], dl = rnd(J.nb * 1.6), dw = rnd(J.nb * 0.6);
      return { b: obb([c[0] + u[0] * dl + v[0] * dw, c[1] + u[1] * dl + v[1] * dw], h, 2.3, 0.9), label, car: true, color: colorOf() }; };
    if (opt.kind === "perp_rev" || opt.kind === "perp_fwd") {
      forward = opt.kind === "perp_fwd";
      if (forward && opt.neighbors !== "none") coachKey = "perp_fwd_tight";
      slot = obb([0, -SL / 2], PI / 2, SL / 2, SW / 2);
      for (const k of [-2, -1, 1, 2]) marks.push(obb([k * SW, -SL / 2], PI / 2, SL / 2, SW / 2));
      obs.push({ b: obb([0, -SL - 0.4], 0, 9, 0.25), label: "뒤 벽", car: false });
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
      start = forward ? { x: -(R + 1.0 + T.offset), y: y0, h: 0 } : { x: R + 0.15 + T.offset, y: y0, h: 0 };
      // 공식대로라면 핸들을 끝까지 돌리는 뒷차축 x: 후면 = R, 전면(옆 차 없음) = −R, 전면(옆 차) = −R + 2.5(지나쳐서 꺾고 한 번 뺀다)
      formulaX = forward ? (opt.neighbors === "none" ? -R : -R + 2.5) : R;
    } else if (opt.kind === "parallel") {
      const gap = spec.length + T.gapExtra, W = 2.3, cy = -W / 2;                 // 평행 칸 폭 2.3(법정 2.0 + 연석 여유)
      slot = obb([0, cy], 0, gap / 2, W / 2);
      obs.push({ b: obb([0, -W - 0.15], 0, 12, 0.15), label: "연석", car: false });
      if (opt.neighbors !== "none") { obs.push(car([gap / 2 + 2.4, cy], 0, "앞차")); obs.push(car([-(gap / 2 + 2.4), cy], 0, "뒷차")); }
      else coachKey = "parallel_none";
      // 45° 두 번 꺾는 공식: 옆으로 2·R(1−cos45°) 만큼 들어간다 → 시작 y 를 거기에 맞춘다. 뒷차축 = 앞차 뒷범퍼 선(= 칸 앞 끝).
      start = { x: gap / 2 + T.offset, y: cy + 2 * R * (1 - Math.cos(PI / 4)), h: 0 };
      formulaX = gap / 2;
    } else { // angled 45°, 전진 진입
      forward = true;
      const h = -PI / 4, u = [Math.cos(h), Math.sin(h)], cy = -(SL / 2 + SW / 2) * Math.SQRT1_2;   // 칸 윗 모서리가 y=0 (차로 가장자리)
      slot = obb([0, cy], h, SL / 2, SW / 2);
      const pitch = SW * Math.SQRT2;
      for (const k of [-2, -1, 1, 2]) marks.push(obb([k * pitch, cy], h, SL / 2, SW / 2));
      if (opt.neighbors !== "none") { obs.push(car([-pitch, cy], h, "옆 차")); obs.push(car([pitch, cy], h, "옆 차")); if (seed !== undefined && seed !== null) for (const k of [-2, 2]) if (rng() < 0.7) obs.push(car([k * pitch, cy], h, "주차 차량")); }
      obs.push({ b: obb([0, cy - (SL / 2) * Math.SQRT1_2 - SW / 2 * Math.SQRT1_2 - 0.3], 0, 14, 0.25), label: "뒤 벽", car: false });
      obs.push({ b: obb([0, lane + 0.3], 0, 14, 0.3), label: "벽", car: false });
      // 45° 가득 꺾으면 y 가 R(1−cos45°) 내려가고 x 가 R·sin45° 나아간다. 그 끝이 칸 축 위에 오도록 꺾는 점을 잡고 1 m 전에서 시작.
      const y0 = lane / 2 + 0.2, yEnd = y0 - R * (1 - Math.SQRT1_2);
      const sAxis = -(yEnd - cy) / Math.SQRT1_2, xEnd = sAxis * Math.SQRT1_2;     // 칸 축 위 점 (cx=0 기준)
      start = { x: xEnd - R * Math.SQRT1_2 - 1.0 - T.offset, y: y0, h: 0 };
      formulaX = xEnd - R * Math.SQRT1_2;
    }
    if (seed !== undefined && seed !== null) start = { x: start.x + rnd(J.x), y: start.y + rnd(J.y), h: start.h + rad(rnd(J.h)) };
    let coach = COACH[coachKey].map(([t, c]) => [t, c]);
    const flipSide = opt.side === "L";
    if (flipSide) { slot = mirrorObb(slot); obs.forEach(o => o.b = mirrorObb(o.b)); for (let i = 0; i < marks.length; i++) marks[i] = mirrorObb(marks[i]);
      start = { x: start.x, y: -start.y, h: -start.h }; coach = coach.map(([t, c]) => [t, FLIP[c] || c]); }
    const Rw = flipSide ? "왼쪽" : "오른쪽", Lw = flipSide ? "오른쪽" : "왼쪽";
    coach = coach.map(([t, c]) => [t.replace(/\{R\}/g, Rw).replace(/\{L\}/g, Lw), c]);
    const walls = obs.filter(o => o.b.hx > 5), rest = [slot, ...obs.filter(o => o.b.hx <= 5).map(o => o.b), ...marks, body(spec, start)];
    const bb = bboxOf(rest, 0.4);
    if (walls.length) { let y0 = bb.c[1] - bb.hy, y1 = bb.c[1] + bb.hy; walls.forEach(w => corners(w.b).forEach(p => { y0 = Math.min(y0, p[1]); y1 = Math.max(y1, p[1]); })); bb.c = [bb.c[0], (y0 + y1) / 2]; bb.hy = (y1 - y0) / 2 + 0.2; }
    return { title: opt.title || stageTitle(opt), kind: opt.kind, tier: opt.tier, forward, slot, obs, marks, start, coach,
      tol: T.tol, hazards: opt.hazards || 0, hazType: opt.hazType || null, bounds: bb, tierNote: T.note, env, seed: seed ?? null, lane, formulaX };
  }
  function collides(sc, b) { const o = sc.obs.find(o => intersects(o.b, b)); if (o) return o;
    // 연습 구역(화면 범위 + 1.5 m) 밖도 막는다 — 끝없이 후진해 사라지지 않게
    const B = sc.bounds; if (!inside(b, obb(B.c, 0, B.hx + 1.5, B.hy + 1.5))) return { label: "연습 구역 끝", car: false }; return null; }
  function headingDiff(sc, h) { const dh = ((h - sc.slot.h) % PI + PI) % PI; return Math.min(dh, PI - dh); }
  function parked(sc, b, h) { return headingDiff(sc, h) < rad(sc.tol) && inside(b, obb(sc.slot.c, sc.slot.h, sc.slot.hx + 0.3, sc.slot.hy + 0.15)); }

  // ---- 엔진 (Kotlin GameEngine 과 같은 규칙) ----
  // 입력: {steer, signal: "OFF"|"LEFT"|"RIGHT", brake, gearP}. 깜빡이 왼쪽 = 전진 0.6 m/s, 오른쪽 = 후진, 브레이크·OFF·P 아님 = 정지.
  function lcg(seed) { let s = (seed >>> 0) || 1; return () => (s = (s * 1664525 + 1013904223) >>> 0) / 4294967296; }
  class Engine {
    constructor(spec, sc, seed) { this.spec = spec; this.speed = 0.6; this.limit = 300; this.seed = seed || 7; this.load(sc); }
    load(sc) { this.sc = sc; this.reset(); }
    reset() {
      const S = this; S.pose = { ...S.sc.start }; S.phase = "READY"; S.hits = 0; S.elapsed = 0; S.trace = []; S.coach = 0; S.stepAt = 0;
      S.hint = S.sc.coach[0][0]; S.lastV = 0; S.lastReverse = !S.sc.forward; S.travelled = 0;
      S.hazard = null; S.hazStats = { total: 0, hit: 0, avoided: 0 }; S.rng = lcg(S.seed);
      S.firstLock = null; S.dirChanges = 0; S.steerFlips = 0; S._lastDir = 0; S._lastSteerSign = 0;
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
      if (S.phase === "PARKED") return S.snap(inp);
      if (!inp.gearP) { S.hint = "기어를 P에 두면 계속할 수 있어요"; S.lastV = 0; return S.snap(inp); }
      const v = inp.brake ? 0 : inp.signal === "LEFT" ? S.speed : inp.signal === "RIGHT" ? -S.speed : 0;
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
        if (d <= hz.r + 0.1) { if (v !== 0) { S.hits++; S.hazStats.hit++; S.phase = "HIT"; S.hint = `${hz.emoji} ${hz.label}과 닿았어요! 사람이 보이면 먼저 멈춰요.`; S.hazard = null; S.lastV = 0; return S.snap(inp); } }
        else if (v === 0 && d < 3.0) hz.wasStopped = true;
        if (S.hazard && hz.life <= 0) { S.hazard = null; S.hazStats.avoided++; S.hint = hz.wasStopped ? "잘 멈췄어요. 이어서 가세요." : "지나갔어요. 다음엔 보이는 순간 멈춰요."; S.stepAt = S.elapsed; }
      } else if (v !== 0 && S.hazPlan.length && S.travelled >= S.hazPlan[0].at) S.spawnHazard(S.hazPlan.shift(), v);

      if (v !== 0) {
        if (S.phase === "READY") S.phase = "DRIVING";
        const nx = step(S.spec, S.pose, inp.steer, v, dt), b = body(S.spec, nx), hit = collides(S.sc, b);
        if (hit) { if (S.phase !== "HIT") S.hits++; S.phase = "HIT"; S.hint = `${hit.label}에 닿았어요. 반대로 빼서 다시.`; }
        else { S.pose = nx; S.phase = "DRIVING"; S.elapsed += dt; S.travelled += Math.abs(v) * dt; S.trace.push({ t: S.elapsed, steer: inp.steer, v }); S.lastReverse = v < 0;
          if (!S.firstLock && Math.abs(inp.steer) > 500) S.firstLock = { ...S.pose, travelled: S.travelled };
          const dir = Math.sign(v); if (S._lastDir && dir !== S._lastDir) S.dirChanges++; S._lastDir = dir;
          const ss = Math.abs(inp.steer) > 150 ? Math.sign(inp.steer) : 0; if (ss && S._lastSteerSign && ss !== S._lastSteerSign) S.steerFlips++; if (ss) S._lastSteerSign = ss;
          if (!S.hazard) S.advanceCoach(inp);
          if (parked(S.sc, b, S.pose.h)) { S.phase = "PARKED"; S.hint = S.doneText(); } }
      } else if (S.phase === "HIT") S.phase = "DRIVING";
      S.lastV = v;
      if (S.elapsed > S.limit) S.hint = "5분이 지났어요. 핸들과 타이어를 위해 오늘은 여기까지.";
      return S.snap(inp);
    }
    advanceCoach(inp) {
      const S = this, c = S.sc.coach, cond = c[S.coach] && c[S.coach][1], st = inp.steer, hd = headingDiff(S.sc, S.pose.h);
      let go = false;
      switch (cond) {
        case "R": go = inp.signal === "RIGHT"; break; case "F": go = inp.signal === "LEFT"; break;
        case ">": go = st > 400; break; case "<": go = st < -400; break;
        case "0": go = Math.abs(st) < 200 && S.elapsed - S.stepAt > 1.0; break;
        case "A": go = hd < rad(12); break; case "D": go = hd > rad(35) && hd < rad(55); break;
      }
      if (go && S.coach < c.length - 1) { S.coach++; S.stepAt = S.elapsed; }
      S.hint = c[Math.min(S.coach, c.length - 1)][0];
    }
    /** 끝난 뒤 피드백: 공식과 비교(핸들을 처음 끝까지 돌린 자리), 보정 횟수, 핸들 뒤집기 */
    review() {
      const S = this, out = [], sc = S.sc;
      if (S.firstLock && sc.formulaX !== undefined) { const d = (S.firstLock.x - sc.formulaX) * (sc.forward ? 1 : -1) * (sc.kind === "parallel" ? -1 : 1);
        // d > 0 = 공식 자리보다 더 지나쳐서(늦게) 꺾음
        out.push(Math.abs(d) < 0.3 ? "핸들을 끝까지 돌린 자리가 공식 자리와 거의 같았어요." : d > 0 ? `핸들을 끝까지 돌린 자리가 공식 자리보다 ${d.toFixed(1)} m 늦었어요.` : `핸들을 끝까지 돌린 자리가 공식 자리보다 ${(-d).toFixed(1)} m 일렀어요.`); }
      else if (!S.firstLock) out.push("핸들을 끝까지 돌리지 않고 들어갔어요. 좁은 곳에서는 가득 꺾어야 회전반경이 가장 작아요.");
      const expect = sc.kind === "perp_fwd" && sc.coach.length === 5 ? 2 : 0;
      out.push(S.dirChanges <= expect ? (expect ? "전후진 한 번 빼기만으로 들어갔어요." : "전후진 전환 없이 한 번에 들어갔어요.") : `전후진을 ${S.dirChanges}번 바꿨어요(보정 ${S.dirChanges - expect}회).`);
      if (S.steerFlips > (sc.kind === "parallel" ? 1 : expect ? 2 : 0) + 1) out.push(`핸들 방향을 ${S.steerFlips}번 뒤집었어요. 한 번 정한 방향을 끝까지 쓰는 게 깔끔해요.`);
      return out;
    }
    doneText() { const h = this.hazStats, base = this.hits === 0 ? "쏙! 한 번도 부딪히지 않았어요." : `쏙! ${this.hits}번 부딪혔어요. 기록에서 어디서 각을 잘못 잡았나 보세요.`;
      return h.total ? `${base} 돌발 ${h.total}회 중 ${h.avoided}회 멈춤${h.hit ? ` · ${h.hit}회 사고` : ""}.` : base; }
    snap(inp) { const S = this; return { phase: S.phase, pose: S.pose, body: body(S.spec, S.pose), steer: inp.steer, v: S.lastV, hint: S.hint, hits: S.hits, elapsed: S.elapsed,
      coach: S.coach, coachN: S.sc.coach.length, reverse: S.lastV === 0 ? S.lastReverse : S.lastV < 0, hazard: S.hazard, hazStats: S.hazStats, flash: S.flash,
      coachText: S.sc.coach[Math.min(S.coach, S.sc.coach.length - 1)][0], dirChanges: S.dirChanges }; }
  }

  return { SPECS, TIERS, KINDS, NEIGH, SIDES, HAZ, HAZ_TYPES, STAGES, CAR_COLORS, JITTER, stageTitle, build, collides, parked, headingDiff,
    obb, corners, intersects, inside, distPoint, bboxOf, ratio, frontOverhang, minR, wheelRad, step, body, rearCorners, frontCorners, path, bumper, Engine };
});
