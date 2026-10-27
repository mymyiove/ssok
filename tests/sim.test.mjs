// node tests/sim.test.mjs — 스테이지 기하 검증: 시작 자세 충돌 없음 · 공식대로 조작하면 들어가는지 · 돌발 규칙.
import { readFileSync } from "node:fs";
// 저장소 package.json 이 type: module 이라 sim.js(UMD)를 직접 require 하지 못한다 → 소스를 읽어 CommonJS 로 평가
const mod = { exports: {} };
new Function("module", "exports", readFileSync(new URL("../site/preview/sim.js", import.meta.url), "utf8"))(mod, mod.exports);
const S = mod.exports;
// 접근 단계가 생겨 시연·엔진 입력에 hazardLights 를 넣는다(비상등 연습)

const rad = d => d * Math.PI / 180;
let fails = 0;
const ok = (c, m) => { if (!c) { fails++; console.log("  ✗ " + m); } };

/** 공식 조작(연수 공식)으로 끝까지 가 본다. 고급(어긋난 시작)은 offset 만큼 직진으로 맞춘 뒤 같은 공식. */
function solve(spec, sc, stage, opts = {}) {
  const eng = new S.Engine(spec, sc, 11), T = S.TIERS[stage.tier], R = S.minR(spec);
  eng.tick({ steer: 0, signal: "RIGHT", brake: true, gearP: true, hazardLights: true }, 0.02);   // 브레이크를 밟은 채 시작(비상등), 밟은 채 D
  const sig = g => g === "D" ? "RIGHT" : g === "R" ? "LEFT" : "OFF";
  let approaching = true;
  const sideSign = stage.side === "L" ? -1 : 1, dt = 0.02;
  let phase = 0, hitLog = [];
  for (let t = 0; t < 240; t += dt) {
    const hd = S.headingDiff(sc, eng.pose.h); if (eng._trav0 === undefined && !approaching) eng._trav0 = eng.travelled; const trav = eng.travelled - (eng._trav0 || 0);
    let steer = 0, signal = "OFF";
    const brakeForHazard = opts.stopForHazard && eng.hazard;
    if (approaching) { if (eng.pose.x < sc.formulaX) { const sn = eng.tick({ steer: 0, signal: "RIGHT", brake: !!brakeForHazard || eng.gear !== "D", gearP: true, hazardLights: true }, dt); if (sn.phase === "HIT" && !opts.keepGoing) return { parked: false, hits: eng.hits, t: eng.elapsed, haz: eng.hazStats, hitLog: [sn.hint] }; continue; } approaching = false; }
    if (stage.kind === "perp_rev") {
      signal = "LEFT";
      if (phase === 0 && trav >= T.offset) phase = 1;
      if (phase === 1) { steer = 540 * sideSign; if (hd < rad(0.7)) phase = 2; }
      if (phase === 2) steer = 0;
    } else if ((stage.kind === "perp_fwd" && stage.neighbors === "none") || stage.kind === "angled") {
      signal = "RIGHT";
      if (phase === 0) phase = 1;
      if (phase === 1) { steer = 540 * sideSign; if (hd < rad(0.7)) phase = 2; }
      if (phase === 2) steer = 0;
    } else if (stage.kind === "parallel") { // parallel: 45° 두 번 (뒷차축 = 앞차 뒷범퍼 선에서 시작, 고급은 어긋난 0.6 m 를 직진으로 맞춘 뒤)
      signal = "LEFT";
      if (phase === 0 && trav >= T.offset) phase = 1;
      if (phase === 1) { steer = 540 * sideSign; if (hd > rad(44.5)) phase = 2; }
      if (phase === 2) { steer = -540 * sideSign; if (hd < rad(0.7)) phase = 3; }
      if (phase === 3) steer = 0;
    }
    if (stage.kind === "perp_fwd" && stage.neighbors !== "none") { // 전면(옆 차): a° 까지 꺾고 → 왼쪽 가득으로 d m 빼고 → 다시 오른쪽 가득 (한 번 빼기 공식)
      const P = opts.fwdParams || { a: 35, s: 2.5, d: 2.0 };
      signal = "RIGHT"; const h = -eng.pose.h * sideSign;
      if (phase === 0) phase = 1;
      if (phase === 1) { steer = 540 * sideSign; if (h >= rad(P.a)) { phase = 2; eng._t0 = trav; } }
      if (phase === 2) { steer = -540 * sideSign; signal = "LEFT"; if (trav - eng._t0 >= P.d) phase = 3; }
      if (phase === 3) { steer = 540 * sideSign; if (hd < rad(0.7)) phase = 4; }
      if (phase === 4) steer = 0;
    }
    // 기어를 바꿀 땐 밟고 바꾼다(시프트 록). 들어왔으면 브레이크 밟고 N 으로 마무리.
    const inp = eng.inSlot ? { steer, signal: "OFF", brake: true } : { steer, signal, brake: !!brakeForHazard || sig(eng.gear) !== signal };
    const snap = eng.tick({ ...inp, gearP: true, hazardLights: true }, dt);
    if (snap.phase === "HIT" && !hitLog.includes(snap.hint)) hitLog.push(snap.hint);
    if (snap.phase === "PARKED") return { parked: true, hits: eng.hits, t: eng.elapsed, haz: eng.hazStats, hitLog };
    if (snap.phase === "HIT" && !opts.keepGoing) return { parked: false, hits: eng.hits, t: eng.elapsed, haz: eng.hazStats, hitLog };
  }
  return { parked: false, hits: eng.hits, t: eng.elapsed, haz: eng.hazStats, hitLog, timeout: true };
}

