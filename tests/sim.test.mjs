// node tests/sim.test.mjs — 스테이지 기하 검증: 시작 자세 충돌 없음 · 공식대로 조작하면 들어가는지 · 돌발 규칙.
import { readFileSync } from "node:fs";
// 저장소 package.json 이 type: module 이라 sim.js(UMD)를 직접 require 하지 못한다 → 소스를 읽어 CommonJS 로 평가
const mod = { exports: {} };
new Function("module", "exports", readFileSync(new URL("../site/preview/sim.js", import.meta.url), "utf8"))(mod, mod.exports);
const S = mod.exports;

const rad = d => d * Math.PI / 180;
let fails = 0;
const ok = (c, m) => { if (!c) { fails++; console.log("  ✗ " + m); } };

/** 공식 조작(연수 공식)으로 끝까지 가 본다. 고급(어긋난 시작)은 offset 만큼 직진으로 맞춘 뒤 같은 공식. */
function solve(spec, sc, stage, opts = {}) {
  const eng = new S.Engine(spec, sc, 11), T = S.TIERS[stage.tier], R = S.minR(spec);
  const sideSign = stage.side === "L" ? -1 : 1, dt = 0.02;
  let phase = 0, hitLog = [];
  for (let t = 0; t < 240; t += dt) {
    const hd = S.headingDiff(sc, eng.pose.h), trav = eng.travelled;
    let steer = 0, signal = "OFF";
    const brakeForHazard = opts.stopForHazard && eng.hazard;
    if (stage.kind === "perp_rev") {
      signal = "RIGHT";
      if (phase === 0 && trav >= T.offset) phase = 1;
      if (phase === 1) { steer = 540 * sideSign; if (hd < rad(0.7)) phase = 2; }
      if (phase === 2) steer = 0;
    } else if ((stage.kind === "perp_fwd" && stage.neighbors === "none") || stage.kind === "angled") {
      signal = "LEFT";
      if (phase === 0 && trav >= 1.0 + T.offset) phase = 1;
      if (phase === 1) { steer = 540 * sideSign; if (hd < rad(0.7)) phase = 2; }
      if (phase === 2) steer = 0;
    } else if (stage.kind === "parallel") { // parallel: 45° 두 번 (뒷차축 = 앞차 뒷범퍼 선에서 시작, 고급은 어긋난 0.6 m 를 직진으로 맞춘 뒤)
      signal = "RIGHT";
      if (phase === 0 && trav >= T.offset) phase = 1;
      if (phase === 1) { steer = 540 * sideSign; if (hd > rad(44.5)) phase = 2; }
      if (phase === 2) { steer = -540 * sideSign; if (hd < rad(0.7)) phase = 3; }
      if (phase === 3) steer = 0;
    }
    if (stage.kind === "perp_fwd" && stage.neighbors !== "none") { // 전면(옆 차): a° 까지 꺾고 → 왼쪽 가득으로 d m 빼고 → 다시 오른쪽 가득 (한 번 빼기 공식)
      const P = opts.fwdParams || { a: 40, s: 2.5, d: 2.0 };
      signal = "LEFT"; const h = -eng.pose.h * sideSign;
      if (phase === 0 && trav >= 1.0 + P.s + T.offset) phase = 1;
      if (phase === 1) { steer = 540 * sideSign; if (h >= rad(P.a)) { phase = 2; eng._t0 = trav; } }
      if (phase === 2) { steer = -540 * sideSign; signal = "RIGHT"; if (trav - eng._t0 >= P.d) phase = 3; }
      if (phase === 3) { steer = 540 * sideSign; if (hd < rad(0.7)) phase = 4; }
      if (phase === 4) steer = 0;
    }
    const snap = eng.tick({ steer, signal, brake: !!brakeForHazard, gearP: true }, dt);
    if (snap.phase === "HIT" && !hitLog.includes(snap.hint)) hitLog.push(snap.hint);
    if (snap.phase === "PARKED") return { parked: true, hits: eng.hits, t: eng.elapsed, haz: eng.hazStats, hitLog };
    if (snap.phase === "HIT" && !opts.keepGoing) return { parked: false, hits: eng.hits, t: eng.elapsed, haz: eng.hazStats, hitLog };
  }
  return { parked: false, hits: eng.hits, t: eng.elapsed, haz: eng.hazStats, hitLog, timeout: true };
}

