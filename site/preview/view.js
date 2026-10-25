/* 쏙 SSOK — 화면 그리기(브라우저 시안). 후방/전방 카메라 재현(핀홀 + 어안 왜곡 + 지하주차장 천장·조명 + 입체 차·기둥 + 노이즈)과
 * 어라운드뷰 탑뷰(아스팔트·주차선·위에서 본 차 그림). 수식은 sim.js 와 같다. Kotlin render/ 로 옮길 때의 기준. */
(function (root, factory) {
  if (typeof module === "object" && module.exports) module.exports = factory();
  else root.SSOK_VIEW = factory();
})(typeof self !== "undefined" ? self : this, function () {
  const PI = Math.PI;

  // ---- 색 도우미 ----
  function hexRgb(h) { const n = parseInt(h.slice(1), 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255]; }
  function shade(hex, f, a) { const [r, g, b] = hexRgb(hex).map(v => Math.max(0, Math.min(255, Math.round(v * f)))); return a === undefined ? `rgb(${r},${g},${b})` : `rgba(${r},${g},${b},${a})`; }
  function mix(h1, h2, t) { const a = hexRgb(h1), b = hexRgb(h2); return `rgb(${a.map((v, i) => Math.round(v + (b[i] - v) * t)).join(",")})`; }

  // ---- 노이즈(카메라 질감·아스팔트) : 한 번 만들어 패턴으로 ----
  let noiseCv = null;
  function noise(g) {
    if (!noiseCv) { noiseCv = document.createElement("canvas"); noiseCv.width = noiseCv.height = 160; const n = noiseCv.getContext("2d"), img = n.createImageData(160, 160);
      for (let i = 0; i < img.data.length; i += 4) { const v = 128 + (Math.random() - 0.5) * 90; img.data[i] = img.data[i + 1] = img.data[i + 2] = v; img.data[i + 3] = 255; } n.putImageData(img, 0, 0); }
    return g.createPattern(noiseCv, "repeat");
  }

  // ===================== 카메라(후방/전방) =====================
  const CAM = { h: 0.95, pitch: 25 * PI / 180, hfov: 140 * PI / 180, near: 0.12, fish: 0.95 /* 어안: r' = atan(c·r)/c, 단조라 뒤집히지 않는다 */ };

  class Camera {
    constructor(S, spec, pose, reverse, W, H) {
      this.S = S; this.spec = spec; this.pose = pose; this.rev = reverse; this.W = W; this.H = H;
      this.c = S.bumper(spec, pose, reverse); this.f = (W / 2) / Math.tan(CAM.hfov / 2);
      this.dir = reverse ? -1 : 1;
    }
    /** 월드 (x,y,z) → 카메라 좌표 [right, up, depth] */
    toCam(p, z0) {
      const { pose, c } = this, dx = p[0] - c[0], dy = p[1] - c[1];
      const along = (dx * Math.cos(pose.h) + dy * Math.sin(pose.h)) * this.dir, right = dx * Math.sin(pose.h) - dy * Math.cos(pose.h);
      const yc = (z0 || 0) - CAM.h;
      return [right, along * Math.sin(CAM.pitch) + yc * Math.cos(CAM.pitch), along * Math.cos(CAM.pitch) - yc * Math.sin(CAM.pitch)];
    }
    /** 카메라 좌표 → 화면(어안 왜곡 포함) */
    toScreen(cc) {
      let nx = (this.f * cc[0] / cc[2]) / (this.W / 2), ny = (this.f * cc[1] / cc[2]) / (this.W / 2);
      const r = Math.hypot(nx, ny); if (r > 1e-6) { const c = CAM.fish, k = (Math.atan(r * c) / c) / r; nx *= k; ny *= k; }
      return [this.W / 2 + nx * this.W / 2, this.H / 2 - ny * this.W / 2];
    }
    clipNear(poly) { const out = [], n = CAM.near; for (let i = 0; i < poly.length; i++) { const a = poly[i], b = poly[(i + 1) % poly.length], ia = a[2] >= n, ib = b[2] >= n;
      if (ia) out.push(a); if (ia !== ib) { const t = (n - a[2]) / (b[2] - a[2]); out.push([a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, n]); } } return out; }
    /** 월드 다각형(모서리를 잘게 나눠 어안 곡률이 나오게) → 화면 점 */
    poly(pts, z, sub) { const w = []; const m = sub || 6;
      for (let i = 0; i < pts.length; i++) { const a = pts[i], b = pts[(i + 1) % pts.length]; for (let j = 0; j < m; j++) { const t = j / m; w.push([a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t]); } }
      return this.clipNear(w.map(p => this.toCam(p, z))).map(cc => this.toScreen(cc)); }
    quad(a, b, za, zb) { // 세로 면: 바닥 선분 a-b 를 za..zb 높이로
      const w = []; const m = 6; for (let j = 0; j <= m; j++) { const t = j / m; w.push([a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t]); }
      const cam = w.map(p => this.toCam(p, za)).concat(w.slice().reverse().map(p => this.toCam(p, zb)));
      return this.clipNear(cam).map(cc => this.toScreen(cc)); }
    path(pts, z) { const cam = pts.map(p => this.toCam(p, z || 0)), segs = []; for (let i = 1; i < cam.length; i++) { const a = cam[i - 1], b = cam[i], ia = a[2] >= CAM.near, ib = b[2] >= CAM.near; if (!ia && !ib) continue;
      let p = a, q = b; if (ia !== ib) { const t = (CAM.near - a[2]) / (b[2] - a[2]); const m = [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, CAM.near]; if (ia) q = m; else p = m; } segs.push([this.toScreen(p), this.toScreen(q)]); } return segs; }
    depth(p) { return this.toCam(p, 0)[2]; }
    /** 면의 밝기: 바깥 법선이 카메라를 향할수록 밝다(헤드램프·실내등 느낌) */
    light(a, b) { const nx = (b[1] - a[1]), ny = -(b[0] - a[0]), len = Math.hypot(nx, ny) || 1; const mx = (a[0] + b[0]) / 2 - this.c[0], my = (a[1] + b[1]) / 2 - this.c[1], ml = Math.hypot(mx, my) || 1;
      const d = -(nx * mx + ny * my) / (len * ml); return 0.55 + 0.45 * Math.max(0, d); }
  }

  function fillPoly(g, pts, style, stroke, lw) { if (pts.length < 3) return; g.beginPath(); pts.forEach((p, i) => i ? g.lineTo(p[0], p[1]) : g.moveTo(p[0], p[1])); g.closePath(); if (style) { g.fillStyle = style; g.fill(); } if (stroke) { g.strokeStyle = stroke; g.lineWidth = lw || 1; g.stroke(); } }
  function strokeSegs(g, segs, style, lw, dash) { if (!segs.length) return; g.beginPath(); segs.forEach(([a, b]) => { g.moveTo(a[0], a[1]); g.lineTo(b[0], b[1]); }); g.setLineDash(dash || []); g.strokeStyle = style; g.lineWidth = lw; g.lineCap = "round"; g.stroke(); g.setLineDash([]); }

  /** 상자(바닥 OBB, z0..z1)를 면 단위로: 먼 면부터, 밝기 적용 */
  function drawBox(cam, g, b, z0, z1, baseHex, topHex, S) {
    const cs = S.corners(b);
    const faces = [0, 1, 2, 3].map(i => [cs[i], cs[(i + 1) % 4]]).sort((f1, f2) => (cam.depth(f2[0]) + cam.depth(f2[1])) - (cam.depth(f1[0]) + cam.depth(f1[1])));
    for (const [a, c] of faces) fillPoly(g, cam.quad(a, c, z0, z1), shade(baseHex, cam.light(a, c)));
    fillPoly(g, cam.poly(cs, z1, 3), shade(topHex || baseHex, 1.05));
  }

  /** 주차된 차: 차체(0.2~0.8) + 캐빈(0.8~1.45, 안쪽으로 들어감) + 바퀴 + 등 */
  function drawCar3D(cam, g, b, hex, S) {
    const cs = S.corners(b);
    const u = [Math.cos(b.h), Math.sin(b.h)], v = [-Math.sin(b.h), Math.cos(b.h)];
    // 바퀴(바닥에 붙은 어두운 상자 4개)
    for (const [du, dv] of [[0.62, 0.85], [0.62, -0.85], [-0.62, 0.85], [-0.62, -0.85]]) {
      const c = [b.c[0] + u[0] * b.hx * du + v[0] * b.hy * dv, b.c[1] + u[1] * b.hx * du + v[1] * b.hy * dv];
      drawBox(cam, g, S.obb(c, b.h, 0.33, 0.11), 0.0, 0.62, "#15161a", "#1c1d22", S);
    }
    // 그림자
    fillPoly(g, cam.poly(S.corners(S.obb(b.c, b.h, b.hx + 0.15, b.hy + 0.2)), 0.005, 3), "rgba(0,0,0,.35)");
    drawBox(cam, g, b, 0.25, 0.85, hex, hex, S);
    const cab = S.obb([b.c[0] - u[0] * 0.25, b.c[1] - u[1] * 0.25], b.h, b.hx * 0.52, b.hy * 0.9);
    // 유리(어두운 청회색) 위에 지붕
    drawBox(cam, g, cab, 0.85, 1.42, "#1f2a38", hex, S);
    // 앞뒤 등: 앞(진행 방향 +u) 흰색, 뒤 붉은색
    for (const [side, col] of [[1, "#f3f3e0"], [-1, "#d0302a"]]) for (const dv of [0.7, -0.7]) {
      const c = [b.c[0] + u[0] * b.hx * side * 1.0 + v[0] * b.hy * dv, b.c[1] + u[1] * b.hx * side * 1.0 + v[1] * b.hy * dv];
      const a = [c[0] - v[0] * 0.18, c[1] - v[1] * 0.18], d = [c[0] + v[0] * 0.18, c[1] + v[1] * 0.18];
      fillPoly(g, cam.quad(a, d, 0.55, 0.72), col);
    }
  }

  /** 기둥: 콘크리트 + 아래 1 m 노란·검정 경고 띠 */
  function drawPillar(cam, g, b, S) {
    drawBox(cam, g, b, 0.0, 2.6, "#9a978f", "#a7a49c", S);
    const cs = S.corners(b);
    for (let i = 0; i < 4; i++) { const a = cs[i], c = cs[(i + 1) % 4]; if (cam.depth(a) < 0 && cam.depth(c) < 0) continue;
      fillPoly(g, cam.quad(a, c, 0.25, 1.05), shade("#f0c020", cam.light(a, c)));
      for (let k = 0; k < 3; k++) { const t0 = k / 3 + 0.08, t1 = k / 3 + 0.2; const p0 = [a[0] + (c[0] - a[0]) * t0, a[1] + (c[1] - a[1]) * t0], p1 = [a[0] + (c[0] - a[0]) * t1, a[1] + (c[1] - a[1]) * t1];
        fillPoly(g, cam.quad(p0, p1, 0.25, 1.05), shade("#15151a", cam.light(a, c))); }
    }
  }
  /** 벽·연석: 긴 상자. 벽은 콘크리트 + 노란 띠, 연석은 낮고 회색 */
  function drawWall(cam, g, o, S) {
    if (o.label === "연석") { drawBox(cam, g, o.b, 0.0, 0.15, "#8b8d93", "#9a9ca3", S); return; }
    drawBox(cam, g, o.b, 0.0, 2.6, "#86847d", "#8f8d86", S);
    const cs = S.corners(o.b); for (let i = 0; i < 4; i++) { const a = cs[i], c = cs[(i + 1) % 4]; fillPoly(g, cam.quad(a, c, 0.45, 0.6), shade("#e8b92a", cam.light(a, c) * 0.9)); }
  }
  /** 사람·카트 */
  function drawHazard3D(cam, g, hz, pose, S) {
    const r = hz.r, h = hz.height, c = hz.pos, face = pose.h + PI / 2;
    if (hz.type === "cart") { drawBox(cam, g, S.obb(c, face, 0.45, 0.3), 0.15, 0.95, "#b9bcc4", "#d7dae0", S); drawBox(cam, g, S.obb(c, face, 0.3, 0.2), 0.0, 0.15, "#2a2a2e", "#333", S); return; }
    const skin = "#e9b58f", shirt = hz.type === "child" ? "#f2c94c" : "#3b6fd1", pants = "#2c3140";
    const b = S.obb(c, face, r * 0.6, r);
    fillPoly(g, cam.poly(S.corners(S.obb(c, face, r * 1.3, r * 1.3)), 0.005, 3), "rgba(0,0,0,.35)");
    drawBox(cam, g, b, 0.0, h * 0.47, pants, pants, S);
    drawBox(cam, g, S.obb(c, face, r * 0.7, r * 1.15), h * 0.47, h * 0.85, shirt, shirt, S);
    drawBox(cam, g, S.obb(c, face, r * 0.45, r * 0.45), h * 0.85, h, skin, "#4a3328", S);
  }

  /** 카메라 화면 전체 */
  function drawCamera(g, W, H, S, spec, snap, sc, steer, opts) {
    const rev = snap.reverse, pose = snap.pose, cam = new Camera(S, spec, pose, rev, W, H);
    const under = sc.env !== "outdoor";
    // 하늘/천장
    if (under) { g.fillStyle = "#2a2b2f"; g.fillRect(0, 0, W, H); }
    else { const sky = g.createLinearGradient(0, 0, 0, H * 0.55); sky.addColorStop(0, "#5f7fa8"); sky.addColorStop(1, "#b9c6d6"); g.fillStyle = sky; g.fillRect(0, 0, W, H); }
    // 바닥(아스팔트): 큰 사각형을 투영 + 거리 그라데이션
    const B = sc.bounds, big = S.obb(B.c, 0, B.hx + 14, B.hy + 14);
    fillPoly(g, cam.poly(S.corners(big), 0, 10), under ? "#3a3b40" : "#4b4d52");
    const hz0 = cam.toScreen([0, (0 - CAM.h) * Math.cos(CAM.pitch) + 60 * Math.sin(CAM.pitch), 60 * Math.cos(CAM.pitch) - (0 - CAM.h) * Math.sin(CAM.pitch)])[1];
    const grd = g.createLinearGradient(0, hz0, 0, H); grd.addColorStop(0, under ? "rgba(20,20,24,.55)" : "rgba(120,130,150,.35)"); grd.addColorStop(0.5, "rgba(0,0,0,0)"); grd.addColorStop(1, "rgba(0,0,0,.25)");
    g.fillStyle = grd; g.fillRect(0, hz0, W, H - hz0);
    // 천장 + 형광등(지하)
    if (under) {
      fillPoly(g, cam.poly(S.corners(big), 2.7, 10), "#4a4b50");
      const x0 = Math.floor((B.c[0] - B.hx - 10) / 5) * 5, x1 = B.c[0] + B.hx + 10;
      for (let x = x0; x <= x1; x += 5) for (let y = Math.floor(B.c[1] - B.hy - 8); y <= B.c[1] + B.hy + 8; y += 6) {
        const l = S.obb([x, y + 3], 0, 0.15, 1.3); const pts = cam.poly(S.corners(l), 2.62, 2); fillPoly(g, pts, "#f4f1d8");
        if (pts.length) { const cx = pts.reduce((s, p) => s + p[0], 0) / pts.length, cy = pts.reduce((s, p) => s + p[1], 0) / pts.length; const gl = g.createRadialGradient(cx, cy, 2, cx, cy, 60); gl.addColorStop(0, "rgba(255,250,210,.35)"); gl.addColorStop(1, "rgba(255,250,210,0)"); g.fillStyle = gl; g.fillRect(cx - 60, cy - 60, 120, 120); }
      }
    }
    // 주차선(옆 칸 + 목표 칸): 두께 있는 선을 월드에서 그린다
    const lineBand = (a, b, w) => { const dx = b[0] - a[0], dy = b[1] - a[1], L = Math.hypot(dx, dy) || 1, nx = -dy / L * w / 2, ny = dx / L * w / 2; return [[a[0] + nx, a[1] + ny], [b[0] + nx, b[1] + ny], [b[0] - nx, b[1] - ny], [a[0] - nx, a[1] - ny]]; };
    const drawSlotLines = (b, col) => { const cs = S.corners(b); for (let i = 0; i < 4; i++) fillPoly(g, cam.poly(lineBand(cs[i], cs[(i + 1) % 4], 0.12), 0.003, 4), col); };
    sc.marks.forEach(m => drawSlotLines(m, "rgba(235,235,230,.55)"));
    if (snap.phase === "PARKED") fillPoly(g, cam.poly(S.corners(sc.slot), 0.002, 4), "rgba(58,209,124,.25)");
    drawSlotLines(sc.slot, "rgba(250,250,245,.95)");
    // 목표 칸 바닥 'P' 표시
    { const u = [Math.cos(sc.slot.h), Math.sin(sc.slot.h)], c = [sc.slot.c[0] + u[0] * (sc.slot.hx * 0.4), sc.slot.c[1] + u[1] * (sc.slot.hx * 0.4)]; const m = S.obb(c, sc.slot.h, 0.35, 0.25); fillPoly(g, cam.poly(S.corners(m), 0.004, 3), "rgba(58,209,124,.55)"); }
    // 장애물: 먼 것부터
    const objs = sc.obs.slice().sort((a, b) => cam.depth(b.b.c) - cam.depth(a.b.c));
    for (const o of objs) { if (o.car) drawCar3D(cam, g, o.b, o.color || "#8a8d94", S); else if (o.label === "기둥") drawPillar(cam, g, o.b, S); else drawWall(cam, g, o, S); }
    if (snap.hazard) drawHazard3D(cam, g, snap.hazard, pose, S);
    // 가이드라인(현대: 노란 조향 연동 · 파란 중립 점선 · 빨간 0.5 m)
    const pr = (st, range, n, col, lw, dash) => { const [L, R] = S.path(spec, pose, st, rev, range, n); strokeSegs(g, cam.path(L, 0.01), col, lw, dash); strokeSegs(g, cam.path(R, 0.01), col, lw, dash); };
    if (opts.guide !== false) {
      pr(0, 3.0, 2, "rgba(80,170,255,.95)", W / 320, [W / 90, W / 140]); pr(steer, 4.0, 40, "#ffd228", W / 190); pr(steer, 0.5, 6, "#ff4d4d", W / 190);
      // 거리 표시 0.5 / 1 / 2.3 m (노란선 사이 가로줄)
      const [a, b] = rev ? S.rearCorners(spec, pose) : S.frontCorners(spec, pose), dir = rev ? -1 : 1;
      g.font = `${Math.round(W / 46)}px IBM Plex Mono, monospace`; g.fillStyle = "rgba(255,255,255,.92)";
      for (const d of [0.5, 1.0, 2.3]) { const bk = [Math.cos(pose.h) * d * dir, Math.sin(pose.h) * d * dir]; const segs = cam.path([[a[0] + bk[0], a[1] + bk[1]], [b[0] + bk[0], b[1] + bk[1]]], 0.01);
        strokeSegs(g, segs, "rgba(255,255,255,.9)", W / 480); if (segs.length) { const e = segs[0][1]; g.fillText(d + "m", Math.max(segs[0][0][0], e[0]) + 8, e[1] + 6); } }
    }
    // 내 범퍼(아래 검은 띠 + 번호판 느낌) · 비네트 · 노이즈
    const bump = g.createLinearGradient(0, H * 0.86, 0, H); bump.addColorStop(0, "rgba(10,10,12,0)"); bump.addColorStop(0.35, "#101114"); bump.addColorStop(1, "#070708"); g.fillStyle = bump; g.fillRect(0, H * 0.86, W, H * 0.14);
    const vg = g.createRadialGradient(W / 2, H / 2, H * 0.45, W / 2, H / 2, H * 0.95); vg.addColorStop(0, "rgba(0,0,0,0)"); vg.addColorStop(1, "rgba(0,0,0,.55)"); g.fillStyle = vg; g.fillRect(0, 0, W, H);
    g.globalAlpha = 0.07; g.fillStyle = noise(g); g.fillRect(0, 0, W, H); g.globalAlpha = 1;
  }

  // ===================== 어라운드뷰 탑뷰 =====================
  /** 위에서 본 차 그림(차 좌표계: +x 앞). 실제 SVM 의 합성 차 이미지 자리. */
  function drawCarTop(g, X, Y, scale, b, hex, mine) {
    g.save(); g.translate(X(b.c[0]), Y(b.c[1])); g.rotate(-b.h); const L = b.hx * 2 * scale, Wd = b.hy * 2 * scale;
    const rr = (x, y, w, h, r, fill) => { g.beginPath(); g.roundRect(x, y, w, h, r); g.fillStyle = fill; g.fill(); };
    // 그림자
    rr(-L / 2 + 3, -Wd / 2 + 4, L, Wd, Wd * 0.18, "rgba(0,0,0,.4)");
    // 바퀴
    for (const [fx, fy] of [[0.3, 0.5], [0.3, -0.5], [-0.3, 0.5], [-0.3, -0.5]]) rr(fx * L - Wd * 0.17, fy * Wd - Wd * 0.07, Wd * 0.34, Wd * 0.14, 2, "#101114");
    // 차체
    const body = g.createLinearGradient(0, -Wd / 2, 0, Wd / 2); body.addColorStop(0, shade(hex, 0.8)); body.addColorStop(0.5, shade(hex, 1.08)); body.addColorStop(1, shade(hex, 0.75));
    rr(-L / 2, -Wd / 2, L, Wd, Wd * 0.22, body);
    // 유리: 앞 유리(앞쪽) · 뒷 유리, 지붕
    rr(-L * 0.26, -Wd * 0.38, L * 0.42, Wd * 0.76, Wd * 0.1, "#182230");
    rr(L * 0.05, -Wd * 0.35, L * 0.17, Wd * 0.7, Wd * 0.08, "#1a2838");
    rr(-L * 0.36, -Wd * 0.34, L * 0.12, Wd * 0.68, Wd * 0.08, "#1a2838");
    rr(-L * 0.17, -Wd * 0.33, L * 0.2, Wd * 0.66, Wd * 0.06, shade(hex, 1.15));
    // 사이드미러 · 등
    rr(L * 0.1, -Wd * 0.62, L * 0.05, Wd * 0.12, 2, shade(hex, 0.9)); rr(L * 0.1, Wd * 0.5, L * 0.05, Wd * 0.12, 2, shade(hex, 0.9));
    rr(L / 2 - L * 0.04, -Wd * 0.42, L * 0.035, Wd * 0.2, 2, "#fff6c8"); rr(L / 2 - L * 0.04, Wd * 0.22, L * 0.035, Wd * 0.2, 2, "#fff6c8");
    rr(-L / 2 + L * 0.005, -Wd * 0.42, L * 0.03, Wd * 0.2, 2, "#e03a30"); rr(-L / 2 + L * 0.005, Wd * 0.22, L * 0.03, Wd * 0.2, 2, "#e03a30");
    if (mine) { g.strokeStyle = "rgba(255,255,255,.7)"; g.lineWidth = 1.5; g.beginPath(); g.roundRect(-L / 2, -Wd / 2, L, Wd, Wd * 0.22); g.stroke(); }
    g.restore();
  }

  function drawTopView(g, W, H, S, spec, snap, sc, steer, opts) {
    const scale = Math.min(W / (sc.bounds.hx * 2), H / (sc.bounds.hy * 2)) * 0.96, ox = W / 2 - sc.bounds.c[0] * scale, oy = H / 2 + sc.bounds.c[1] * scale;
    const X = x => ox + x * scale, Y = y => oy - y * scale;
    const poly = (pts, fill, stroke, lw, dash) => { g.beginPath(); pts.forEach((p, i) => i ? g.lineTo(X(p[0]), Y(p[1])) : g.moveTo(X(p[0]), Y(p[1]))); g.closePath(); if (fill) { g.fillStyle = fill; g.fill(); } if (stroke) { g.setLineDash(dash || []); g.strokeStyle = stroke; g.lineWidth = lw; g.stroke(); g.setLineDash([]); } };
    const under = sc.env !== "outdoor";
    g.fillStyle = under ? "#2e2f34" : "#3c3e44"; g.fillRect(0, 0, W, H);
    g.globalAlpha = 0.12; g.fillStyle = noise(g); g.fillRect(0, 0, W, H); g.globalAlpha = 1;
    // 주차선
    sc.marks.forEach(m => poly(S.corners(m), null, "rgba(240,240,235,.5)", Math.max(1.5, 0.12 * scale)));
    poly(S.corners(sc.slot), snap.phase === "PARKED" ? "rgba(58,209,124,.3)" : "rgba(58,209,124,.08)", "rgba(250,250,245,.95)", Math.max(2, 0.12 * scale));
    { const u = [Math.cos(sc.slot.h), Math.sin(sc.slot.h)], c = [sc.slot.c[0] + u[0] * (sc.slot.hx * 0.4), sc.slot.c[1] + u[1] * (sc.slot.hx * 0.4)]; g.save(); g.translate(X(c[0]), Y(c[1])); g.font = `bold ${Math.max(12, 0.6 * scale)}px IBM Plex Sans KR, sans-serif`; g.fillStyle = "rgba(58,209,124,.8)"; g.textAlign = "center"; g.textBaseline = "middle"; g.fillText("P", 0, 0); g.restore(); }
    // 벽·연석·기둥·차
    for (const o of sc.obs) {
      if (o.car) { drawCarTop(g, X, Y, scale, o.b, o.color || "#8a8d94", false); continue; }
      if (o.label === "기둥") { poly(S.corners(o.b), "#8e8b84", "#f0c020", Math.max(2, 0.08 * scale)); continue; }
      poly(S.corners(o.b), o.label === "연석" ? "#8b8d93" : "#6f6e6a", null);
      if (o.label !== "연석") { const cs = S.corners(o.b); g.strokeStyle = "rgba(232,185,42,.9)"; g.lineWidth = 2; g.beginPath(); g.moveTo(X(cs[0][0]), Y(cs[0][1])); g.lineTo(X(cs[1][0]), Y(cs[1][1])); g.stroke(); }
    }
    // 가이드라인
    const pr = (st, range, n, col, lw, dash) => { const [L, R] = S.path(spec, snap.pose, st, snap.reverse, range, n); [L, R].forEach(P => { g.beginPath(); P.forEach((p, i) => i ? g.lineTo(X(p[0]), Y(p[1])) : g.moveTo(X(p[0]), Y(p[1]))); g.setLineDash(dash || []); g.strokeStyle = col; g.lineWidth = lw; g.lineCap = "round"; g.stroke(); g.setLineDash([]); }); };
    if (opts.guide !== false) { pr(0, 3, 2, "rgba(80,170,255,.9)", 2, [8, 6]); pr(steer, 4, 40, "#ffd228", 3.5); pr(steer, 0.5, 6, "#ff4d4d", 4); }
    // 내 차 + 앞바퀴 각
    drawCarTop(g, X, Y, scale, snap.body, "#2f6df6", true);
    const wr = S.wheelRad(spec, steer);
    [[spec.wheelbase, spec.width / 2 - 0.12, wr], [spec.wheelbase, -(spec.width / 2 - 0.12), wr]].forEach(([fx, sy, ang]) => {
      const cx = snap.pose.x + fx * Math.cos(snap.pose.h) - sy * Math.sin(snap.pose.h), cy = snap.pose.y + fx * Math.sin(snap.pose.h) + sy * Math.cos(snap.pose.h);
      poly(S.corners(S.obb([cx, cy], snap.pose.h + ang, 0.32, 0.11)), "#0c0d10", null); });
    // 돌발
    if (snap.hazard) { const hz = snap.hazard; g.beginPath(); g.arc(X(hz.pos[0]), Y(hz.pos[1]), Math.max(7, (hz.r + 0.1) * scale), 0, 7); g.fillStyle = "rgba(255,70,70,.9)"; g.fill();
      g.font = `${Math.max(14, 0.75 * scale)}px sans-serif`; g.textAlign = "center"; g.textBaseline = "middle"; g.fillStyle = "#fff"; g.fillText(hz.emoji, X(hz.pos[0]), Y(hz.pos[1])); g.textAlign = "left"; g.textBaseline = "alphabetic"; }
    // 비네트(합성 화면 느낌)
    const vg = g.createRadialGradient(W / 2, H / 2, Math.min(W, H) * 0.4, W / 2, H / 2, Math.max(W, H) * 0.75); vg.addColorStop(0, "rgba(0,0,0,0)"); vg.addColorStop(1, "rgba(0,0,0,.45)"); g.fillStyle = vg; g.fillRect(0, 0, W, H);
  }

  // ---- 핸들 아이콘(클러스터) ----
  function drawWheel(g, size, steer) { const c = size / 2, r = size * 0.42; g.clearRect(0, 0, size, size); g.save(); g.translate(c, c); g.rotate(steer * PI / 180);
    g.lineWidth = size * 0.085; g.strokeStyle = "#3a3f4b"; g.beginPath(); g.arc(0, 0, r - size * 0.04, 0, 7); g.stroke();
    g.strokeStyle = "#aab0bd"; g.lineWidth = size * 0.07; g.lineCap = "round"; g.beginPath(); g.moveTo(-r + size * 0.06, 0); g.lineTo(-size * 0.11, 0); g.moveTo(size * 0.11, 0); g.lineTo(r - size * 0.06, 0); g.moveTo(0, size * 0.1); g.lineTo(0, r - size * 0.06); g.stroke();
    g.fillStyle = "#2b303a"; g.beginPath(); g.arc(0, 0, size * 0.12, 0, 7); g.fill(); g.fillStyle = "#ffb340"; g.beginPath(); g.arc(0, -r + size * 0.04, size * 0.035, 0, 7); g.fill(); g.restore(); }

  // ---- 리플레이 ----
  function drawReplay(g, W, H, trace) { g.fillStyle = "#15181f"; g.fillRect(0, 0, W, H); const T = trace; if (!T.length) return; const tmax = T[T.length - 1].t || 1, X = t => 20 + (W - 40) * t / tmax, Y = s => H / 2 - (H / 2 - 14) * s / 540;
    g.strokeStyle = "#2a2f3a"; g.lineWidth = 1; [-540, 0, 540].forEach(v => { g.beginPath(); g.moveTo(20, Y(v)); g.lineTo(W - 20, Y(v)); g.stroke(); });
    g.fillStyle = "#9aa3b2"; g.font = "20px IBM Plex Mono, monospace"; g.fillText("+540°", 24, Y(540) + 22); g.fillText("0°", 24, Y(0) - 6); g.fillText("−540°", 24, Y(-540) - 6);
    T.forEach((p, i) => { if (i === 0) return; g.fillStyle = p.v < 0 ? "rgba(255,179,64,.08)" : "rgba(74,158,255,.10)"; g.fillRect(X(T[i - 1].t), 14, Math.max(1, X(p.t) - X(T[i - 1].t)), H - 28); });
    g.strokeStyle = "#ffb340"; g.lineWidth = 3; g.beginPath(); T.forEach((p, i) => i ? g.lineTo(X(p.t), Y(p.steer)) : g.moveTo(X(p.t), Y(p.steer))); g.stroke(); }

  /** 시작 화면 미리보기: 시작 자세로 탑뷰 한 장 */
  function drawPreview(g, W, H, S, spec, sc) { const snap = { pose: sc.start, body: S.body(spec, sc.start), reverse: !sc.forward, phase: "READY", hazard: null }; drawTopView(g, W, H, S, spec, snap, sc, 0, { guide: false }); }
  return { drawCamera, drawTopView, drawPreview, drawWheel, drawReplay, CAM };
});
