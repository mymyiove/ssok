// 숏폼 오버레이(1080×1920, 투명 PNG): 위 = 로고 + 쏙 SSOK + 큰 제목, 아래 = 설명 한 줄
const { chromium } = require('playwright'); const fs = require('fs');
const SCENES = JSON.parse(fs.readFileSync(process.argv[2], 'utf8')); const out = process.argv[3];
const MARK = fs.readFileSync('/home/user/ssok/site/preview/icon.svg', 'utf8').replace(/<animateTransform[^>]*\/>/, '');
(async () => { const b = await chromium.launch(); const pg = await b.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
  for (const s of SCENES) {
    const html = `<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;background:transparent;width:1080px;height:1920px;font-family:Pretendard,system-ui,"Apple SD Gothic Neo","Malgun Gothic",sans-serif;color:#111216;overflow:hidden}
      .top{position:absolute;left:0;right:0;top:150px;display:flex;flex-direction:column;align-items:center;gap:26px;padding:0 70px;text-align:center}
      .brand{display:flex;align-items:center;gap:18px;font-size:44px;font-weight:800;letter-spacing:-.02em}.brand svg{width:64px;height:64px}.brand b{color:#00AAD2}
      .h{font-size:${s.big || 66}px;font-weight:800;line-height:1.25;letter-spacing:-.03em;word-break:keep-all}
      .k{font-size:30px;color:#5f6775;font-weight:600;background:#f3f4f6;padding:10px 22px;border-radius:999px}
      .bot{position:absolute;left:70px;right:70px;top:1300px;text-align:center;font-size:48px;font-weight:700;line-height:1.35;letter-spacing:-.02em;word-break:keep-all;color:#111216}
      .step{position:absolute;left:0;right:0;top:1560px;text-align:center;font-size:28px;color:#6f7682;letter-spacing:.08em}
      </style></head><body><div class="top"><div class="brand">${MARK}<span>쏙 <b>SSOK</b></span></div><div class="k">${s.kicker || '정차 중 주차 연습 · Pleos Connect'}</div><div class="h">${s.head}</div></div>
      <div class="bot">${s.sub || ''}</div><div class="step">${s.step || ''}</div></body></html>`;
    await pg.setContent(html); await pg.waitForTimeout(150); await pg.screenshot({ path: `${out}/ov_${s.id}.png`, omitBackground: true }); }
  await b.close(); console.log('overlays', SCENES.length); })();