for (const specKey of ["AVANTE", "GRANDEUR", "TUCSON"]) {
  const spec = S.SPECS[specKey];
  console.log(`\n== ${spec.name}`);
  for (const st of S.STAGES) {
    const sc = S.build(spec, st);
    const b = S.body(spec, sc.start);
    ok(!S.collides(sc, b), `${st.no} 시작 자세가 장애물과 겹침`);
    ok(!S.parked(sc, b, sc.start.h), `${st.no} 시작부터 주차 판정`);
    ok(sc.bounds.hx < 14.5 && sc.bounds.hy < 10, `${st.no} 화면 범위 과대 ${sc.bounds.hx.toFixed(1)}×${sc.bounds.hy.toFixed(1)}`);
    let r = solve(spec, sc, st, { stopForHazard: true });
    if (!r.parked && st.kind === "perp_fwd") for (const P of [{ a: 40, s: 2.5, d: 2.0 }, { a: 33, s: 2.5, d: 2.0 }]) { r = solve(spec, sc, st, { stopForHazard: true, fwdParams: P }); if (r.parked) { r.params = P; break; } }
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
  for (const specKey of ["AVANTE", "GRANDEUR", "TUCSON"]) { const spec = S.SPECS[specKey], sc = S.build(spec, { kind, tier, neighbors: nb, side, hazards: 0 });
    ok(!S.collides(sc, S.body(spec, sc.start)), `자유 ${kind}/${tier}/${nb}/${side}/${specKey} 시작 충돌`); n++; }
}
console.log(`\n자유 조합 ${n}개 시작 자세 확인`);
// 랜덤 상황(seed): 시작 자세가 흔들려도 충돌 없이 시작해야 한다 — 15 스테이지 × 2 차종 × 20 seed
let bad = 0, cnt = 0;
for (const specKey of ["AVANTE", "GRANDEUR", "TUCSON"]) for (const st of S.STAGES) for (let seed = 1; seed <= 20; seed++) { const spec = S.SPECS[specKey], sc = S.build(spec, st, seed * 7919 + st.no);
  cnt++; if (S.collides(sc, S.body(spec, sc.start))) { bad++; console.log(`  ✗ 랜덤 ${st.no} ${specKey} seed ${seed} 시작 충돌`); } }
ok(bad === 0, `랜덤 상황 시작 충돌 ${bad}/${cnt}`);
console.log(`랜덤 상황 ${cnt}개 시작 자세 확인`);
// 시연 운전자: 15 스테이지 × 2 차종 × 랜덤 seed 3개(옆 차 자리·색은 흔들리고 시작은 공식 자리) 모두 충돌 없이 들어가야 한다
let dn = 0, dbad = 0;
for (const specKey of ["AVANTE", "GRANDEUR", "TUCSON"]) for (const st of S.STAGES) for (const seed of [null, 101, 2024]) {
  const spec = S.SPECS[specKey], sc = S.build(spec, st, seed, { noStartJitter: true }), eng = new S.Engine(spec, sc, seed || 5), demo = new S.Demo(S, spec, sc, st);
  let parked = false, t = 0; const dt = 0.02;
  while (t < 240) { t += dt; const inp = demo.step(eng, dt); const sn = eng.tick({ steer: inp.steer, signal: inp.signal, brake: inp.brake, gearP: true, hazardLights: inp.hazardLights }, dt); if (sn.phase === "PARKED") { parked = true; break; } if (sn.phase === "HIT") break; }
  dn++; if (!parked || eng.hits) { dbad++; console.log(`  ✗ 시연 ${st.no} ${specKey} seed ${seed}: ${parked ? "들어갔지만 충돌" : "실패"} (${eng.hint})`); }
}
ok(dbad === 0, `시연 실패 ${dbad}/${dn}`); console.log(`시연 ${dn}회 확인`);
// 브레이크를 밟기 전엔 깜빡이를 올려도 안 움직인다
{ const spec = S.SPECS.AVANTE, sc = S.build(spec, S.STAGES[0]), eng = new S.Engine(spec, sc, 1); for (let i = 0; i < 50; i++) eng.tick({ steer: 0, signal: "RIGHT", brake: false, gearP: true }, 0.02);
  ok(eng.travelled === 0 && !eng.started && eng.gear === "N", "브레이크 없이 출발하거나 기어가 들어감"); eng.tick({ steer: 0, signal: "RIGHT", brake: true, gearP: true }, 0.02); ok(eng.gear === "D", "밟고 D 를 넣었는데 안 들어감");
  for (let i = 0; i < 50; i++) eng.tick({ steer: 0, signal: "RIGHT", brake: false, gearP: true }, 0.02); ok(eng.travelled > 0.5, "브레이크를 뗐는데 크리핑하지 않음");
  // 시프트 록: 안 밟고 R → 거부, D 그대로. 밟고 R → 들어감. N 은 언제나.
  let sn = eng.tick({ steer: 0, signal: "LEFT", brake: false, gearP: true }, 0.02); ok(sn.gearRefused && sn.gear === "D" && !sn.reverse, "안 밟고 R 이 들어감");
  sn = eng.tick({ steer: 0, signal: "LEFT", brake: true, gearP: true }, 0.02); ok(!sn.gearRefused && sn.gear === "R" && sn.reverse, "밟고 R 을 넣었는데 안 들어감(카메라 후방)");
  sn = eng.tick({ steer: 0, signal: "OFF", brake: false, gearP: true }, 0.02); ok(sn.gear === "N", "N 은 언제나 들어가야");
  // 살살 밟기: 깊이 0.3 이면 절반 속도, 0.6 부터 정지
  eng.tick({ steer: 0, signal: "RIGHT", brake: true, gearP: true }, 0.02); const t0 = eng.travelled; for (let i = 0; i < 50; i++) eng.tick({ steer: 0, signal: "RIGHT", brake: 0.3, gearP: true }, 0.02);
  ok(Math.abs((eng.travelled - t0) - 0.3) < 0.02, `살살(0.3) 1초에 ${(eng.travelled - t0).toFixed(2)} m — 0.30 이어야`); const t1 = eng.travelled; for (let i = 0; i < 20; i++) eng.tick({ steer: 0, signal: "RIGHT", brake: 0.6, gearP: true }, 0.02); ok(eng.travelled === t1, "0.6 이면 정지여야");
  console.log("브레이크 · 시프트 록 · 살살 규칙 확인"); }
// 들어온 뒤 브레이크 + N 이어야 끝: 1번 스테이지를 공식대로 넣되 N 을 안 주면 PARKED 가 아니다
{ const spec = S.SPECS.AVANTE, sc = S.build(spec, S.STAGES[0]), eng = new S.Engine(spec, sc, 1); const dt = 0.02;
  eng.tick({ steer: 0, signal: "RIGHT", brake: true, gearP: true, hazardLights: true }, dt);
  while (eng.pose.x < sc.formulaX) eng.tick({ steer: 0, signal: "RIGHT", brake: false, gearP: true, hazardLights: true }, dt);
  eng.tick({ steer: 540, signal: "LEFT", brake: true, gearP: true, hazardLights: true }, dt); let ph = 1, n = 0;
  while (!eng.inSlot && n++ < 3000) { const hd = S.headingDiff(sc, eng.pose.h); if (ph === 1 && hd < rad(0.7)) ph = 2; eng.tick({ steer: ph === 1 ? 540 : 0, signal: "LEFT", brake: false, gearP: true, hazardLights: true }, dt); }
  ok(eng.inSlot && eng.phase !== "PARKED", "칸에 들어왔는데 N 없이 끝남"); eng.tick({ steer: 0, signal: "LEFT", brake: true, gearP: true }, dt); ok(eng.phase !== "PARKED", "R 인 채 밟기만 했는데 끝남");
  const fin = eng.tick({ steer: 0, signal: "OFF", brake: true, gearP: true }, dt); ok(fin.phase === "PARKED", "브레이크 + N 인데 안 끝남"); console.log("브레이크 + N 마무리 확인"); }
console.log(fails ? `\n실패 ${fails}` : "\n모두 통과");
process.exit(fails ? 1 : 0);
