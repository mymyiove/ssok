// 장마다: 마지막 장면 PNG(2배 해상도) + 등장 애니메이션 영상(webm → 나중에 mp4로 자름)
const { chromium } = require('playwright'); const fs = require('fs');
const OUT = process.argv[2];
const states = []; for (let n = 1; n <= 18; n++) { states.push({ n, step: 0 }); if (n === 5 || n === 16) states.push({ n, step: 1 }); }
(async () => {
  const b = await chromium.launch(); const meta = [];
  for (const [k, st] of states.entries()) {
    const id = String(k + 1).padStart(2, '0');
    // 1) PNG (2x)
    { const c = await b.newContext({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 2 }); const p = await c.newPage();
      await p.goto(`http://127.0.0.1:8765/pt/index.html#${st.n}`); await p.waitForTimeout(st.step ? 1600 : 4200);
      if (st.step) { await p.keyboard.press('ArrowRight'); await p.waitForTimeout(3800); }
      await p.mouse.move(960, 540); await p.waitForTimeout(100);
      await p.screenshot({ path: `${OUT}/png/${id}.png` }); await c.close(); }
    // 2) 영상
    { const t0 = Date.now(); const c = await b.newContext({ viewport: { width: 1920, height: 1080 }, recordVideo: { dir: `${OUT}/raw/${id}`, size: { width: 1920, height: 1080 } } });
      const p = await c.newPage(); await p.goto(`http://127.0.0.1:8765/pt/index.html#${st.n}`); let start = (Date.now() - t0) / 1000;
      if (st.step) { await p.waitForTimeout(1600); start = (Date.now() - t0) / 1000; await p.keyboard.press('ArrowRight'); }
      await p.waitForTimeout(st.n === 1 ? 4600 : 4000); await c.close();
      meta.push({ id, n: st.n, step: st.step, start: Math.max(0, start - 0.05) }); }
    process.stdout.write(id + ' ');
  }
  fs.writeFileSync(`${OUT}/meta.json`, JSON.stringify(meta)); await b.close();
})();