for (const specKey of ["AVANTE", "GRANDEUR"]) {
  const spec = S.SPECS[specKey];
  console.log(`\n== ${spec.name}`);
  for (const st of S.STAGES) {
    const sc = S.build(spec, st);
    const b = S.body(spec, sc.start);
    ok(!S.collides(sc, b), `${st.no} 시작 자세가 장애물과 겹침`);
    ok(!S.parked(sc, b, sc.start.h), `${st.no} 시작부터 주차 판정`);
    ok(sc.bounds.hx < 12 && sc.bounds.hy < 10, `${st.no} 화면 범위 과대 ${sc.bounds.hx.toFixed(1)}×${sc.bounds.hy.toFixed(1)}`);
    let r = solve(spec, sc, st, { stopForHazard: true });
    if (!r.parked && st.kind === "perp_fwd") for (const P of [{ a: 35, s: 2.75, d: 2.0 }, { a: 40, s: 2.25, d: 1.75 }]) { r = solve(spec, sc, st, { stopForHazard: true, fwdParams: P }); if (r.parked) { r.params = P; break; } }
    const line = `${String(st.no).padStart(2)} ${sc.title.padEnd(44)} ${r.parked ? "쏙" : "실패"} 충돌 ${r.hits} ${r.t.toFixed(1)}s` + (r.haz.total ? ` 돌발 ${r.haz.total}/멈춤 ${r.haz.avoided}/사고 ${r.haz.hit}` : "") + (r.hitLog.length ? "  " + r.hitLog.join(" | ") : "") + (r.params ? `  (a${r.params.a} s${r.params.s} d${r.params.d})` : "");
    console.log("  " + line);
    // 초급·중급은 공식대로 하면 한 번에 들어가야 한다. 고급은 offset 만큼 직진으로 맞추면 들어가야 한다(모두 같은 풀이).
    ok(r.parked && r.hits === 0, `${st.no} 공식 풀이로 안 들어감`);
    if (st.hazards) ok(r.haz.total === st.hazards && r.haz.hit === 0, `${st.no} 돌발이 ${st.hazards}회 나오고 멈추면 사고 0 이어야`);
  }
  // 돌발을 무시하고 계속 가면 사고가 나야 한다 (8번: 보행자)
  const st8 = S.STAGES[7], r8 = solve(spec, S.build(spec, st8), st8, { stopForHazard: false, keepGoing: true });
  console.log(`  8번 돌발 무시: 사고 ${r8.haz.hit} / 돌발 ${r8.haz.total}`);
  ok(r8.haz.hit >= 1, "돌발을 무시했는데 사고가 안 남");
}

// 자유 연습 조합 전부: 시작 자세만 검사(72 조합)
let n = 0;
for (const kind of Object.keys(S.KINDS)) for (const tier of Object.keys(S.TIERS)) for (const nb of Object.keys(S.NEIGH)) for (const side of ["R", "L"]) {
  for (const specKey of ["AVANTE", "GRANDEUR"]) { const spec = S.SPECS[specKey], sc = S.build(spec, { kind, tier, neighbors: nb, side, hazards: 0 });
    ok(!S.collides(sc, S.body(spec, sc.start)), `자유 ${kind}/${tier}/${nb}/${side}/${specKey} 시작 충돌`); n++; }
}
console.log(`\n자유 조합 ${n}개 시작 자세 확인`);
// 랜덤 상황(seed): 시작 자세가 흔들려도 충돌 없이 시작해야 한다 — 15 스테이지 × 2 차종 × 20 seed
let bad = 0, cnt = 0;
for (const specKey of ["AVANTE", "GRANDEUR"]) for (const st of S.STAGES) for (let seed = 1; seed <= 20; seed++) { const spec = S.SPECS[specKey], sc = S.build(spec, st, seed * 7919 + st.no);
  cnt++; if (S.collides(sc, S.body(spec, sc.start))) { bad++; console.log(`  ✗ 랜덤 ${st.no} ${specKey} seed ${seed} 시작 충돌`); } }
ok(bad === 0, `랜덤 상황 시작 충돌 ${bad}/${cnt}`);
console.log(`랜덤 상황 ${cnt}개 시작 자세 확인`);
// 시연 운전자: 15 스테이지 × 2 차종 × 랜덤 seed 3개(옆 차 자리·색은 흔들리고 시작은 공식 자리) 모두 충돌 없이 들어가야 한다
let dn = 0, dbad = 0;
for (const specKey of ["AVANTE", "GRANDEUR"]) for (const st of S.STAGES) for (const seed of [null, 101, 2024]) {
  const spec = S.SPECS[specKey], sc = S.build(spec, st, seed, { noStartJitter: true }), eng = new S.Engine(spec, sc, seed || 5), demo = new S.Demo(S, spec, sc, st);
  let parked = false, t = 0; const dt = 0.02;
  while (t < 240) { t += dt; const inp = demo.step(eng, dt); const sn = eng.tick({ steer: inp.steer, signal: inp.signal, brake: inp.brake, gearP: true }, dt); if (sn.phase === "PARKED") { parked = true; break; } if (sn.phase === "HIT") break; }
  dn++; if (!parked || eng.hits) { dbad++; console.log(`  ✗ 시연 ${st.no} ${specKey} seed ${seed}: ${parked ? "들어갔지만 충돌" : "실패"} (${eng.hint})`); }
}
ok(dbad === 0, `시연 실패 ${dbad}/${dn}`); console.log(`시연 ${dn}회 확인`);
console.log(fails ? `\n실패 ${fails}` : "\n모두 통과");
process.exit(fails ? 1 : 0);
