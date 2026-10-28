// 소개 페이지 사진 다시 찍기(2026-10-28): 지금 시안을 1280×720(차 화면 기준) × 1.5 로 찍어 site/img/*.jpg(1920) · *-m.jpg(960) 로
// 쓰는 법: node docs/caps5.cjs <png 폴더>  → 그 폴더에 png, 이어서 python 으로 jpg(아래 맨 끝 줄)
const { chromium } = require('playwright'); const http = require('http'), fs = require('fs'), path = require('path');
const ROOT = path.join(__dirname, '..', 'site'), TMP = path.join(ROOT, 'preview', '__cap.html');
const html = fs.readFileSync(path.join(ROOT, 'preview', 'index.html'), 'utf8').replace('const ENG = { nodes: null };', 'const ENG = { nodes: null }; window.__ssok = S; window.SSOK = SSOK;');
fs.writeFileSync(TMP, html);
const T = { '.html': 'text/html', '.js': 'text/javascript', '.svg': 'image/svg+xml', '.m4a': 'audio/mp4', '.mp3': 'audio/mpeg', '.json': 'application/json', '.webmanifest': 'application/manifest+json' };
const srv = http.createServer((q, r) => { let f = path.join(ROOT, decodeURIComponent(q.url.split('?')[0])); fs.readFile(f, (e, d) => { if (e) { r.writeHead(404); return r.end(); } r.writeHead(200, { 'content-type': T[path.extname(f)] || 'application/octet-stream' }); r.end(d); }); });
(async () => {
  await new Promise(ok => srv.listen(0, '127.0.0.1', ok)); const URL = `http://127.0.0.1:${srv.address().port}/preview/__cap.html`;
  const br = await chromium.launch(); const ctx = await br.newContext({ viewport: { width: 1280, height: 720 }, deviceScaleFactor: 1.5 });
  const pg = await ctx.newPage(); pg.on('pageerror', e => console.log('ERR', e.message)); const out = process.argv[2]; fs.mkdirSync(out, { recursive: true });
  const shot = n => pg.screenshot({ path: `${out}/${n}.png` });
  await pg.goto(URL); await pg.waitForTimeout(1200); await pg.evaluate(() => { try { localStorage.clear(); localStorage.setItem('ssok.tourSeen', '1'); } catch (e) {} });
  await pg.click('#opening'); await pg.waitForTimeout(800);
  await pg.click('.stg[data-i="1"]'); await pg.waitForTimeout(500); await shot('home');
  // 시연: R 단계에서 연습 화면 · 미러 반대쪽
  await pg.evaluate(() => document.getElementById('startDemo').click()); await pg.waitForTimeout(500);
  for (let i = 0; i < 400; i++) { if (await pg.evaluate(() => window.__ssok.lastGear) === 'R') break; await pg.waitForTimeout(100); }
  await pg.waitForTimeout(2600); await shot('play');
  const mr = await pg.locator('#mirBox').boundingBox(); await pg.mouse.click(mr.x + mr.width / 2, mr.y + mr.height / 2); await pg.waitForTimeout(500); await shot('glance'); await pg.mouse.click(mr.x + mr.width / 2, mr.y + mr.height / 2);
  // 결과: 시연 끝 → 점수 모드에서 시연 운전자 입력으로 넣고 P
  const t0 = Date.now(); while (Date.now() - t0 < 60000) { if (await pg.evaluate(() => window.__ssok.demoDone)) break; await pg.waitForTimeout(200); }
  await pg.click('#demoStop'); await pg.waitForTimeout(400); await pg.evaluate(() => { window.__ssok.mode = 'score'; }); await pg.evaluate(() => document.getElementById('reset').click()); await pg.waitForTimeout(400);
  await pg.evaluate(() => { const S = window.__ssok; S.__auto = new window.SSOK.Demo(window.SSOK, S.spec, S.sc, window.SSOK.STAGES[0]); });
  const t1 = Date.now(); while (Date.now() - t1 < 70000) { const r = await pg.evaluate(() => { const S = window.__ssok; const d = S.__auto.step(S.eng, 0.016); S.steer = d.steer; S.signal = d.signal; S.brakeD = d.brake ? 1 : 0; S.brake = !!d.brake; S.hazL = !!d.hazardLights; return S.eng.inSlot; }); if (r) break; await pg.waitForTimeout(16); }
  await pg.evaluate(() => { const S = window.__ssok; S.brakeD = 1; S.brake = true; S.brakeLatch = true; S.signal = 'OFF'; }); await pg.waitForTimeout(300);
  for (let i = 0; i < 30; i++) { const r = await pg.evaluate(() => window.__ssok.eng.finish()); if (r === 'ok' || r === 'idle') break; await pg.waitForTimeout(200); }
  await pg.waitForTimeout(1500); await shot('result');
  // 밤 돌발(11번 시연)
  await pg.evaluate(() => { const a = document.getElementById('ask'); if (a) a.classList.remove('show'); }); await pg.evaluate(() => document.getElementById('toMenu').click()); await pg.waitForTimeout(300);
  await pg.evaluate(() => { const a = document.getElementById('ask'); if (a && a.classList.contains('show')) document.getElementById('askNo').click(); }); await pg.waitForTimeout(200);
  if (!(await pg.evaluate(() => document.getElementById('setup').classList.contains('show')))) { await pg.evaluate(() => document.getElementById('askYes') && document.getElementById('askYes').click()); await pg.waitForTimeout(300); }
  await pg.click('.stg[data-i="11"]'); await pg.waitForTimeout(300); await pg.evaluate(() => document.getElementById('startDemo').click()); await pg.waitForTimeout(500);
  for (let i = 0; i < 600; i++) { if (await pg.evaluate(() => !!window.__ssok.eng.hazard)) break; await pg.waitForTimeout(100); }
  await pg.waitForTimeout(600); await shot('hazard');
  // 타기 전 준비
  await pg.click('#dHome'); await pg.waitForTimeout(300); await pg.evaluate(() => { const a = document.getElementById('ask'); if (a.classList.contains('show')) document.getElementById('askYes').click(); }); await pg.waitForTimeout(300);
  await pg.click('#dTips'); await pg.waitForTimeout(400); await pg.click('.tip.prep[data-prep="seat"] button'); await pg.waitForTimeout(600); await shot('mirror');
  await br.close(); srv.close(); fs.unlinkSync(TMP); console.log('png ok');
})();
// jpg: python3 -c "from PIL import Image;import sys;[ (lambda im:(im.save(f'site/img/{n}.jpg',quality=84),im.resize((960,540)).save(f'site/img/{n}-m.jpg',quality=80)))(Image.open(f'{sys.argv[1]}/{n}.png').convert('RGB')) for n in ['home','play','glance','result','hazard','mirror']]" <png 폴더>
