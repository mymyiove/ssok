// 렌더링 전수 점검(PM 2026-10-28 "니가 보낸 시안부터 봐라"): 15 스테이지를 시연으로 돌려 4 시점씩 찍고 모음 그림을 만든다.
// 고칠 때마다 돌려 사람이 먼저 본다(카메라 · 미러 · 어라운드뷰 깨짐). 쓰는 법: node tests/render-sweep.cjs <출력 폴더> [가로 세로]
const { chromium } = require("playwright"), http = require("http"), fs = require("fs"), path = require("path");
const ROOT = path.join(__dirname, "..", "site"), TMP = path.join(ROOT, "preview", "__cap.html"), OUT = process.argv[2] || "sweep";
const [W, H] = [+(process.argv[3] || 1600), +(process.argv[4] || 900)];
fs.mkdirSync(OUT, { recursive: true });
fs.writeFileSync(TMP, fs.readFileSync(path.join(ROOT, "preview", "index.html"), "utf8").replace("const ENG = { nodes: null };", "const ENG = { nodes: null }; window.__ssok = S; window.SSOK = SSOK;"));
const T = { ".html": "text/html", ".js": "text/javascript", ".svg": "image/svg+xml" };
const srv = http.createServer((q, r) => { const f = path.join(ROOT, decodeURIComponent(q.url.split("?")[0])); fs.readFile(f, (e, d) => { if (e) { r.writeHead(404); return r.end(); } r.writeHead(200, { "content-type": T[path.extname(f)] || "application/octet-stream" }); r.end(d); }); });
(async () => { await new Promise(ok => srv.listen(0, "127.0.0.1", ok)); const U = `http://127.0.0.1:${srv.address().port}/preview/__cap.html`; const b = await chromium.launch(); const errs = [];
  const pg = await b.newPage({ viewport: { width: W, height: H } }); pg.on("pageerror", e => errs.push(e.message));
  await pg.goto(U); await pg.waitForTimeout(800); await pg.click("#opening"); await pg.waitForTimeout(600);
  const files = [];
  for (let i = 0; i < 15; i++) {
    await pg.evaluate(() => { if (!document.getElementById("setup").classList.contains("show")) document.getElementById("dHome").click(); }); await pg.waitForTimeout(250);
    await pg.evaluate(() => { const a = document.getElementById("ask"); if (a.classList.contains("show")) document.getElementById("askYes").click(); }); await pg.waitForTimeout(150);
    await pg.click(`.stg[data-i="${i}"]`); await pg.waitForTimeout(150); await pg.evaluate(() => document.getElementById("startDemo").click()); await pg.waitForTimeout(300);
    const t0 = Date.now(); let k = 0;
    for (const tt of [4, 9, 14, 19]) { while (Date.now() - t0 < tt * 1000) await pg.waitForTimeout(100); const f = path.join(OUT, `s${String(i + 1).padStart(2, "0")}_${k++}.png`); await pg.screenshot({ path: f }); files.push(f); if (await pg.evaluate(() => window.__ssok.demoDone)) break; }
  }
  // 모음 그림(스테이지 4개씩 한 장): 브라우저 캔버스로 이어 붙인다(파이썬 없이)
  for (let sh = 0; sh < 4; sh++) { const sts = [...Array(4)].map((_, j) => sh * 4 + j + 1).filter(n => n <= 15);
    const rows = sts.map(st => files.filter(f => path.basename(f).startsWith(`s${String(st).padStart(2, "0")}_`)).map(f => "data:image/png;base64," + fs.readFileSync(f).toString("base64")));
    const p2 = await b.newPage({ viewport: { width: 4 * 405, height: rows.length * 230 } });
    await p2.setContent(`<body style="margin:0;background:#888;display:grid;grid-template-columns:repeat(4,400px);gap:5px">${rows.map(r => [0, 1, 2, 3].map(c => r[c] ? `<img src="${r[c]}" style="width:400px;height:225px">` : "<div></div>").join("")).join("")}</body>`);
    await p2.waitForTimeout(300); await p2.screenshot({ path: path.join(OUT, `sheet${sh}.png`) }); await p2.close(); }
  await b.close(); srv.close(); fs.unlinkSync(TMP);
  console.log(errs.length ? "스크립트 오류: " + [...new Set(errs)].join(" / ") : "스크립트 오류 없음", "· 모음:", path.join(OUT, "sheet0~3.png"));
})();
