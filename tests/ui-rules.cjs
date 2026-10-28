// 쏙 화면 규칙 검사(docs/rules.md): 시안을 1280×720(차 화면 dp 기준)으로 띄워 잰다.
// C1 글자 12px 이상 · C2 대비 4.5:1 이상 · C3 누름 영역 64(가운데에서 상하좌우 31px 지점이 그 버튼에 닿는지 — 둥근 모서리는 크롬이 누름으로 안 친다) · R3 버튼 이름 5글자 이하 · R10 줄표 금지
// 쓰는 법: npm run test:ui   (또는 node tests/ui-rules.cjs [주소])
const { chromium } = require("playwright");
const http = require("http"), fs = require("fs"), path = require("path");
const ROOT = path.join(__dirname, "..", "site"), TYPES = { ".html": "text/html", ".js": "text/javascript", ".svg": "image/svg+xml", ".json": "application/json", ".webmanifest": "application/manifest+json", ".m4a": "audio/mp4", ".mp3": "audio/mpeg", ".jpg": "image/jpeg", ".png": "image/png", ".css": "text/css" };
// 주소를 안 주면 site/ 를 스스로 띄운다(서버 따로 안 켜도 됨)
const srv = process.argv[2] ? null : http.createServer((q, r) => { let f = path.join(ROOT, decodeURIComponent(q.url.split("?")[0])); if (!f.startsWith(ROOT)) { r.writeHead(403); return r.end(); } if (fs.existsSync(f) && fs.statSync(f).isDirectory()) f = path.join(f, "index.html"); fs.readFile(f, (e, d) => { if (e) { r.writeHead(404); return r.end(); } r.writeHead(200, { "content-type": TYPES[path.extname(f)] || "application/octet-stream" }); r.end(d); }); });
let URL = process.argv[2];
const SKIP_LABEL = /^(BRAKE|[0-9.]+ s|⋯|▲|▼|⏮|⏭|▶|⏸|P|오늘|오늘 3|졸업|자유)/;   // 상태 글·기호·번호 칸
(async () => {
  if (srv) { await new Promise(ok => srv.listen(0, "127.0.0.1", ok)); URL = `http://127.0.0.1:${srv.address().port}/preview/`; }
  const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 1280, height: 720 } }); const bad = [];
  await p.goto(URL); await p.waitForTimeout(500); await p.click("#opening"); await p.waitForTimeout(500);
  const scan = async name => { const r = await p.evaluate(() => {
      const vis = e => { const r = e.getBoundingClientRect(), cs = getComputedStyle(e); return r.width > 0 && r.height > 0 && cs.visibility !== "hidden" && +cs.opacity > 0.05 && r.bottom > 0 && r.top < innerHeight && r.right > 0 && r.left < innerWidth; };
      const top = e => { const r = e.getBoundingClientRect(); const x = Math.min(innerWidth - 1, Math.max(0, r.left + r.width / 2)), y = Math.min(innerHeight - 1, Math.max(0, r.top + r.height / 2)); const h = document.elementFromPoint(x, y); return h && (h === e || e.contains(h)); };
      const out = { hit: [], label: [], small: [], low: [], dash: [] };
      const ctl = [...document.querySelectorAll("button, .stg, .pedal, .gblock, #wheelBox, select, input")].filter(e => vis(e) && top(e));
      for (const e of ctl) { const r = e.getBoundingClientRect(), cx = r.left + r.width / 2, cy = r.top + r.height / 2, H = 31;
        const pts = [[cx - H, cy], [cx + H, cy], [cx, cy - H], [cx, cy + H]].filter(([x, y]) => x >= 0 && y >= 0 && x < innerWidth && y < innerHeight);
        const ok = pts.every(([x, y]) => { const h = document.elementFromPoint(x, y); return h && (h === e || e.contains(h) || h.closest("button,.stg,.pedal,.gblock") === e); });
        const name = e.id || (e.innerText || "").trim().slice(0, 12) || e.className;
        if (!ok) out.hit.push(`${name} ${Math.round(r.width)}×${Math.round(r.height)}`);
        if (e.tagName === "BUTTON" && !e.classList.contains("stg")) { const t = (e.innerText || "").replace(/\s/g, ""); if (t.length > 5) out.label.push(`${name}: ${(e.innerText || "").trim()}`); } }
      const lum = c => { const m = c.match(/[\d.]+/g); if (!m) return null; const a = m.length > 3 ? +m[3] : 1; const f = v => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return { L: 0.2126 * f(+m[0]) + 0.7152 * f(+m[1]) + 0.0722 * f(+m[2]), a }; };
      const bgOf = e => { for (let x = e; x; x = x.parentElement) { const cs = getComputedStyle(x); if (cs.backgroundImage !== "none") return null; const l = lum(cs.backgroundColor); if (l && l.a > 0.9) return l.L; } return 1; };
      const w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT); let n;
      while ((n = w.nextNode())) { const t = n.textContent.trim(); if (!t) continue; const e = n.parentElement; if (!vis(e) || !top(e)) continue; const cs = getComputedStyle(e);
        if (parseFloat(cs.fontSize) < 12) out.small.push(`${cs.fontSize} ${t.slice(0, 16)}`);
        if (/—|–/.test(t)) out.dash.push(t.slice(0, 30));
        const fg = lum(cs.color), bg = bgOf(e); if (fg && bg !== null) { const L1 = Math.max(fg.L, bg), L2 = Math.min(fg.L, bg), cr = (L1 + 0.05) / (L2 + 0.05) * (fg.a < 1 ? fg.a : 1) + (fg.a < 1 ? 1 - fg.a : 0); if (cr < 4.5) out.low.push(`${cr.toFixed(1)} ${t.slice(0, 16)}`); } }
      return out; });
    for (const [k, list] of Object.entries(r)) for (const x of [...new Set(list)]) if (!(k === "label" && SKIP_LABEL.test(x.split(": ")[1] || ""))) bad.push(`[${name}] ${({ hit: "C3 누름 영역 64 미만", label: "R3 버튼 이름 5자 넘음", small: "C1 글자 12 미만", low: "C2 대비 4.5 미만", dash: "R10 줄표" })[k]}: ${x}`); };
  await scan("홈");
  await p.click('.stg[data-i="1"]'); await p.click("#startGuide"); await p.waitForTimeout(900); await scan("연습");
  await p.click("#moreBtn"); await p.waitForTimeout(200); await scan("더 보기"); await p.click("#moreBtn");
  await p.click("#dHelp"); await p.waitForTimeout(300); await scan("도움말"); await p.click("#dHelp");
  await p.click("#dTips"); await p.waitForTimeout(300); await scan("팁");
  await b.close(); if (srv) srv.close();
  if (bad.length) { console.log(bad.join("\n")); console.log(`\n화면 규칙 위반 ${bad.length}건`); process.exit(1); } else console.log("화면 규칙 통과");
})();
