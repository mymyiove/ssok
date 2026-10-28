// 시연 영상(2026-10-28 동결 화면): 오프닝 → 홈 → 화면 안내 7칸 → [시연 보기] 2번 완주 → [직접 하기] 연습(고스트·미러 반대쪽) → P → 결과 → 돌발(12번 시연) → 타기 전 준비 → 끝 카드
// 자막은 페이지 안 띠(#vcap)로 넣고, 장면 시각(ms)을 scenes.json 에 남겨 숏폼 컷에 쓴다.
const { chromium } = require('playwright'); const fs = require('fs');
(async () => {
  const out = process.argv[2]; const scenes = []; let T0 = 0;
  const br = await chromium.launch(); const ctx = await br.newContext({ viewport: { width: 1280, height: 720 }, recordVideo: { dir: out + '/raw', size: { width: 1280, height: 720 } } });
  const pg = await ctx.newPage(); T0 = Date.now();
  const mark = (name) => { scenes.push({ name, t: (Date.now() - T0) / 1000 }); };
  await pg.goto('file:///home/user/ssok/docs/sian.html'); await pg.waitForTimeout(600);
  await pg.evaluate(() => { try { localStorage.clear(); localStorage.removeItem('ssok.tourSeen'); } catch (e) {} });
  await pg.addStyleTag({ content: '#vcap{position:fixed;left:50%;bottom:28px;transform:translateX(-50%);z-index:9999;background:rgba(17,18,22,.86);color:#fff;font-size:26px;font-weight:700;padding:12px 26px;border-radius:999px;letter-spacing:-.01em;white-space:nowrap;pointer-events:none;transition:opacity .25s;opacity:0}#vcap.on{opacity:1}' });
  await pg.evaluate(() => { const d = document.createElement('div'); d.id = 'vcap'; document.body.appendChild(d); });
  const cap = async (t) => pg.evaluate(t => { const d = document.getElementById('vcap'); d.textContent = t; d.classList.toggle('on', !!t); }, t);
  const brake = (on) => pg.evaluate(on => { const S = window.__ssok; S.brakeD = on ? 1 : 0; S.brake = !!on; S.brakeLatch = !!on; }, on);
  mark('opening'); await pg.waitForTimeout(2600);
  await pg.click('#opening'); await pg.waitForTimeout(700); await pg.evaluate(() => document.getElementById('opening').classList.remove('show'));
  mark('home'); await pg.click('.stg[data-i="1"]'); await cap('세워 둔 내 차에서, 내 핸들로 주차 연습'); await pg.waitForTimeout(3200);
  // 화면 안내 7칸
  await cap(''); await pg.evaluate(() => document.getElementById('startDemo').click()); await pg.waitForTimeout(900); mark('tour');
  await cap('처음 들어오면 화면을 하나씩 안내해요'); for (let i = 0; i < 6; i++) { await pg.waitForTimeout(1300); await pg.click('#tourNext'); } await pg.waitForTimeout(1300); await pg.click('#tourNext'); await pg.waitForTimeout(300);
  // 시연 보기
  mark('demo'); await cap('시연 보기 · 차가 공식대로 혼자 주차해요'); await pg.waitForTimeout(2500); await cap('');
  const t0 = Date.now(); let rMarked = false; while (Date.now() - t0 < 60000) { const g = await pg.evaluate(() => window.__ssok.lastGear); if (g === 'R' && !rMarked) { rMarked = true; mark('demo_R'); } if (await pg.evaluate(() => window.__ssok.demoDone)) break; await pg.waitForTimeout(150); }
  mark('demo_done'); await pg.waitForTimeout(2200);
  // 직접 하기 = 연습하기
  await pg.click('#demoStop'); await pg.waitForTimeout(500); mark('guide'); await cap('직접 하기 · 한 단계씩, 고스트 차가 앞서 가요'); await pg.waitForTimeout(2200); await cap('');
  await pg.evaluate(() => { const S = window.__ssok; S.__auto = new window.SSOK.Demo(window.SSOK, S.spec, S.sc, window.SSOK.STAGES[1]); });
  let glanced = false; const t1 = Date.now();
  while (Date.now() - t1 < 70000) {
    const r = await pg.evaluate(() => { const S = window.__ssok; const d = S.__auto.step(S.eng, 0.016); S.steer = d.steer; S.signal = d.signal; S.brakeD = d.brake ? 1 : 0; S.brake = !!d.brake; S.hazL = !!d.hazardLights; return { gear: S.eng.gear, inSlot: S.eng.inSlot }; });
    if (r.gear === 'R' && !glanced) { glanced = true; mark('guide_R'); await cap('R 을 넣으면 칸 쪽 거울이 내려가요 · 누르면 반대쪽'); const mr = await pg.locator('#mir').boundingBox(); await pg.mouse.click(mr.x + mr.width / 2, mr.y + mr.height / 2); await pg.waitForTimeout(1600); await pg.mouse.click(mr.x + mr.width / 2, mr.y + mr.height / 2); await pg.waitForTimeout(400); await cap('노란선이 칸 안에 오면 핸들을 풀어요'); }
    if (r.inSlot) break; await pg.waitForTimeout(16); }
  await cap('다 되면 멈추고 P'); await pg.evaluate(() => { const S = window.__ssok; S.brakeD = 1; S.brake = true; S.brakeLatch = true; S.signal = 'OFF'; }); await pg.waitForTimeout(1200);
  for (let i = 0; i < 30; i++) { const ph = await pg.evaluate(() => window.__ssok.eng.phase); if (ph === 'PARKED' || ph === 'ENDED') break; await pg.click('#pBtn'); await pg.waitForTimeout(300); }
  mark('result'); await cap('결과 · 내 길과 공식 길, 내려서 보면 간격까지'); await pg.waitForTimeout(3000); await pg.click('#rpPlay'); await pg.waitForTimeout(5500); await cap('');
  // 돌발(12번 시연, 밤)
  await pg.click('#dHome'); await pg.waitForTimeout(500); await pg.click('.stg[data-i="11"]'); await pg.waitForTimeout(400); await pg.evaluate(() => document.getElementById('startDemo').click()); await pg.waitForTimeout(300); mark('hazard_start');
  await cap('중급부터는 사람이 걸어 나와요'); for (let i = 0; i < 600; i++) { if (await pg.evaluate(() => !!window.__ssok.eng.hazard)) break; await pg.waitForTimeout(100); } mark('hazard'); await cap('보이는 순간 브레이크면 통과, 그냥 가면 사고'); await pg.waitForTimeout(3500); await cap('');
  // 타기 전 준비
  await pg.click('#dHome'); await pg.waitForTimeout(400); await pg.click('#dTips'); await pg.waitForTimeout(400); await pg.click('.tip.prep[data-prep="seat"] button'); await pg.waitForTimeout(400); mark('prep'); await cap('타기 전 준비 · 의자·핸들·거울부터'); await pg.waitForTimeout(3200);
  // 끝 카드
  await pg.evaluate(() => { const e = document.createElement('div'); e.id = 'vend'; e.style.cssText = 'position:fixed;inset:0;z-index:9998;background:#fff;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:14px;font-family:inherit;opacity:0;transition:opacity .5s'; e.innerHTML = '<div style="font-size:72px;font-weight:800;letter-spacing:-.03em">쏙 <span style="color:#00AAD2">SSOK</span></div><div style="font-size:24px;color:#5f6775">세워 둔 내 차에서, 내 차 핸들로 주차 연습</div><div style="font-size:20px;color:#6f7682;margin-top:10px">Pleos Connect · ssok.hojun.app</div>'; document.body.appendChild(e); requestAnimationFrame(() => e.style.opacity = '1'); });
  await cap(''); mark('end'); await pg.waitForTimeout(3500); mark('stop');
  fs.writeFileSync(out + '/scenes.json', JSON.stringify(scenes, null, 1));
  await ctx.close(); await br.close(); console.log(JSON.stringify(scenes));
})();
