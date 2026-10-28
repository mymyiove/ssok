// 렌더링 전수 점검(PM 2026-10-28 "단 한 곳도 깨져선 안 됨"): 15 스테이지 시연(+ 실수 보기, 차종 3개)을 2초 간격으로 끝까지 돌리며
//   ① 그리는 중 생긴 비정상 면(숫자가 아닌 좌표 · 화면 폭 6배 밖) 개수를 장면마다 기록하고
//   ② 카메라 · 미러를 크게 잘라 스테이지마다 모음 그림을 만든다(사람이 한 장씩 본다).
// 쓰는 법: node tests/render-sweep.cjs <출력 폴더> [차종 AVANTE|GRANDEUR|TUCSON|all] [간격 초]
const { chromium } = require("playwright"), http = require("http"), fs = require("fs"), path = require("path");
const ROOT = path.join(__dirname, "..", "site"), TMP = path.join(ROOT, "preview", "__cap.html"), OUT = process.argv[2] || "sweep";
const CARS = (process.argv[3] || "AVANTE") === "all" ? ["AVANTE", "GRANDEUR", "TUCSON"] : [process.argv[3] || "AVANTE"], STEP = +(process.argv[4] || 2);
fs.mkdirSync(OUT, { recursive: true });
fs.writeFileSync(TMP, fs.readFileSync(path.join(ROOT, "preview", "index.html"), "utf8").replace("const ENG = { nodes: null };", "const ENG = { nodes: null }; window.__ssok = S; window.SSOK = SSOK; window.__V = V;"));
const T = { ".html": "text/html", ".js": "text/javascript", ".svg": "image/svg+xml" };
const srv = http.createServer((q, r) => { const f = path.join(ROOT, decodeURIComponent(q.url.split("?")[0])); fs.readFile(f, (e, d) => { if (e) { r.writeHead(404); return r.end(); } r.writeHead(200, { "content-type": T[path.extname(f)] || "application/octet-stream" }); r.end(d); }); });
(async () => {
  await new Promise(ok => srv.listen(0, "127.0.0.1", ok)); const U = `http://127.0.0.1:${srv.address().port}/preview/__cap.html`;
  const b = await chromium.launch(); const errs = [], bad = [], shots = {};
  const pg = await b.newPage({ viewport: { width: 1600, height: 900 } }); pg.on("pageerror", e => errs.push(e.message));
  await pg.goto(U); await pg.waitForTimeout(800); await pg.click("#opening"); await pg.waitForTimeout(600);
  const home = async () => { await pg.evaluate(() => { if (!document.getElementById("setup").classList.contains("show")) document.getElementById("dHome").click(); }); await pg.waitForTimeout(200);
    await pg.evaluate(() => { const a = document.getElementById("ask"); if (a.classList.contains("show")) document.getElementById("askYes").click(); }); await pg.waitForTimeout(150); };
  const runs = [];
  for (const car of CARS) for (let i = 0; i < 15; i++) runs.push({ car, i, mode: "startDemo" });
  for (const i of [0, 1, 5, 7, 10, 11, 14]) runs.push({ car: CARS[0], i, mode: "startMistake" });   // 실수 보기(후면 직각만)
  for (const run of runs) {
    await home(); await pg.evaluate(c => { const bt = document.querySelector(`#carSeg [data-v="${c}"]`); if (bt) bt.click(); }, run.car); await pg.waitForTimeout(100);
    await pg.click(`.stg[data-i="${run.i}"]`); await pg.waitForTimeout(150);
    const ok = await pg.evaluate(m => { const e = document.getElementById(m); if (!e || e.classList.contains("hidden")) return false; e.click(); return true; }, run.mode); if (!ok) continue;
    await pg.waitForTimeout(300); const key = `${run.car}_${run.mode === "startMistake" ? "mis" : "demo"}_s${String(run.i + 1).padStart(2, "0")}`; shots[key] = [];
    const t0 = Date.now();
    for (let tt = STEP; tt <= 40; tt += STEP) {
      while (Date.now() - t0 < tt * 1000) await pg.waitForTimeout(80);
      await pg.evaluate(() => { const s = window.__V.STATS; s.nan = 0; s.wild = 0; s.faces = 0; }); await pg.waitForTimeout(120);
      const st = await pg.evaluate(() => ({ ...window.__V.STATS }));
      if (st.nan || st.wild) bad.push(`${key} ${tt}s: 비정상 면 nan ${st.nan} · wild ${st.wild}`);
      const f = path.join(OUT, `${key}_${String(tt).padStart(2, "0")}.png`);
      const cam = await pg.locator(".view.cam").boundingBox(), mir = await pg.locator("#mirBox").boundingBox(), top = await pg.locator(".view.topv").boundingBox();
      await pg.screenshot({ path: f + ".cam.png", clip: cam }); await pg.screenshot({ path: f + ".mir.png", clip: mir }); await pg.screenshot({ path: f + ".top.png", clip: top });
      shots[key].push([f + ".cam.png", f + ".mir.png", f + ".top.png", tt]);
      if (await pg.evaluate(() => window.__ssok.demoDone)) break;
    }
  }
  // 모음 그림: 한 줄 = 한 시점(카메라 | 미러 | 어라운드뷰), 스테이지마다 한 장
  for (const [key, list] of Object.entries(shots)) {
    const img = p => "data:image/png;base64," + fs.readFileSync(p).toString("base64");
    const p2 = await b.newPage({ viewport: { width: 1520, height: 100 } });
    await p2.setContent(`<body style="margin:0;background:#777;font:14px sans-serif;color:#fff">${list.map(([c, m, t, tt]) => `<div style="display:flex;gap:4px;margin:0 0 4px;align-items:center"><b style="width:36px">${tt}s</b><img src="${img(c)}" style="width:820px"><img src="${img(m)}" style="width:300px"><img src="${img(t)}" style="width:340px"></div>`).join("")}</body>`);
    await p2.waitForTimeout(200); await p2.screenshot({ path: path.join(OUT, `${key}.png`), fullPage: true }); await p2.close();
    for (const [c, m, t] of list) { fs.unlinkSync(c); fs.unlinkSync(m); fs.unlinkSync(t); }
  }
  await b.close(); srv.close(); fs.unlinkSync(TMP);
  console.log(errs.length ? "스크립트 오류: " + [...new Set(errs)].join(" / ") : "스크립트 오류 없음");
  console.log(bad.length ? "비정상 면:\n" + bad.join("\n") : "비정상 면 없음");
  console.log("모음:", Object.keys(shots).length, "장 →", OUT);
  process.exit(errs.length || bad.length ? 1 : 0);
})();
