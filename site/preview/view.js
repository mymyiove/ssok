/* 쏙 SSOK — 화면 그리기(브라우저 시안). 후방/전방 카메라 재현(핀홀 + 어안 왜곡 + 지하주차장 천장·조명 + 입체 차·기둥 + 노이즈),
 * 좌·우 사이드미러(거울상, 내 차 옆구리가 안쪽에 보임), 어라운드뷰 탑뷰(아스팔트·주차선·위에서 본 차 그림). 수식은 sim.js 와 같다. Kotlin render/ 로 옮길 때의 기준. */
(function (root, factory) {
  if (typeof module === "object" && module.exports) module.exports = factory();
  else root.SSOK_VIEW = factory();
})(typeof self !== "undefined" ? self : this, function () {
  const PI = Math.PI, rad = d => d * PI / 180;

  // ---- 색 도우미 ----
  function hexRgb(h) { const n = parseInt(h.slice(1), 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255]; }
  function shade(hex, f, a) { const [r, g, b] = hexRgb(hex).map(v => Math.max(0, Math.min(255, Math.round(v * f)))); return a === undefined ? `rgb(${r},${g},${b})` : `rgba(${r},${g},${b},${a})`; }

  // ---- 노이즈(카메라 질감·아스팔트): 한 번 만들어 패턴으로 ----
  let noiseCv = null;
  function noise(g) {
    if (!noiseCv) { noiseCv = document.createElement("canvas"); noiseCv.width = noiseCv.height = 160; const n = noiseCv.getContext("2d"), img = n.createImageData(160, 160);
      for (let i = 0; i < img.data.length; i += 4) { const v = 128 + (Math.random() - 0.5) * 90; img.data[i] = img.data[i + 1] = img.data[i + 2] = v; img.data[i + 3] = 255; } n.putImageData(img, 0, 0); }
    return g.createPattern(noiseCv, "repeat");
  }

  // ===================== 카메라(후방/전방/사이드미러) =====================
  // 후방카메라: 뒷범퍼 중앙, 높이 0.95, 아래로 25°, 광각 140°, 거울상. 전방: 앞범퍼, 거울상 아님. 사이드미러: A필러 옆, 높이 1.0, 뒤를 보며 바깥쪽 15°, 화각 50°, 거울상.
  const REAR = { h: 0.95, pitch: 25 * PI / 180, hfov: 140 * PI / 180, near: 0.12, fish: 0.95, mirror: true };
  const FRONT = { h: 0.95, pitch: 25 * PI / 180, hfov: 140 * PI / 180, near: 0.12, fish: 0.95, mirror: false };
  const SIDE = { h: 1.0, pitch: 8 * PI / 180, hfov: 78 * PI / 180, near: 0.12, fish: 0.5, mirror: true };   // 볼록 거울 + 바깥 18°: 후진 때 옆 칸 차·주차선이 들어온다
  const SIDE_DOWN = Object.assign({}, SIDE, { pitch: 15 * PI / 180 });   // R 연동 하향 미러(뒷바퀴·주차선)

  class Camera {
    /** pos: 월드 [x,y], yaw: 보는 방향(rad), P: 설정 */
    constructor(S, pos, yaw, P, W, H) { this.S = S; this.c = pos; this.yaw = yaw; this.P = P; this.W = W; this.H = H; this.f = (W / 2) / Math.tan(P.hfov / 2);
      this.fw = [Math.cos(yaw), Math.sin(yaw)]; this.rt = [Math.sin(yaw), -Math.cos(yaw)]; }
    /** 월드 (x,y,z) → 카메라 좌표 [right, up, depth] */
    toCam(p, z0) {
      const dx = p[0] - this.c[0], dy = p[1] - this.c[1], P = this.P;
      const along = dx * this.fw[0] + dy * this.fw[1]; let right = dx * this.rt[0] + dy * this.rt[1]; if (P.mirror) right = -right;
      const yc = (z0 || 0) - P.h;
      return [right, along * Math.sin(P.pitch) + yc * Math.cos(P.pitch), along * Math.cos(P.pitch) - yc * Math.sin(P.pitch)];
    }
    toScreen(cc) {
      let nx = (this.f * cc[0] / cc[2]) / (this.W / 2), ny = (this.f * cc[1] / cc[2]) / (this.W / 2);
      const r = Math.hypot(nx, ny); if (r > 1e-6 && this.P.fish > 0) { const c = this.P.fish, k = (Math.atan(r * c) / c) / r; nx *= k; ny *= k; }
      return [this.W / 2 + nx * this.W / 2, this.H / 2 - ny * this.W / 2];
    }
    clipNear(poly) { const out = [], n = this.P.near; for (let i = 0; i < poly.length; i++) { const a = poly[i], b = poly[(i + 1) % poly.length], ia = a[2] >= n, ib = b[2] >= n;
      if (ia) out.push(a); if (ia !== ib) { const t = (n - a[2]) / (b[2] - a[2]); out.push([a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, n]); } } return out; }
    poly(pts, z, sub) { const w = []; const m = sub || 6;
      for (let i = 0; i < pts.length; i++) { const a = pts[i], b = pts[(i + 1) % pts.length]; for (let j = 0; j < m; j++) { const t = j / m; w.push([a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t]); } }
      return this.clipNear(w.map(p => this.toCam(p, z))).map(cc => this.toScreen(cc)); }
    quad(a, b, za, zb) { const w = []; const m = 6; for (let j = 0; j <= m; j++) { const t = j / m; w.push([a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t]); }
      const cam = w.map(p => this.toCam(p, za)).concat(w.slice().reverse().map(p => this.toCam(p, zb)));
      return this.clipNear(cam).map(cc => this.toScreen(cc)); }
    /** 아래 변 a→b(높이 za) 와 위 변 c→d(높이 zb) 가 다른 사각형 — 캐빈 유리처럼 기운 면 */
    quad2(a, b, za, c, d, zb) { const m = 6, lo = [], hi = []; for (let j = 0; j <= m; j++) { const t = j / m; lo.push([a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t]); hi.push([c[0] + (d[0] - c[0]) * t, c[1] + (d[1] - c[1]) * t]); }
      return this.clipNear(lo.map(p => this.toCam(p, za)).concat(hi.reverse().map(p => this.toCam(p, zb)))).map(cc => this.toScreen(cc)); }
    path(pts, z) { const n = this.P.near, cam = pts.map(p => this.toCam(p, z || 0)), segs = []; for (let i = 1; i < cam.length; i++) { const a = cam[i - 1], b = cam[i], ia = a[2] >= n, ib = b[2] >= n; if (!ia && !ib) continue;
      let p = a, q = b; if (ia !== ib) { const t = (n - a[2]) / (b[2] - a[2]); const m = [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, n]; if (ia) q = m; else p = m; } segs.push([this.toScreen(p), this.toScreen(q)]); } return segs; }
    depth(p) { return this.toCam(p, 0)[2]; }
    light(a, b) { const nx = (b[1] - a[1]), ny = -(b[0] - a[0]), len = Math.hypot(nx, ny) || 1; const mx = (a[0] + b[0]) / 2 - this.c[0], my = (a[1] + b[1]) / 2 - this.c[1], ml = Math.hypot(mx, my) || 1;
      const d = -(nx * mx + ny * my) / (len * ml); return 0.55 + 0.45 * Math.max(0, d); }
  }

  function fillPoly(g, pts, style, stroke, lw) { if (pts.length < 3) return; g.beginPath(); pts.forEach((p, i) => i ? g.lineTo(p[0], p[1]) : g.moveTo(p[0], p[1])); g.closePath(); if (style) { g.fillStyle = style; g.fill(); } if (stroke) { g.strokeStyle = stroke; g.lineWidth = lw || 1; g.stroke(); } }
  function strokeSegs(g, segs, style, lw, dash) { if (!segs.length) return; g.beginPath(); segs.forEach(([a, b]) => { g.moveTo(a[0], a[1]); g.lineTo(b[0], b[1]); }); g.setLineDash(dash || []); g.strokeStyle = style; g.lineWidth = lw; g.lineCap = "round"; g.stroke(); g.setLineDash([]); }

  function drawBox(cam, g, b, z0, z1, baseHex, topHex, S) {
    const cs = S.corners(b);
    const faces = [0, 1, 2, 3].map(i => [cs[i], cs[(i + 1) % 4]]).sort((f1, f2) => (cam.depth(f2[0]) + cam.depth(f2[1])) - (cam.depth(f1[0]) + cam.depth(f1[1])));
    for (const [a, c] of faces) fillPoly(g, cam.quad(a, c, z0, z1), shade(baseHex, cam.light(a, c)));
    fillPoly(g, cam.poly(cs, z1, 3), shade(topHex || baseHex, 1.05));
  }
  /** 차(현대차 꼴): 차체(0.25~보닛 높이) + 기운 캐빈(세단은 뒤로 앉고 SUV 는 길고 높다) + 앞 가로 한 줄 램프(심리스 호라이즌)·뒤 빨간 띠 + 바퀴·미러 + 그림자.
   *  model = SPECS 키(아반떼·그랜저·투싼). 치수는 b(OBB) 가 정한다. */
  function drawCar3D(cam, g, b, hex, S, blink, model) {
    const M = (S.SPECS && S.SPECS[model]) || S.SPECS.GRANDEUR, suv = M.shape === "suv", hoodH = M.hoodH, roofH = M.roofH;
    const u = [Math.cos(b.h), Math.sin(b.h)], v = [-Math.sin(b.h), Math.cos(b.h)];
    const at = (du, dv) => [b.c[0] + u[0] * b.hx * du + v[0] * b.hy * dv, b.c[1] + u[1] * b.hx * du + v[1] * b.hy * dv];
    for (const [du, dv] of [[0.6, 0.86], [0.6, -0.86], [-0.6, 0.86], [-0.6, -0.86]]) drawBox(cam, g, S.obb(at(du, dv), b.h, 0.34, 0.11), 0.0, 0.64, "#15161a", "#1c1d22", S);
    fillPoly(g, cam.poly(S.corners(S.obb(b.c, b.h, b.hx + 0.15, b.hy + 0.2)), 0.005, 3), "rgba(0,0,0,.35)");
    // 차체(약간 안으로 기운 어깨): 바닥 폭 그대로, 보닛 높이에서 4% 좁게
    const cs = S.corners(b), faces = [0, 1, 2, 3].map(i => [cs[i], cs[(i + 1) % 4]]).sort((f1, f2) => (cam.depth(f2[0]) + cam.depth(f2[1])) - (cam.depth(f1[0]) + cam.depth(f1[1])));
    const top = S.corners(S.obb(b.c, b.h, b.hx * 0.99, b.hy * 0.96));
    for (const [a, c] of faces) { const i = cs.indexOf(a); fillPoly(g, cam.quad2(a, c, 0.25, top[i], top[(i + 1) % 4], hoodH), shade(hex, cam.light(a, c))); }
    fillPoly(g, cam.poly(top, hoodH, 3), shade(hex, 1.08));
    // 캐빈: 아래 변(보닛 높이)은 넓고, 위 변(지붕)은 앞뒤로 줄어든다 → 앞유리·뒷유리 경사
    const cab = suv ? { f0: 0.22, r0: -0.98, f1: 0.02, r1: -0.86, w0: 0.9, w1: 0.8 } : { f0: 0.3, r0: -0.72, f1: 0.02, r1: -0.48, w0: 0.88, w1: 0.74 };
    const lo = [at(cab.f0, cab.w0), at(cab.f0, -cab.w0), at(cab.r0, -cab.w0), at(cab.r0, cab.w0)], hi = [at(cab.f1, cab.w1), at(cab.f1, -cab.w1), at(cab.r1, -cab.w1), at(cab.r1, cab.w1)];
    const cfaces = [0, 1, 2, 3].sort((i, j) => (cam.depth(lo[j]) + cam.depth(lo[(j + 1) % 4])) - (cam.depth(lo[i]) + cam.depth(lo[(i + 1) % 4])));
    for (const i of cfaces) { const a = lo[i], c = lo[(i + 1) % 4]; const glass = i === 0 || i === 2 ? "#1b2633" : "#223040"; fillPoly(g, cam.quad2(a, c, hoodH, hi[i], hi[(i + 1) % 4], roofH), shade(glass, cam.light(a, c) * 1.1)); }
    fillPoly(g, cam.poly(hi, roofH, 3), shade(hex, 1.0));
    if (suv) for (const dv of [0.62, -0.62]) fillPoly(g, cam.quad(at(cab.f1 - 0.02, dv), at(cab.r1 + 0.02, dv), roofH, roofH + 0.05), "#2a2c31");   // 루프랙
    // 램프: 앞 = 폭 전체 가로 한 줄(심리스 호라이즌 램프, 아반떼는 두 토막) · 뒤 = 빨간 띠, 비상등이면 양 끝 주황
    const barZ = hoodH - 0.12, segs = model === "AVANTE" ? [[-0.92, -0.3], [0.3, 0.92]] : [[-0.92, 0.92]];
    for (const [s0, s1] of segs) fillPoly(g, cam.quad(at(1.0, s0), at(1.0, s1), barZ, barZ + 0.05), "#f4f6ff");
    fillPoly(g, cam.quad(at(-1.0, -0.92), at(-1.0, 0.92), barZ + 0.02, barZ + 0.09), "#e0342c");
    for (const side of [1, -1]) for (const dv of [0.86, -0.86]) { const c = at(side, dv); const a = [c[0] - v[0] * 0.14, c[1] - v[1] * 0.14], d = [c[0] + v[0] * 0.14, c[1] + v[1] * 0.14];
      fillPoly(g, cam.quad(a, d, barZ - 0.04, barZ + 0.12), blink ? "#ffb340" : side === 1 ? "#dfe9ff" : "#c8221c"); }
    for (const dv of [1.0, -1.0]) { const c = at(cab.f0 - 0.04, dv * 1.06); drawBox(cam, g, S.obb(c, b.h, 0.08, 0.06), hoodH + 0.08, hoodH + 0.2, shade(hex, 0.9), shade(hex, 1.05), S);
      if (blink) fillPoly(g, cam.quad(at(0.3, dv), at(0.45, dv), hoodH - 0.2, hoodH - 0.1), "#ffb340"); }
  }
  /** 사이드미러에 비치는 내 차 옆구리(PM 2026-10-26 '더 현실적으로'): 상자 차 대신 실제로 거울에 보이는 것만 — 사이드 실·도어 패널(어깨선 하이라이트)·도어 이음선·손잡이·창 띠(B·C 필러)·뒷바퀴 아치와 타이어·휠·뒤로 돌아간 램프. side = 보는 거울 쪽(+1 오른쪽). */
  function drawOwnSide(cam, g, b, hex, S, blink, model, side) {
    const M = (S.SPECS && S.SPECS[model]) || S.SPECS.GRANDEUR, suv = M.shape === "suv", hoodH = M.hoodH, roofH = M.roofH;
    const u = [Math.cos(b.h), Math.sin(b.h)], v = [-Math.sin(b.h), Math.cos(b.h)], dvS = -side;   // 오른쪽 거울 = 차 오른쪽 면(dv < 0)
    const at = (du, dv) => [b.c[0] + u[0] * b.hx * du + v[0] * b.hy * dv, b.c[1] + u[1] * b.hx * du + v[1] * b.hy * dv];
    const P3 = pts => cam.clipNear(pts.map(([x, y, z]) => cam.toCam([x, y], z))).map(cc => cam.toScreen(cc));
    const band = (du0, du1, dv0, z0, dv1, z1, col) => fillPoly(g, cam.quad2(at(du0, dvS * dv0), at(du1, dvS * dv0), z0, at(du0, dvS * dv1), at(du1, dvS * dv1), z1), col);
    const F = 0.98, R = -1.0, cab = suv ? { f0: 0.22, r0: -0.98 } : { f0: 0.3, r0: -0.72 };
    const sh = hoodH - 0.1;   // 어깨선 높이
    fillPoly(g, cam.poly(S.corners(S.obb(b.c, b.h, b.hx + 0.15, b.hy + 0.2)), 0.005, 3), "rgba(0,0,0,.35)");   // 바닥 그림자
    // 뒷면(멀리): 범퍼·빨간 램프 띠 — 거울에서 뒤 모서리가 돌아 보인다
    fillPoly(g, cam.quad(at(R, -0.92), at(R, 0.92), 0.25, hoodH), shade(hex, 0.72)); fillPoly(g, cam.quad(at(R, -0.92), at(R, 0.92), hoodH - 0.1, hoodH - 0.03), "#e0342c");
    // 옆면: 실(안으로 들어감) → 도어(바깥으로 벌어짐, 아래 어둡고 위 밝게) → 어깨선 → 창 띠(안으로 기움)
    band(F, R, 0.96, 0.22, 1.0, 0.42, shade(hex, 0.5));
    band(F, R, 1.0, 0.42, 1.02, 0.62, shade(hex, 0.78)); band(F, R, 1.02, 0.62, 1.035, sh - 0.08, shade(hex, 0.96)); band(F, R, 1.035, sh - 0.08, 1.035, sh, shade(hex, 1.22));
    band(F, R, 1.035, sh, 1.0, hoodH, shade(hex, 1.0));
    band(F, R, 1.0, 0.405, 1.0, 0.425, shade(hex, 0.35));   // 실 몰딩 선
    // 도어 이음선 둘(앞문 뒤 · 뒷문 뒤)과 뒷문 손잡이
    for (const du of [0.08, cab.r0 + 0.02]) band(du, du - 0.014, 1.04, 0.44, 1.04, sh - 0.02, shade(hex, 0.45));
    band(-0.22, -0.4, 1.045, sh - 0.3, 1.045, sh - 0.24, shade(hex, 0.6)); band(-0.22, -0.4, 1.05, sh - 0.3, 1.05, sh - 0.285, shade(hex, 1.3));
    // 창 띠: 어깨선 위 유리 + B 필러 + C 필러(뒤로 기움), 세단은 트렁크 뒤로 유리가 없다
    const g0 = Math.min(F, cab.f0 + 0.04), g1 = cab.r0; band(g0, g1, 1.0, hoodH, 0.94, roofH - 0.02, "#1b2633");
    fillPoly(g, cam.quad2(at(g0 - 0.02, dvS * 1.0), at(g0 + 0.02, dvS * 1.0), hoodH, at(g0 - 0.03, dvS * 0.95), at(g0 + 0.01, dvS * 0.95), roofH - 0.02, 1), "#0e1116");
    const bp = (cab.f0 + cab.r0) / 2 - 0.06; band(bp + 0.02, bp - 0.02, 1.005, hoodH, 0.95, roofH - 0.02, "#0e1116");
    fillPoly(g, cam.quad2(at(g1 + 0.12, dvS * 1.0), at(g1, dvS * 1.0), hoodH, at(g1 + 0.02, dvS * 0.95), at(g1 - 0.06, dvS * 0.95), roofH - 0.02), shade(hex, 0.85));
    band(F, R, 0.94, roofH - 0.02, 0.9, roofH, shade(hex, 1.1));   // 지붕 모서리
    // 뒷바퀴: 아치(어두운 구멍) → 타이어 → 휠
    const rr = 0.36 / b.hx, zc = 0.34, arch = [], tire = [], rim = [];
    for (let i = 0; i <= 14; i++) { const t = i / 14 * PI; arch.push([...at(-0.6 + Math.cos(t) * rr * 1.15, dvS * 1.01), zc + Math.sin(t) * 0.36 * 1.15]); }
    arch.push([...at(-0.6 - rr * 1.15, dvS * 1.01), 0.12]); arch.push([...at(-0.6 + rr * 1.15, dvS * 1.01), 0.12]);
    for (let i = 0; i < 20; i++) { const t = i / 20 * 2 * PI; tire.push([...at(-0.6 + Math.cos(t) * rr, dvS * 0.985), zc + Math.sin(t) * 0.34]); rim.push([...at(-0.6 + Math.cos(t) * rr * 0.62, dvS * 0.99), zc + Math.sin(t) * 0.34 * 0.62]); }
    fillPoly(g, P3(arch), "#101114"); fillPoly(g, P3(tire), "#1c1d22"); fillPoly(g, P3(rim), "#8d9199"); fillPoly(g, P3(rim.map((p, i) => i % 2 ? p : [p[0], p[1], zc + (p[2] - zc) * 0.3])), "#5c6068");
    // 옆 램프(뒤쪽 모서리, 비상등이면 주황) · 뒤 범퍼 아래 어둡게
    fillPoly(g, cam.quad(at(R + 0.02, dvS * 1.0), at(R + 0.14, dvS * 1.0), hoodH - 0.14, hoodH - 0.02), blink ? "#ffb340" : "#c8221c");
    if (blink) fillPoly(g, cam.quad(at(0.3, dvS * 1.04), at(0.45, dvS * 1.04), hoodH - 0.2, hoodH - 0.1), "#ffb340");
  }
  function drawPillar(cam, g, b, S) {
    drawBox(cam, g, b, 0.0, 2.6, "#9a978f", "#a7a49c", S);
    const cs = S.corners(b);
    for (let i = 0; i < 4; i++) { const a = cs[i], c = cs[(i + 1) % 4]; if (cam.depth(a) < 0 && cam.depth(c) < 0) continue;
      fillPoly(g, cam.quad(a, c, 0.25, 1.05), shade("#f0c020", cam.light(a, c)));
      for (let k = 0; k < 3; k++) { const t0 = k / 3 + 0.08, t1 = k / 3 + 0.2; const p0 = [a[0] + (c[0] - a[0]) * t0, a[1] + (c[1] - a[1]) * t0], p1 = [a[0] + (c[0] - a[0]) * t1, a[1] + (c[1] - a[1]) * t1];
        fillPoly(g, cam.quad(p0, p1, 0.25, 1.05), shade("#15151a", cam.light(a, c))); }
    }
  }
  function drawWall(cam, g, o, S) {
    if (o.label === "연석") { drawBox(cam, g, o.b, 0.0, 0.15, "#8b8d93", "#9a9ca3", S); return; }
    drawBox(cam, g, o.b, 0.0, 2.6, "#86847d", "#8f8d86", S);
    const cs = S.corners(o.b); for (let i = 0; i < 4; i++) { const a = cs[i], c = cs[(i + 1) % 4]; fillPoly(g, cam.quad(a, c, 0.45, 0.6), shade("#e8b92a", cam.light(a, c) * 0.9)); }
  }
  /** 돌발 인물·카트(PM 2026-10-27 "글로만 말고 마인크래프트 사람처럼이라도"): 머리·몸통·팔 둘·다리 둘 상자, 걷는 동안 팔다리가 번갈아 흔들린다. 카트 = 바구니 + 손잡이 + 바퀴 */
  function drawHazard3D(cam, g, hz, pose, S) {
    const r = hz.r, h = hz.height, c = hz.pos, face = pose.h + PI / 2;
    const moving = !hz.wasStopped || (hz.pauseLeft !== undefined && hz.pauseLeft <= 0), sw = moving ? Math.sin(performance.now() / 150) * 0.32 : 0;
    const u = [Math.cos(face), Math.sin(face)], v = [-Math.sin(face), Math.cos(face)];
    const at = (dx, dy) => [c[0] + u[0] * dx + v[0] * dy, c[1] + u[1] * dx + v[1] * dy];
    fillPoly(g, cam.poly(S.corners(S.obb(c, face, r * 1.4, r * 1.4)), 0.005, 3), "rgba(0,0,0,.35)");
    if (hz.type === "cart") {
      drawBox(cam, g, S.obb(c, face, 0.45, 0.3), 0.3, 0.95, "#b9bcc4", "#d7dae0", S);                       // 바구니
      for (const [dx, dy] of [[0.36, 0.24], [0.36, -0.24], [-0.36, 0.24], [-0.36, -0.24]]) drawBox(cam, g, S.obb(at(dx, dy), face, 0.05, 0.05), 0.0, 0.14, "#1f2126", "#2a2d33", S);   // 바퀴
      drawBox(cam, g, S.obb(at(-0.5, 0), face, 0.04, 0.3), 0.95, 1.05, "#d8362f", "#e24a43", S);              // 손잡이
      return; }
    const kid = hz.type === "child", skin = "#e9b58f", shirt = kid ? "#f2c94c" : "#3b6fd1", pants = kid ? "#5a7bd1" : "#2c3140", shoe = "#1a1c22", hair = kid ? "#4a3328" : "#2b211c";
    const lw = r * 0.32, legH = h * 0.47, legX = r * 0.28;
    drawBox(cam, g, S.obb(at(sw * 0.35, legX), face, lw, lw), 0.0, legH, pants, pants, S); drawBox(cam, g, S.obb(at(-sw * 0.35, -legX), face, lw, lw), 0.0, legH, pants, pants, S);   // 다리
    drawBox(cam, g, S.obb(at(sw * 0.35, legX), face, lw * 1.1, lw * 1.1), 0.0, 0.07, shoe, shoe, S); drawBox(cam, g, S.obb(at(-sw * 0.35, -legX), face, lw * 1.1, lw * 1.1), 0.0, 0.07, shoe, shoe, S);
    drawBox(cam, g, S.obb(c, face, r * 0.55, r * 1.0), legH, h * 0.85, shirt, shirt, S);                                                                  // 몸통
    const armX = r * 1.0 + lw * 0.6;
    drawBox(cam, g, S.obb(at(-sw * 0.4, armX), face, lw * 0.8, lw * 0.8), h * 0.5, h * 0.85, shirt, skin, S); drawBox(cam, g, S.obb(at(sw * 0.4, -armX), face, lw * 0.8, lw * 0.8), h * 0.5, h * 0.85, shirt, skin, S);   // 팔
    drawBox(cam, g, S.obb(c, face, r * 0.5, r * 0.5), h * 0.85, h, skin, skin, S);                                                                        // 머리
    drawBox(cam, g, S.obb(c, face, r * 0.52, r * 0.52), h * 0.97, h + 0.03, hair, hair, S);                                                               // 머리카락
  }

  /** 장면: 바닥·천장·주차선·장애물·돌발 (+ 내 차 옆구리). 모든 카메라가 같이 쓴다 */
  function drawScene(cam, g, W, H, S, spec, snap, sc, opts) {
    const env = sc.env || "underground", under = env === "underground", night = env === "night", rain = env === "rain", P = cam.P;
    if (under) { g.fillStyle = "#2a2b2f"; g.fillRect(0, 0, W, H); }
    else { const sky = g.createLinearGradient(0, 0, 0, H * 0.55); if (night) { sky.addColorStop(0, "#070a14"); sky.addColorStop(1, "#1a2235"); } else if (rain) { sky.addColorStop(0, "#4a5058"); sky.addColorStop(1, "#8a9099"); } else { sky.addColorStop(0, "#5f7fa8"); sky.addColorStop(1, "#b9c6d6"); } g.fillStyle = sky; g.fillRect(0, 0, W, H); }
    const B = sc.bounds, big = S.obb(B.c, 0, B.hx + 14, B.hy + 14);
    fillPoly(g, cam.poly(S.corners(big), 0, 10), under ? "#3a3b40" : night ? "#1f2126" : rain ? "#33363c" : "#4b4d52");
    if (night || rain) {   // 가로등 불빛 웅덩이(밤) · 젖은 바닥 반사(비): 바닥 위 큰 원 몇 개
      for (const [lx, ly] of [[B.c[0] - B.hx, B.c[1] + B.hy + 1], [B.c[0] + B.hx, B.c[1] + B.hy + 1], [B.c[0], B.c[1] - B.hy - 1]]) {
        const pts = cam.poly(S.corners(S.obb([lx, ly], 0, 3.2, 3.2)), 0.002, 8); if (!pts.length) continue;
        fillPoly(g, pts, night ? "rgba(255,214,150,.13)" : "rgba(200,210,230,.10)"); } }
    const hz0 = cam.toScreen([0, (0 - P.h) * Math.cos(P.pitch) + 60 * Math.sin(P.pitch), 60 * Math.cos(P.pitch) - (0 - P.h) * Math.sin(P.pitch)])[1];
    const grd = g.createLinearGradient(0, hz0, 0, H); grd.addColorStop(0, under ? "rgba(20,20,24,.55)" : "rgba(120,130,150,.35)"); grd.addColorStop(0.5, "rgba(0,0,0,0)"); grd.addColorStop(1, "rgba(0,0,0,.25)");
    g.fillStyle = grd; g.fillRect(0, Math.max(0, hz0), W, H - Math.max(0, hz0));
    if (under) {
      fillPoly(g, cam.poly(S.corners(big), 2.7, 10), "#4a4b50");
      const x0 = Math.floor((B.c[0] - B.hx - 10) / 5) * 5, x1 = B.c[0] + B.hx + 10;
      for (let x = x0; x <= x1; x += 5) for (let y = Math.floor(B.c[1] - B.hy - 8); y <= B.c[1] + B.hy + 8; y += 6) {
        const pts = cam.poly(S.corners(S.obb([x, y + 3], 0, 0.15, 1.3)), 2.62, 2); fillPoly(g, pts, "#f4f1d8");
        if (pts.length) { const cx = pts.reduce((s, p) => s + p[0], 0) / pts.length, cy = pts.reduce((s, p) => s + p[1], 0) / pts.length; const gl = g.createRadialGradient(cx, cy, 2, cx, cy, 60); gl.addColorStop(0, "rgba(255,250,210,.35)"); gl.addColorStop(1, "rgba(255,250,210,0)"); g.fillStyle = gl; g.fillRect(cx - 60, cy - 60, 120, 120); }
      }
    }
    const lineBand = (a, b, w) => { const dx = b[0] - a[0], dy = b[1] - a[1], L = Math.hypot(dx, dy) || 1, nx = -dy / L * w / 2, ny = dx / L * w / 2; return [[a[0] + nx, a[1] + ny], [b[0] + nx, b[1] + ny], [b[0] - nx, b[1] - ny], [a[0] - nx, a[1] - ny]]; };
    const drawSlotLines = (b, col) => { const cs = S.corners(b); for (let i = 0; i < 4; i++) fillPoly(g, cam.poly(lineBand(cs[i], cs[(i + 1) % 4], 0.12), 0.003, 4), col); };
    sc.marks.forEach(m => drawSlotLines(m, night ? "rgba(235,235,230,.4)" : "rgba(235,235,230,.55)"));
    if (snap.phase === "PARKED") fillPoly(g, cam.poly(S.corners(sc.slot), 0.002, 4), "rgba(58,209,124,.25)");
    drawSlotLines(sc.slot, "rgba(250,250,245,.95)");
    { const u = [Math.cos(sc.slot.h), Math.sin(sc.slot.h)], c = [sc.slot.c[0] + u[0] * (sc.slot.hx * 0.4), sc.slot.c[1] + u[1] * (sc.slot.hx * 0.4)]; fillPoly(g, cam.poly(S.corners(S.obb(c, sc.slot.h, 0.35, 0.25)), 0.004, 3), "rgba(58,209,124,.55)"); }
    const objs = sc.obs.slice().sort((a, b) => cam.depth(b.b.c) - cam.depth(a.b.c));
    for (const o of objs) { if (o.car) drawCar3D(cam, g, o.b, o.color || "#8a8d94", S, false, o.model); else if (o.label === "기둥") drawPillar(cam, g, o.b, S); else drawWall(cam, g, o, S); }
    if (snap.hazard) drawHazard3D(cam, g, snap.hazard, snap.pose, S);
    if (opts.ownSide) drawOwnSide(cam, g, snap.body, "#2f6df6", S, opts.blink, spec.key, opts.ownSide); else if (opts.ownBody) drawCar3D(cam, g, snap.body, "#2f6df6", S, opts.blink, spec.key);
  }

  /** 비: 렌즈에 맺힌 빗줄기(프레임마다 다른 자리 → 움직이는 느낌) + 흐림 */
  function drawRain(g, W, H) { g.save(); g.strokeStyle = "rgba(220,230,245,.35)"; g.lineWidth = Math.max(1, W / 600); g.beginPath();
    for (let i = 0; i < 70; i++) { const x = Math.random() * W, y = Math.random() * H, L = H * (0.02 + Math.random() * 0.05); g.moveTo(x, y); g.lineTo(x - L * 0.15, y + L); } g.stroke();
    g.fillStyle = "rgba(150,165,190,.08)"; g.fillRect(0, 0, W, H); g.restore(); }
  /** 밤: 전체를 어둡게 + 카메라 노출 보정 느낌의 노이즈 */
  function drawNight(g, W, H) { g.fillStyle = "rgba(5,8,16,.28)"; g.fillRect(0, 0, W, H); }

  /** 8방향 주차 거리 센서(현대 PDW): 앞 3 · 뒤 3 · 옆 2 자리에서 가장 가까운 장애물·사람까지 거리(m). 호 그리기·소리·진동이 같이 쓴다 */
  function sensors(S, spec, snap, sc) {
    const b = snap.body, u = [Math.cos(b.h), Math.sin(b.h)], v = [-Math.sin(b.h), Math.cos(b.h)];
    const at = (du, dv) => [b.c[0] + u[0] * b.hx * du + v[0] * b.hy * dv, b.c[1] + u[1] * b.hx * du + v[1] * b.hy * dv];
    const spots = [["FL", 1, 0.7], ["F", 1, 0], ["FR", 1, -0.7], ["RL", -1, 0.7], ["R", -1, 0], ["RR", -1, -0.7], ["L", 0, 1], ["Rt", 0, -1]];
    return spots.map(([k, du, dv]) => { const p = at(du, dv); let d = 9;
      for (const o of sc.obs) d = Math.min(d, S.distPoint(o.b, p));
      if (snap.hazard) d = Math.min(d, Math.max(0, Math.hypot(snap.hazard.pos[0] - p[0], snap.hazard.pos[1] - p[1]) - snap.hazard.r));
      return { k, p, d, du, dv }; });
  }
  const pdwLevel = d => d < 0.3 ? 3 : d < 0.6 ? 2 : d < 1.2 ? 1 : 0;
  const PDW_COL = ["rgba(255,255,255,.10)", "#ffd228", "#ff8c2b", "#ff4d4d"];

  /** 후방/전방 카메라 화면 전체(가이드라인·거리 표시·범퍼·비네트·노이즈) */
  function drawCamera(g, W, H, S, spec, snap, sc, steer, opts) {
    const rev = snap.reverse, pose = snap.pose, P = rev ? REAR : FRONT;
    const pos = S.bumper(spec, pose, rev), cam = new Camera(S, pos, rev ? pose.h + PI : pose.h, P, W, H);
    drawScene(cam, g, W, H, S, spec, snap, sc, { ownBody: false });
    const pr = (st, range, n, col, lw, dash) => { const [L, R] = S.path(spec, pose, st, rev, range, n); strokeSegs(g, cam.path(L, 0.01), col, lw, dash); strokeSegs(g, cam.path(R, 0.01), col, lw, dash); };
    if (opts.guide !== false) {
      pr(0, 3.0, 2, "rgba(80,170,255,.95)", W / 320, [W / 90, W / 140]); pr(steer, 4.0, 40, "#ffd228", W / 190); pr(steer, 0.5, 6, "#ff4d4d", W / 190);
      const [a, b] = rev ? S.rearCorners(spec, pose) : S.frontCorners(spec, pose), dir = rev ? -1 : 1;
      g.font = `${Math.round(W / 46)}px IBM Plex Mono, monospace`; g.fillStyle = "rgba(255,255,255,.92)";
      for (const d of [0.5, 1.0, 2.3]) { const bk = [Math.cos(pose.h) * d * dir, Math.sin(pose.h) * d * dir]; const segs = cam.path([[a[0] + bk[0], a[1] + bk[1]], [b[0] + bk[0], b[1] + bk[1]]], 0.01);
        strokeSegs(g, segs, "rgba(255,255,255,.9)", W / 480); if (segs.length) { const e = segs[0][1]; g.fillText(d + "m", Math.max(segs[0][0][0], e[0]) + 8, e[1] + 6); } }
    }
    if (sc.env === "rain") drawRain(g, W, H); if (sc.env === "night") drawNight(g, W, H);
    const bump = g.createLinearGradient(0, H * 0.86, 0, H); bump.addColorStop(0, "rgba(10,10,12,0)"); bump.addColorStop(0.35, "#101114"); bump.addColorStop(1, "#070708"); g.fillStyle = bump; g.fillRect(0, H * 0.86, W, H * 0.14);
    const vg = g.createRadialGradient(W / 2, H / 2, H * 0.45, W / 2, H / 2, H * 0.95); vg.addColorStop(0, "rgba(0,0,0,0)"); vg.addColorStop(1, "rgba(0,0,0,.55)"); g.fillStyle = vg; g.fillRect(0, 0, W, H);
    g.globalAlpha = 0.07; g.fillStyle = noise(g); g.fillRect(0, 0, W, H); g.globalAlpha = 1;
  }

  /** 사이드미러(side: -1 왼쪽, +1 오른쪽): A필러 옆에서 뒤를 본다. 내 차 옆구리가 안쪽에 보이고, 거울상. */
  function drawMirror(g, W, H, S, spec, snap, sc, side, opts) {
    const pose = snap.pose, h = pose.h;
    const ahead = spec.wheelbase * 0.62, lat = (spec.width / 2 + 0.12) * side;
    // 차 기준 오른쪽 벡터 = (sin h, −cos h). 오른쪽 미러(side=+1)는 오른쪽으로, 왼쪽은 반대.
    const rx = Math.sin(h), ry = -Math.cos(h);
    const px = pose.x + Math.cos(h) * ahead + rx * lat, py = pose.y + Math.sin(h) * ahead + ry * lat;
    const yawOut = opts && opts.yawDeg !== undefined ? opts.yawDeg : 18, yaw = h + PI + side * rad(yawOut);     // 뒤를 보되 바깥쪽으로 18° (오른쪽 미러는 오른쪽 뒤)
    // R 연동 하향(PM 2026-10-27 "왼쪽은 안 되나"): 기본 양쪽, 설정(⋯)에서 오른쪽만·끔
    const dip = (opts && opts.dip) || "both", dipHere = snap.reverse && (dip === "both" || (dip === "right" && side > 0));
    const P = opts && opts.pitchDeg !== undefined ? Object.assign({}, SIDE, { pitch: rad(opts.pitchDeg) }) : (dipHere ? SIDE_DOWN : SIDE);
    const cam = new Camera(S, [px, py], yaw, P, W, H);
    drawScene(cam, g, W, H, S, spec, snap, sc, { ownBody: true, ownSide: side, blink: opts && opts.blink });
    if (sc.env === "rain") drawRain(g, W, H); if (sc.env === "night") drawNight(g, W, H);
    // 후측방 경고(BCW): 그쪽 옆·뒤(옆 0.5~3.5 m · 뒷차축 기준 −6~+1 m)에 차·사람이 있으면 거울 안쪽 주황 아이콘 점멸
    if (!(opts && opts.noBcw)) { const fw = [Math.cos(h), Math.sin(h)], rt = [Math.sin(h), -Math.cos(h)];
      const inZone = q => { const dx = q[0] - pose.x, dy = q[1] - pose.y, lon = dx * fw[0] + dy * fw[1], lat = (dx * rt[0] + dy * rt[1]) * side; return lon > -6 && lon < 1 && lat > 0.5 && lat < 3.5; };
      const warn = sc.obs.some(o => o.car && inZone(o.b.c)) || (snap.hazard && inZone(snap.hazard.pos));
      if (warn && Math.floor(Date.now() / 350) % 2 === 0) { const r = Math.min(W, H) * 0.09, cx = side > 0 ? W * 0.14 : W * 0.86, cy = H * 0.22;
        g.fillStyle = "#ff9a1f"; g.beginPath(); g.arc(cx, cy, r, 0, 7); g.fill(); g.fillStyle = "#1a1200"; g.beginPath(); g.moveTo(cx, cy - r * 0.55); g.lineTo(cx + r * 0.5, cy + r * 0.4); g.lineTo(cx - r * 0.5, cy + r * 0.4); g.closePath(); g.fill();
        g.fillStyle = "#ff9a1f"; g.fillRect(cx - r * 0.07, cy - r * 0.2, r * 0.14, r * 0.38); g.fillRect(cx - r * 0.07, cy + r * 0.24, r * 0.14, r * 0.1); } }
    // 거울 유리 느낌: 푸르스름한 틴트 + 왼쪽 위 비스듬한 반사 띠 + 가장자리 어둡게 + 검은 테
    g.fillStyle = "rgba(30,45,70,.07)"; g.fillRect(0, 0, W, H);
    const sp = g.createLinearGradient(0, 0, W * 0.55, H); sp.addColorStop(0, "rgba(255,255,255,.16)"); sp.addColorStop(0.28, "rgba(255,255,255,.05)"); sp.addColorStop(0.42, "rgba(255,255,255,0)"); g.fillStyle = sp; g.fillRect(0, 0, W, H);
    const vg = g.createRadialGradient(W / 2, H / 2, H * 0.45, W / 2, H / 2, H * 0.95); vg.addColorStop(0, "rgba(0,0,0,0)"); vg.addColorStop(1, "rgba(0,0,0,.45)"); g.fillStyle = vg; g.fillRect(0, 0, W, H);
    g.globalAlpha = 0.04; g.fillStyle = noise(g); g.fillRect(0, 0, W, H); g.globalAlpha = 1;
    g.strokeStyle = "rgba(0,0,0,.55)"; g.lineWidth = Math.max(3, W / 60); g.strokeRect(0, 0, W, H);
  }

  // ===================== 어라운드뷰 탑뷰 =====================
  /** 탑뷰 차(현대차 꼴): 세단은 보닛이 길고 캐빈이 뒤로, SUV 는 캐빈이 길고 루프랙. 앞 가로 한 줄 램프(아반떼는 두 토막)·뒤 빨간 띠·미러. */
  /** 현대 엠블럼 꼴(타원 안 기울어진 H). (x,y) 가운데, w = 가로 지름. ang = 회전 */
  function drawEmblem(g, x, y, w, ang, col) {
    const h = w * 0.5, t = w * 0.09; g.save(); g.translate(x, y); g.rotate(ang || 0);
    g.lineWidth = t; g.strokeStyle = col || "#c9ccd2"; g.lineCap = "round"; g.lineJoin = "round";
    g.beginPath(); g.ellipse(0, 0, w / 2 - t / 2, h / 2 - t / 2, 0, 0, 7); g.stroke();
    const sk = w * 0.08, x0 = -w * 0.18, x1 = w * 0.18, y0 = -h * 0.26, y1 = h * 0.26;             // 기울어진 두 세로획 + 가로획
    g.beginPath(); g.moveTo(x0 + sk, y0); g.lineTo(x0 - sk, y1); g.moveTo(x1 + sk, y0); g.lineTo(x1 - sk, y1); g.moveTo(x0, 0); g.lineTo(x1, 0); g.stroke(); g.restore(); }
  function drawCarTop(g, X, Y, scale, b, hex, mine, blink, model, S) {
    const M = (S && S.SPECS && S.SPECS[model]) || { shape: "sedan" }, suv = M.shape === "suv";
    g.save(); g.translate(X(b.c[0]), Y(b.c[1])); g.rotate(-b.h); const L = b.hx * 2 * scale, Wd = b.hy * 2 * scale;
    const rr = (x, y, w, h, r, fill) => { g.beginPath(); if (g.roundRect) g.roundRect(x, y, w, h, r); else g.rect(x, y, w, h); g.fillStyle = fill; g.fill(); };
    const trap = (x0, w0, x1, w1, fill) => { g.beginPath(); g.moveTo(x0, -w0 / 2); g.lineTo(x1, -w1 / 2); g.lineTo(x1, w1 / 2); g.lineTo(x0, w0 / 2); g.closePath(); g.fillStyle = fill; g.fill(); };
    rr(-L / 2 + 3, -Wd / 2 + 4, L, Wd, Wd * 0.18, "rgba(0,0,0,.4)");
    for (const [fx, fy] of [[0.3, 0.5], [0.3, -0.5], [-0.3, 0.5], [-0.3, -0.5]]) rr(fx * L - Wd * 0.17, fy * Wd - Wd * 0.07, Wd * 0.34, Wd * 0.14, 2, "#101114");
    const body = g.createLinearGradient(0, -Wd / 2, 0, Wd / 2); body.addColorStop(0, shade(hex, 0.8)); body.addColorStop(0.5, shade(hex, 1.08)); body.addColorStop(1, shade(hex, 0.75));
    rr(-L / 2, -Wd / 2, L, Wd, Wd * (suv ? 0.16 : 0.22), body);
    // 캐빈: 앞유리(사다리꼴) → 지붕 → 뒷유리
    const c = suv ? { f0: 0.22, f1: 0.05, r1: -0.4, r0: -0.49, w0: 0.84, w1: 0.74 } : { f0: 0.26, f1: 0.06, r1: -0.26, r0: -0.38, w0: 0.8, w1: 0.68 };
    trap(c.f0 * L, Wd * c.w0, c.f1 * L, Wd * c.w1, "#1a2838");
    rr(c.r1 * L, -Wd * c.w1 / 2, (c.f1 - c.r1) * L, Wd * c.w1, Wd * 0.06, shade(hex, 1.15));
    trap(c.r1 * L, Wd * c.w1, c.r0 * L, Wd * c.w0, "#1a2838");
    if (suv) for (const sy of [-1, 1]) rr(c.r1 * L, sy * Wd * 0.33 - 1, (c.f1 - c.r1) * L, 2, 1, "#2a2c31");
    // 미러
    rr(c.f0 * L - L * 0.03, -Wd * 0.62, L * 0.05, Wd * 0.12, 2, shade(hex, 0.9)); rr(c.f0 * L - L * 0.03, Wd * 0.5, L * 0.05, Wd * 0.12, 2, shade(hex, 0.9));
    // 앞 램프 한 줄(아반떼는 두 토막) · 뒤 빨간 띠
    const segs = model === "AVANTE" ? [[-0.46, -0.14], [0.14, 0.46]] : [[-0.46, 0.46]];
    for (const [s0, s1] of segs) rr(L / 2 - L * 0.035, s0 * Wd, L * 0.03, (s1 - s0) * Wd, 1.5, "#f4f6ff");
    rr(-L / 2 + L * 0.005, -Wd * 0.46, L * 0.025, Wd * 0.92, 1.5, "#e0342c");
    if (blink) { for (const sy of [-1, 1]) { rr(L * 0.4, sy * Wd * 0.44 - Wd * 0.06, L * 0.08, Wd * 0.12, 2, "#ffb340"); rr(-L * 0.47, sy * Wd * 0.44 - Wd * 0.06, L * 0.08, Wd * 0.12, 2, "#ffb340"); } }
    if (mine) { g.strokeStyle = "rgba(255,255,255,.7)"; g.lineWidth = 1.5; g.beginPath(); if (g.roundRect) g.roundRect(-L / 2, -Wd / 2, L, Wd, Wd * 0.22); else g.rect(-L / 2, -Wd / 2, L, Wd); g.stroke();
      if (Wd > 18) { drawEmblem(g, L * 0.42, 0, Wd * 0.22, PI / 2, "#e8eaef"); drawEmblem(g, -L * 0.42, 0, Wd * 0.2, PI / 2, "#e8eaef"); } }   // 보닛·트렁크 엠블럼(내 차만)
    g.restore();
  }

  function drawTopView(g, W, H, S, spec, snap, sc, steer, opts) {
    const scale = Math.min(W / (sc.bounds.hx * 2), H / (sc.bounds.hy * 2)) * 0.96, ox = W / 2 - sc.bounds.c[0] * scale, oy = H / 2 + sc.bounds.c[1] * scale;
    const X = x => ox + x * scale, Y = y => oy - y * scale;
    const poly = (pts, fill, stroke, lw, dash) => { g.beginPath(); pts.forEach((p, i) => i ? g.lineTo(X(p[0]), Y(p[1])) : g.moveTo(X(p[0]), Y(p[1]))); g.closePath(); if (fill) { g.fillStyle = fill; g.fill(); } if (stroke) { g.setLineDash(dash || []); g.strokeStyle = stroke; g.lineWidth = lw; g.stroke(); g.setLineDash([]); } };
    const env = sc.env || "underground", under = env === "underground", light = !!opts.light;
    // 라이트 테마(플레오스 심플): 밝은 회색 아스팔트 + 진한 회색 주차선. 다크: 원래 어두운 바닥
    const PAL = light ? { floor: under ? "#d9dbe0" : env === "night" ? "#b9bcc4" : env === "rain" ? "#c8cbd1" : "#e2e4e8", mark: "rgba(60,64,72,.45)", slot: "rgba(40,44,52,.9)", noise: 0.05, vig: 0.12, pdw0: "rgba(0,0,0,.07)" }
                      : { floor: under ? "#2e2f34" : env === "night" ? "#1c1e24" : env === "rain" ? "#2b2e36" : "#3c3e44", mark: "rgba(240,240,235,.5)", slot: "rgba(250,250,245,.95)", noise: 0.12, vig: 0.45, pdw0: PDW_COL[0] };
    g.fillStyle = PAL.floor; g.fillRect(0, 0, W, H);
    g.globalAlpha = PAL.noise; g.fillStyle = noise(g); g.fillRect(0, 0, W, H); g.globalAlpha = 1;
    sc.marks.forEach(m => poly(S.corners(m), null, PAL.mark, Math.max(1.5, 0.12 * scale)));
    poly(S.corners(sc.slot), snap.phase === "PARKED" ? "rgba(58,209,124,.3)" : "rgba(58,209,124,.12)", PAL.slot, Math.max(2, 0.12 * scale));
    { const u = [Math.cos(sc.slot.h), Math.sin(sc.slot.h)], c = [sc.slot.c[0] + u[0] * (sc.slot.hx * 0.4), sc.slot.c[1] + u[1] * (sc.slot.hx * 0.4)]; g.save(); g.translate(X(c[0]), Y(c[1])); g.font = `bold ${Math.max(12, 0.6 * scale)}px IBM Plex Sans KR, sans-serif`; g.fillStyle = light ? "#1f9d55" : "rgba(58,209,124,.8)"; g.textAlign = "center"; g.textBaseline = "middle"; g.fillText("P", 0, 0); g.restore(); }
    for (const o of sc.obs) {
      if (o.car) { drawCarTop(g, X, Y, scale, o.b, o.color || "#8a8d94", false, false, o.model, S); continue; }
      if (o.label === "기둥") { poly(S.corners(o.b), "#8e8b84", "#f0c020", Math.max(2, 0.08 * scale)); continue; }
      poly(S.corners(o.b), o.label === "연석" ? "#8b8d93" : "#6f6e6a", null);
      if (o.label !== "연석") { const cs = S.corners(o.b); g.strokeStyle = "rgba(232,185,42,.9)"; g.lineWidth = 2; g.beginPath(); g.moveTo(X(cs[0][0]), Y(cs[0][1])); g.lineTo(X(cs[1][0]), Y(cs[1][1])); g.stroke(); }
    }
    const pr = (st, range, n, col, lw, dash) => { const [L, R] = S.path(spec, snap.pose, st, snap.reverse, range, n); [L, R].forEach(P => { g.beginPath(); P.forEach((p, i) => i ? g.lineTo(X(p[0]), Y(p[1])) : g.moveTo(X(p[0]), Y(p[1]))); g.setLineDash(dash || []); g.strokeStyle = col; g.lineWidth = lw; g.lineCap = "round"; g.stroke(); g.setLineDash([]); }); };
    if (opts.guide !== false) { pr(0, 3, 2, "rgba(80,170,255,.9)", 2, [8, 6]); pr(steer, 4, 40, "#ffd228", 3.5); pr(steer, 0.5, 6, "#ff4d4d", 4); }
    drawCarTop(g, X, Y, scale, snap.body, "#2f6df6", true, opts.blink, spec.key, S);
    const wr = S.wheelRad(spec, steer);
    [[spec.wheelbase, spec.width / 2 - 0.12, wr], [spec.wheelbase, -(spec.width / 2 - 0.12), wr]].forEach(([fx, sy, ang]) => {
      const cx = snap.pose.x + fx * Math.cos(snap.pose.h) - sy * Math.sin(snap.pose.h), cy = snap.pose.y + fx * Math.sin(snap.pose.h) + sy * Math.cos(snap.pose.h);
      poly(S.corners(S.obb([cx, cy], snap.pose.h + ang, 0.32, 0.11)), "#0c0d10", null); });
    // 주차 거리 경고 호(현대 PDW 꼴): 센서 8자리마다 띠 3개, 가까울수록 많이·빨갛게
    if (opts.pdw !== false && snap.phase !== "READY") { const sens = sensors(S, spec, snap, sc), b = snap.body;
      for (const sn of sens) { const lv = pdwLevel(sn.d), ang = Math.atan2(sn.p[1] - b.c[1], sn.p[0] - b.c[0]), base = Math.hypot(sn.p[0] - b.c[0], sn.p[1] - b.c[1]);
        for (let k = 1; k <= 3; k++) { const rr = (base + 0.12 + k * 0.2) * scale; g.beginPath(); g.arc(X(b.c[0]), Y(b.c[1]), rr, -(ang + 0.14), -(ang - 0.14)); g.strokeStyle = k <= lv ? PDW_COL[lv] : PAL.pdw0; g.lineWidth = Math.max(2, 0.1 * scale); g.lineCap = "round"; g.stroke(); } } }
    if (opts.stopMark) { const m = opts.stopMark, x = X(m.x); g.setLineDash([6, 5]); g.strokeStyle = "#00AAD2"; g.lineWidth = Math.max(2, 0.06 * scale); g.beginPath(); g.moveTo(x, Y(m.y + 2.4)); g.lineTo(x, Y(m.y - 2.4)); g.stroke(); g.setLineDash([]);
      g.font = `600 ${Math.max(11, 0.45 * scale)}px Pretendard, sans-serif`; g.textAlign = "center"; g.fillStyle = "#00AAD2"; g.fillText("여기서 멈춤", x, Y(m.y + 2.4) - 6); g.textAlign = "left"; }   // 연습 모드: 핸들을 돌릴 자리
    if (snap.hazard) { const hz = snap.hazard, x = X(hz.pos[0]), y = Y(hz.pos[1]), rr = Math.max(8, (hz.r + 0.12) * scale);
      g.beginPath(); g.arc(x, y, rr * 1.6, 0, 7); g.fillStyle = "rgba(255,70,70,.28)"; g.fill();                                      // 경고 테
      if (hz.type === "cart") { g.fillStyle = "#d7dae0"; g.strokeStyle = "#ff4646"; g.lineWidth = 2; g.beginPath(); g.rect(x - rr * 0.9, y - rr * 0.6, rr * 1.8, rr * 1.2); g.fill(); g.stroke(); }
      else { g.fillStyle = hz.type === "child" ? "#f2c94c" : "#3b6fd1"; g.beginPath(); g.ellipse(x, y, rr * 1.1, rr * 0.7, 0, 0, 7); g.fill();     // 어깨
        g.fillStyle = "#e9b58f"; g.beginPath(); g.arc(x, y, rr * 0.55, 0, 7); g.fill(); g.strokeStyle = "#ff4646"; g.lineWidth = 2; g.beginPath(); g.ellipse(x, y, rr * 1.1, rr * 0.7, 0, 0, 7); g.stroke(); } }
    const vg = g.createRadialGradient(W / 2, H / 2, Math.min(W, H) * 0.4, W / 2, H / 2, Math.max(W, H) * 0.75); vg.addColorStop(0, "rgba(0,0,0,0)"); vg.addColorStop(1, `rgba(0,0,0,${PAL.vig})`); g.fillStyle = vg; g.fillRect(0, 0, W, H);
    return { X, Y, scale };
  }

  /** 결과 리플레이(탑뷰 애니메이션): i 번째 궤적 자세로 장면을 그리고, 공식 궤적(흰 점선)과 내 궤적(파란 실선, 뒷차축)을 겹친다 */
  function drawReplayTop(g, W, H, S, spec, sc, trace, i, ghost, light) {
    const tp = trace[Math.max(0, Math.min(i, trace.length - 1))], pose = tp.pose || sc.start;
    const snap = { pose, body: S.body(spec, pose), reverse: tp.v < 0, phase: "DRIVING", hazard: null };
    const { X, Y, scale } = drawTopView(g, W, H, S, spec, snap, sc, tp.steer, { guide: false, pdw: false, light });
    const line = (pts, col, lw, dash) => { if (pts.length < 2) return; g.beginPath(); pts.forEach((p, k) => k ? g.lineTo(X(p.x), Y(p.y)) : g.moveTo(X(p.x), Y(p.y))); g.setLineDash(dash || []); g.strokeStyle = col; g.lineWidth = lw; g.lineCap = "round"; g.lineJoin = "round"; g.stroke(); g.setLineDash([]); };
    if (ghost && ghost.length) line(ghost, light ? "rgba(30,34,40,.7)" : "rgba(255,255,255,.75)", Math.max(2, 0.08 * scale), [8, 7]);
    line(trace.slice(0, i + 1).map(t => t.pose).filter(Boolean), "rgba(80,170,255,.95)", Math.max(2.5, 0.1 * scale));
    g.font = `${Math.max(11, W / 46)}px Pretendard, system-ui, sans-serif`; g.fillStyle = light ? "rgba(20,22,27,.85)" : "rgba(255,255,255,.85)"; g.textAlign = "left"; g.fillText(`${tp.t.toFixed(1)} s · 핸들 ${tp.steer >= 0 ? "+" : ""}${Math.round(tp.steer)}°`, 10, H - 10);
    g.textAlign = "right"; g.fillStyle = light ? "rgba(20,22,27,.6)" : "rgba(255,255,255,.6)"; g.fillText("── 내 궤적   ┈┈ 공식 궤적", W - 10, H - 10); g.textAlign = "left";
  }

  /** 핸들 아이콘(요즘 현대 핸들): 더블 D컷 림(디 올 뉴 아반떼) · 9시·3시·6시 3스포크(더 뉴 투싼) · 가로 허브에 모스 부호 H 점 네 개 · 12시 파란 점(가운데 표시) */
  /** 플랫 핸들(플레오스 시스템 아이콘 꼴): 얇은 검정 선 림 + 3스포크 + 허브 점 네 개 + 12시 파란 점. 모양은 아래 입체 핸들과 같다 */
  /** 핸들(플레오스 심플 + 질감, PM 2026-10-27 "그림판 같다"): 선 두 겹 픽토그램 → 가죽 림(라디얼 음영) · 박음질 · 베벨 스포크 · 허브(점 네 개 유지) · 12시 띠. 조명은 돌리지 않고 마지막에 덧칠해 돌려도 빛 방향이 그대로다. */
  function drawWheelFlat(g, size, steer, col) {
    const c = size / 2, r = size * 0.45, rw = size * 0.105; g.clearRect(0, 0, size, size); g.save(); g.translate(c, c);
    const fy = r * 0.87, fx = Math.sqrt(1 - 0.87 * 0.87) * r, a = Math.atan2(fy, fx);
    const dpath = () => { g.beginPath(); g.moveTo(-fx, -fy); g.lineTo(fx, -fy); g.arc(0, 0, r, -a, a); g.lineTo(-fx, fy); g.arc(0, 0, r, PI - a, PI + a); g.closePath(); };
    g.lineJoin = "round"; g.lineCap = "round";
    g.save(); g.rotate(steer * PI / 180);
    // 림: 라디얼 그라데이션(안쪽 어둡게 → 가운데 밝게 → 바깥 어둡게) = 둥근 가죽
    const rg = g.createRadialGradient(0, 0, r - rw / 2, 0, 0, r + rw / 2); rg.addColorStop(0, "#15171b"); rg.addColorStop(0.42, "#3a3e46"); rg.addColorStop(0.62, "#2c2f36"); rg.addColorStop(1, "#111216");
    g.strokeStyle = rg; g.lineWidth = rw; dpath(); g.stroke();
    // 박음질
    g.setLineDash([size * 0.012, size * 0.014]); g.strokeStyle = "rgba(255,255,255,.22)"; g.lineWidth = Math.max(1, size * 0.004); dpath(); g.stroke(); g.setLineDash([]);
    // 12시 띠(가운데 표시)
    g.fillStyle = "#00AAD2"; g.beginPath(); if (g.roundRect) g.roundRect(-size * 0.05, -fy - rw * 0.3, size * 0.1, rw * 0.6, size * 0.012); else g.rect(-size * 0.05, -fy - rw * 0.3, size * 0.1, rw * 0.6); g.fill();
    // 스포크 셋(9시 · 3시 · 6시): 그림자 → 몸통 → 윗면 하이라이트 = 베벨
    const spoke = (x0, y0, x1, y1) => { g.lineWidth = size * 0.08; g.strokeStyle = "#0e0f12"; g.beginPath(); g.moveTo(x0, y0 + size * 0.012); g.lineTo(x1, y1 + size * 0.012); g.stroke();
      g.lineWidth = size * 0.07; g.strokeStyle = "#262930"; g.beginPath(); g.moveTo(x0, y0); g.lineTo(x1, y1); g.stroke();
      g.lineWidth = size * 0.028; g.strokeStyle = "#3d414a"; g.beginPath(); g.moveTo(x0, y0 - size * 0.016); g.lineTo(x1, y1 - size * 0.016); g.stroke(); };
    spoke(-r + rw * 0.35, 0, -size * 0.2, size * 0.012); spoke(r - rw * 0.35, 0, size * 0.2, size * 0.012);
    g.lineWidth = size * 0.07; g.strokeStyle = "#0e0f12"; g.beginPath(); g.moveTo(size * 0.012, size * 0.12); g.lineTo(size * 0.012, fy - size * 0.03); g.stroke();
    g.lineWidth = size * 0.06; g.strokeStyle = "#262930"; g.beginPath(); g.moveTo(0, size * 0.12); g.lineTo(0, fy - size * 0.03); g.stroke();
    g.lineWidth = size * 0.022; g.strokeStyle = "#3d414a"; g.beginPath(); g.moveTo(-size * 0.014, size * 0.13); g.lineTo(-size * 0.014, fy - size * 0.04); g.stroke();
    // 허브: 바깥 판(그라데이션) + 안쪽 판 + 점 네 개(PM "핸들은 유지" — 엠블럼 아님)
    const hw = size * 0.2, hh = size * 0.105; const hg = g.createLinearGradient(0, -hh, 0, hh); hg.addColorStop(0, "#33373f"); hg.addColorStop(1, "#1a1c21");
    g.fillStyle = "#0e0f12"; g.beginPath(); if (g.roundRect) g.roundRect(-hw, -hh + size * 0.012, hw * 2, hh * 2, size * 0.045); else g.rect(-hw, -hh, hw * 2, hh * 2); g.fill();
    g.fillStyle = hg; g.beginPath(); if (g.roundRect) g.roundRect(-hw, -hh, hw * 2, hh * 2, size * 0.045); else g.rect(-hw, -hh, hw * 2, hh * 2); g.fill();
    g.strokeStyle = "rgba(255,255,255,.1)"; g.lineWidth = 1; g.beginPath(); if (g.roundRect) g.roundRect(-hw + 1, -hh + 1, hw * 2 - 2, hh * 2 - 2, size * 0.04); else g.rect(-hw, -hh, hw * 2, hh * 2); g.stroke();
    g.fillStyle = "#15171b"; g.beginPath(); if (g.roundRect) g.roundRect(-hw * 0.78, -hh * 0.5, hw * 1.56, hh, size * 0.03); else g.rect(-hw * 0.78, -hh * 0.5, hw * 1.56, hh); g.fill();
    for (const k of [-1.5, -0.5, 0.5, 1.5]) { const x = k * size * 0.06; g.fillStyle = "#07080a"; g.beginPath(); g.arc(x, size * 0.004, size * 0.021, 0, 7); g.fill(); g.fillStyle = "#4a4f59"; g.beginPath(); g.arc(x, 0, size * 0.018, 0, 7); g.fill(); g.fillStyle = "#2a2d34"; g.beginPath(); g.arc(x, size * 0.004, size * 0.011, 0, 7); g.fill(); }
    g.restore();
    // 조명(돌지 않음): 위는 밝게 아래는 어둡게, 이미 그린 픽셀 위에만
    g.globalCompositeOperation = "source-atop"; const lg = g.createLinearGradient(0, -r - rw, 0, r + rw); lg.addColorStop(0, "rgba(255,255,255,.2)"); lg.addColorStop(0.45, "rgba(255,255,255,0)"); lg.addColorStop(1, "rgba(0,0,0,.28)");
    g.fillStyle = lg; g.fillRect(-c, -c, size, size); g.globalCompositeOperation = "source-over"; g.restore(); }
  function drawWheel(g, size, steer, flat) { if (flat) return drawWheelFlat(g, size, steer, typeof flat === "object" ? flat : null); const c = size / 2, r = size * 0.42; g.clearRect(0, 0, size, size); g.save(); g.translate(c, c); g.rotate(steer * PI / 180);
    const fy = r * 0.87, fx = Math.sqrt(1 - 0.87 * 0.87) * r, a = Math.atan2(fy, fx);   // 위아래 평평한 림
    const rim = () => { g.beginPath(); g.moveTo(-fx, -fy); g.lineTo(fx, -fy); g.arc(0, 0, r, -a, a); g.lineTo(-fx, fy); g.arc(0, 0, r, PI - a, PI + a); g.closePath(); };
    g.lineJoin = "round"; g.lineWidth = size * 0.12; g.strokeStyle = "#2a2e37"; rim(); g.stroke();
    g.lineWidth = size * 0.08; g.strokeStyle = "#3a3f4b"; rim(); g.stroke();
    g.lineWidth = size * 0.02; g.strokeStyle = "rgba(255,255,255,.12)"; g.beginPath(); g.arc(0, 0, r - size * 0.03, PI + a + 0.15, 2 * PI - a - 0.15); g.stroke();   // 윗쪽 하이라이트
    g.strokeStyle = "#b7bcc6"; g.lineWidth = size * 0.085; g.lineCap = "round"; g.beginPath();
    g.moveTo(-r + size * 0.06, -size * 0.01); g.lineTo(-size * 0.15, size * 0.025); g.moveTo(r - size * 0.06, -size * 0.01); g.lineTo(size * 0.15, size * 0.025); g.moveTo(0, size * 0.13); g.lineTo(0, fy - size * 0.03); g.stroke();
    const hw = size * 0.2, hh = size * 0.105; g.fillStyle = "#1d2027"; g.beginPath(); if (g.roundRect) g.roundRect(-hw, -hh, hw * 2, hh * 2, size * 0.04); else g.rect(-hw, -hh, hw * 2, hh * 2); g.fill();
    g.fillStyle = "#d6dae2"; for (const k of [-1.5, -0.5, 0.5, 1.5]) { g.beginPath(); g.arc(k * size * 0.06, 0, size * 0.022, 0, 7); g.fill(); }   // 모스 부호 H = ····
    g.fillStyle = "#00AAD2"; g.beginPath(); g.arc(0, -fy, size * 0.035, 0, 7); g.fill(); g.restore(); }

  /** 슬림 클러스터(PM 2026-10-26 '계기판도 진짜 차처럼 — 핸들 앞 9.9인치 패널'): 검정 유리 바 위에 흰 큰 숫자·얇은 막대 게이지. 낮·밤 상관없이 검정(물리 디스플레이).
   *  d = { kmh, gear:'P'|'R'|'N'|'D', realP, sigL, sigR(점멸 반영), brakeD, latch, steer, elapsed, hits, src } */
  function drawCluster(g, W, H, d) {
    const F = "Pretendard, 'IBM Plex Sans KR', system-ui, sans-serif"; g.clearRect(0, 0, W, H);
    const r = H * 0.16; g.beginPath(); if (g.roundRect) g.roundRect(0, 0, W, H, r); else g.rect(0, 0, W, H); g.fillStyle = "#0a0b0d"; g.fill();
    const gl = g.createLinearGradient(0, 0, 0, H); gl.addColorStop(0, "rgba(255,255,255,.09)"); gl.addColorStop(0.35, "rgba(255,255,255,.015)"); gl.addColorStop(1, "rgba(255,255,255,0)"); g.fillStyle = gl; g.fill();
    g.strokeStyle = "rgba(255,255,255,.10)"; g.lineWidth = 1; g.beginPath(); if (g.roundRect) g.roundRect(0.5, 0.5, W - 1, H - 1, r); else g.rect(0.5, 0.5, W - 1, H - 1); g.stroke();
    const pad = W * 0.045, dim = "#8d93a0", dim2 = "#4a4f59", wh = "#f4f5f7", bw = Math.min(W * 0.2, H * 1.9);
    g.textBaseline = "alphabetic";
    // 왼쪽: BRAKE 게이지(연료 게이지 꼴)
    g.textAlign = "left"; g.font = `500 ${H * 0.15}px ${F}`; g.fillStyle = dim; g.fillText("BRAKE", pad, H * 0.32);
    const pct = Math.round(d.brakeD * 100); g.font = `500 ${H * 0.34}px ${F}`; g.fillStyle = wh; g.fillText(d.src === "pleos" ? (d.brakeD >= 0.6 ? "밟음" : "뗌") : pct + "%", pad, H * 0.66);
    const by = H * 0.82, bh = Math.max(2, H * 0.05); g.fillStyle = "rgba(255,255,255,.16)"; g.fillRect(pad, by, bw, bh); g.fillStyle = d.brakeD >= 0.6 ? wh : "#ff5a5a"; g.fillRect(pad, by, bw * d.brakeD, bh);
    g.fillStyle = "rgba(255,255,255,.55)"; g.fillRect(pad + bw * 0.6 - 1, by - bh * 1.2, 2, bh * 3.4);
    g.font = `500 ${H * 0.11}px ${F}`; g.fillStyle = dim2; g.textAlign = "center"; g.fillText("정지", pad + bw * 0.6, by - bh * 1.4);
    // 가운데: 깜빡이 ◀ ▶ · 속도 큰 숫자 · km/h · 위에 P R N D
    // 속도: P R N D 줄 아래, 기준선 0.84(PM 2026-10-27 '속도계 내려')
    const cx = W / 2; g.font = `600 ${H * 0.56}px ${F}`; g.textAlign = "center"; g.fillStyle = wh; const sp = String(Math.round(d.kmh)); g.fillText(sp, cx, H * 0.84);
    const sw = g.measureText(sp).width; g.font = `500 ${H * 0.15}px ${F}`; g.fillStyle = dim; g.textAlign = "left"; g.fillText("km/h", cx + sw / 2 + H * 0.1, H * 0.84); const kw = g.measureText("km/h").width;
    // 깜빡이 ◀ ▶: 숫자·km/h 와 겹치지 않게 잰 폭 기준으로(PM 2026-10-27)
    const tri = (x, dir, on) => { const h = H * 0.17; g.fillStyle = on ? "#35d07a" : "#23262c"; g.beginPath(); g.moveTo(x + dir * h, H * 0.62); g.lineTo(x, H * 0.62 - h * 0.7); g.lineTo(x, H * 0.62 + h * 0.7); g.closePath(); g.fill(); };
    tri(cx - sw / 2 - H * 0.3, -1, d.sigL); tri(cx + sw / 2 + H * 0.1 + kw + H * 0.3, 1, d.sigR);
    g.font = `600 ${H * 0.15}px ${F}`; const gs = ["P", "R", "N", "D"], gw = H * 0.28; let gx = cx - gw * 1.5;
    for (const k of gs) { const on = k === d.gear; if (on) { g.fillStyle = d.realP || k !== "P" ? wh : "#ff5a5a"; g.beginPath(); if (g.roundRect) g.roundRect(gx - gw * 0.38, H * 0.08, gw * 0.76, H * 0.2, 3); else g.rect(gx - gw * 0.38, H * 0.08, gw * 0.76, H * 0.2); g.fill(); g.fillStyle = "#0a0b0d"; }
      else g.fillStyle = (!d.realP && k === "D") ? "#ff5a5a" : dim2; g.textAlign = "center"; g.fillText(k, gx, H * 0.24); gx += gw; }
    // 오른쪽: TIME · 핸들 각도 막대
    const rx = W - pad - bw; g.textAlign = "left"; g.font = `500 ${H * 0.15}px ${F}`; g.fillStyle = dim; g.fillText("TIME", rx, H * 0.32);
    { const room = (W - pad) - (rx + g.measureText("TIME").width + H * 0.12); const cands = d.src === "pleos" ? ["실차 신호"] : d.src === "pleos-haz" ? ["실차 신호 · 비상등 화면", "실차 · 비상등 화면", "비상등 화면"] : ["화면 조작"];
      const t = cands.find(x => g.measureText(x).width <= room); if (t) { g.textAlign = "right"; g.fillStyle = dim2; g.fillText(t, W - pad, H * 0.32); } }   // TIME 오른쪽에 남는 폭에 들어가는 가장 긴 글자, 안 들어가면 뺀다(앱 ClusterView 와 같다)
    g.textAlign = "left"; g.font = `500 ${H * 0.34}px ${F}`; g.fillStyle = wh; const ts = d.elapsed.toFixed(1) + " s"; g.fillText(ts, rx, H * 0.66);
    if (d.hits) { const tw = g.measureText(ts).width; g.font = `500 ${H * 0.15}px ${F}`; g.fillStyle = "#ff5a5a"; g.fillText("+" + d.hits * 5, rx + tw + H * 0.1, H * 0.66); }
    g.fillStyle = "rgba(255,255,255,.16)"; g.fillRect(rx, by, bw, bh); g.fillStyle = "rgba(255,255,255,.55)"; g.fillRect(rx + bw / 2 - 1, by - bh * 1.2, 2, bh * 3.4);
    const mx = rx + bw / 2 + (Math.max(-540, Math.min(540, d.steer)) / 540) * bw / 2; g.fillStyle = wh; g.fillRect(Math.min(rx + bw / 2, mx), by, Math.abs(mx - rx - bw / 2), bh); g.fillRect(mx - 1.5, by - bh, 3, bh * 3);
    g.font = `500 ${H * 0.11}px ${F}`; g.fillStyle = dim2; g.textAlign = "right"; g.fillText((d.steer >= 0 ? "+" : "") + Math.round(d.steer) + "°", W - pad, by - bh * 1.4); g.textAlign = "left";
    // 계기판 안내 팝업(실차 클러스터 팝업 자리): 예) 브레이크를 밟고 변속하세요
    if (d.msg) { g.font = `600 ${H * 0.17}px ${F}`; const tw = g.measureText(d.msg).width + H * 0.5, th = H * 0.36; g.beginPath(); if (g.roundRect) g.roundRect(cx - tw / 2, H / 2 - th / 2, tw, th, th / 2); else g.rect(cx - tw / 2, H / 2 - th / 2, tw, th); g.fillStyle = "#f4f5f7"; g.fill(); g.fillStyle = "#0a0b0d"; g.textAlign = "center"; g.textBaseline = "middle"; g.fillText(d.msg, cx, H / 2); g.textBaseline = "alphabetic"; g.textAlign = "left"; }
  }
  function drawReplay(g, W, H, trace) { g.fillStyle = "#15181f"; g.fillRect(0, 0, W, H); const T = trace; if (!T.length) return; const tmax = T[T.length - 1].t || 1, X = t => 20 + (W - 40) * t / tmax, Y = s => H / 2 - (H / 2 - 14) * s / 540;
    g.strokeStyle = "#2a2f3a"; g.lineWidth = 1; [-540, 0, 540].forEach(v => { g.beginPath(); g.moveTo(20, Y(v)); g.lineTo(W - 20, Y(v)); g.stroke(); });
    g.fillStyle = "#9aa3b2"; g.font = "20px IBM Plex Mono, monospace"; g.fillText("+540°", 24, Y(540) + 22); g.fillText("0°", 24, Y(0) - 6); g.fillText("−540°", 24, Y(-540) - 6);
    T.forEach((p, i) => { if (i === 0) return; g.fillStyle = p.v < 0 ? "rgba(255,179,64,.08)" : "rgba(74,158,255,.10)"; g.fillRect(X(T[i - 1].t), 14, Math.max(1, X(p.t) - X(T[i - 1].t)), H - 28); });
    g.strokeStyle = "#ffb340"; g.lineWidth = 3; g.beginPath(); T.forEach((p, i) => i ? g.lineTo(X(p.t), Y(p.steer)) : g.moveTo(X(p.t), Y(p.steer))); g.stroke(); }

  function drawPreview(g, W, H, S, spec, sc, light) { const snap = { pose: sc.start, body: S.body(spec, sc.start), reverse: !sc.forward, phase: "READY", hazard: null }; drawTopView(g, W, H, S, spec, snap, sc, 0, { guide: false, light }); }

  return { drawCamera, drawMirror, drawTopView, drawPreview, drawWheel, drawCluster, drawReplay, drawReplayTop, sensors, pdwLevel, REAR, FRONT, SIDE };
});
