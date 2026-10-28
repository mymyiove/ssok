// 기획서·소개 페이지 캡처(2026-10-28 동결 화면): 홈 · 연습하기(고스트·힌트) · 미러 반대쪽 · 결과 · 밤 돌발 · 타기 전 준비 · 화면 안내 · 실수 보기 · 오늘 3개
const { chromium } = require('playwright'); const sharp = (() => { try { return require('sharp'); } catch (e) { return null; } })();
(async () => {
  const br = await chromium.launch(); const ctx = await br.newContext({ viewport: { width: 1280, height: 720 }, deviceScaleFactor: 1.5 });
  const pg = await ctx.newPage(); pg.on('pageerror', e => console.log('ERR', e.message)); const out = process.argv[2];
  const shot = n => pg.screenshot({ path: `${out}/${n}.png` });
  const brake = (on) => pg.evaluate(on => { const S = window.__ssok; S.brakeD = on ? 1 : 0; S.brake = !!on; S.brakeLatch = !!on; }, on);
  await pg.goto('file:///home/user/ssok/docs/sian.html'); await pg.waitForTimeout(1200); await pg.evaluate(() => { try { localStorage.clear(); localStorage.setItem('ssok.tourSeen', '1'); } catch (e) {} });
  await pg.click('#opening'); await pg.waitForTimeout(600); await pg.evaluate(() => document.getElementById('opening').classList.remove('show'));
  await pg.click('.stg[data-i="1"]'); await pg.waitForTimeout(400); await shot('1_home');
  // 2 연습하기 6번(중급): 비상등 → D → 2.6초 → 고스트 차·여기서 멈춤·코치 줄(새 단계라 보임)
  await pg.click('.stg[data-i="6"]'); await pg.waitForTimeout(300); await pg.evaluate(() => document.getElementById('startGuide').click()); await pg.waitForTimeout(600);
  await brake(true); await pg.waitForTimeout(200); await pg.click('#hazBtn'); await pg.waitForTimeout(200); await pg.click('#gUp'); await pg.waitForTimeout(300); await brake(false); await pg.waitForTimeout(1800);
  await pg.evaluate(() => { window.__ssok.hintUntil = performance.now() + 5000; }); await pg.waitForTimeout(200); await shot('2_guide');
  // 3 R 단계에서 미러(반대쪽): 공식 자리까지 가서 R → 미러 누르기
  await pg.click('#dHome'); await pg.waitForTimeout(300); await pg.click('.stg[data-i="1"]'); await pg.waitForTimeout(300); await pg.evaluate(() => document.getElementById('startDemo').click()); await pg.waitForTimeout(500);
  for (let i = 0; i < 400; i++) { if (await pg.evaluate(() => window.__ssok.lastGear) === 'R') break; await pg.waitForTimeout(100); }
  await pg.waitForTimeout(2600); await shot('play');
  const mr = await pg.locator('#mir').boundingBox(); await pg.mouse.click(mr.x + mr.width / 2, mr.y + mr.height / 2); await pg.waitForTimeout(500); await shot('3_mirror'); await pg.mouse.click(mr.x + mr.width / 2, mr.y + mr.height / 2);
  // 4 결과: 시연 끝까지 → 직접 하기(연습) → 시연 운전자 입력으로 넣고 P
  const t0 = Date.now(); while (Date.now() - t0 < 60000) { if (await pg.evaluate(() => window.__ssok.demoDone)) break; await pg.waitForTimeout(200); }
  await pg.click('#demoStop'); await pg.waitForTimeout(400); await pg.evaluate(() => { window.__ssok.mode = 'score'; }); await pg.evaluate(() => document.getElementById('reset').click()); await pg.waitForTimeout(400);
  await pg.evaluate(() => { const S = window.__ssok; S.__auto = new window.SSOK.Demo(window.SSOK, S.spec, S.sc, window.SSOK.STAGES[0]); });
  const t1 = Date.now(); while (Date.now() - t1 < 70000) { const r = await pg.evaluate(() => { const S = window.__ssok; const d = S.__auto.step(S.eng, 0.016); S.steer = d.steer; S.signal = d.signal; S.brakeD = d.brake ? 1 : 0; S.brake = !!d.brake; S.hazL = !!d.hazardLights; return S.eng.inSlot; }); if (r) break; await pg.waitForTimeout(16); }
  await pg.evaluate(() => { const S = window.__ssok; S.brakeD = 1; S.brake = true; S.brakeLatch = true; S.signal = 'OFF'; }); await pg.waitForTimeout(300);
  for (let i = 0; i < 30; i++) { const r = await pg.evaluate(() => window.__ssok.eng.finish()); if (r === 'ok' || r === 'idle') break; await pg.waitForTimeout(200); }
  await pg.waitForTimeout(1500); await shot('4_result');
  // 5 밤 돌발(11번 시연)
  await pg.click('#dHome'); await pg.waitForTimeout(300); await pg.click('.stg[data-i="11"]'); await pg.waitForTimeout(300); await pg.evaluate(() => document.getElementById('startDemo').click()); await pg.waitForTimeout(500);
  for (let i = 0; i < 600; i++) { if (await pg.evaluate(() => !!window.__ssok.eng.hazard)) break; await pg.waitForTimeout(100); }
  await pg.waitForTimeout(600); await shot('5_hazard');
  // 6 타기 전 준비
  await pg.click('#dHome'); await pg.waitForTimeout(300); await pg.click('#dTips'); await pg.waitForTimeout(400); await pg.click('.tip.prep[data-prep="seat"] button'); await pg.waitForTimeout(500); await shot('6_prep');
  await pg.click('#prepClose'); await pg.waitForTimeout(200); await pg.click('#tipsClose'); await pg.waitForTimeout(300);
  // 7 화면 안내 둘째 칸(어라운드뷰)
  await pg.evaluate(() => { try { localStorage.removeItem('ssok.tourSeen'); } catch (e) {} }); await pg.click('.stg[data-i="1"]'); await pg.waitForTimeout(300); await pg.evaluate(() => document.getElementById('startBtn').click()); await pg.waitForTimeout(900); await pg.click('#tourNext'); await pg.waitForTimeout(500); await shot('7_tour');
  await pg.click('#tourX'); await pg.waitForTimeout(300);
  // 8 실수 보기 끝
  await pg.click('#dHome'); await pg.waitForTimeout(300); await pg.click('.stg[data-i="1"]'); await pg.waitForTimeout(300); await pg.evaluate(() => document.getElementById('startMistake').click());
  const t2 = Date.now(); while (Date.now() - t2 < 70000) { if (await pg.evaluate(() => window.__ssok.demoDone)) break; await pg.waitForTimeout(200); }
  await pg.waitForTimeout(600); await shot('8_mistake');
  await br.close();
  if (sharp) { const map = { '1_home': 'home', 'play': 'play', '3_mirror': 'glance', '4_result': 'result', '5_hazard': 'hazard', '6_prep': 'mirror' };
    for (const [src, dst] of Object.entries(map)) { await sharp(`${out}/${src}.png`).jpeg({ quality: 84 }).toFile(`${out}/${dst}.jpg`); await sharp(`${out}/${src}.png`).resize(960).jpeg({ quality: 80 }).toFile(`${out}/${dst}-m.jpg`); }
    for (const n of ['1_home', '2_guide', '3_mirror', '4_result', '5_hazard', '6_prep', '7_tour', '8_mistake']) await sharp(`${out}/${n}.png`).resize(1600).jpeg({ quality: 82 }).toFile(`${out}/${n}.jpg`);
    console.log('jpg ok'); } else console.log('no sharp');
})();
