// node tests/ui-copy-lint.mjs — 앱 화면 글자 법칙(PM 2026-10-27 "텍스트 너무 많고 작다 — 글자 크기 하한 + 글자수 제한").
// 글자 수는 공백·문장부호를 뺀 글자(한글·영문·숫자)만 센다. 시안(site/preview)을 재고, 앱은 같은 표를 쓴다.
//   코치 문장 ≤ 22 · 연습 문장 ≤ 24 · 완료 이름 ≤ 8 · 화면 안내 말풍선 ≤ 36 · 도움말 카드 ≤ 60 · 팁 카드 ≤ 60 · 홈 설명 ≤ 34 · 글자 크기 하한 12px
import { readFileSync } from "node:fs";
const html = readFileSync(new URL("../site/preview/index.html", import.meta.url), "utf8");
const mod = { exports: {} }; new Function("module", "exports", readFileSync(new URL("../site/preview/sim.js", import.meta.url), "utf8"))(mod, mod.exports); const S = mod.exports;
const LIM = { coach: 22, guide: 24, ok: 8, tour: 20, help: 24, tip: 36, what: 16 };
const count = t => (String(t).replace(/<[^>]+>/g, " ").match(/[가-힣A-Za-z0-9]/g) || []).length;
let fails = 0; const bad = m => { fails++; console.log("  ✗ " + m); };
for (const st of S.STAGES) { const sc = S.build(S.SPECS.AVANTE, st); sc.coach.forEach(([t, c, g, o], i) => {
  if (count(t) > LIM.coach) bad(`${st.no}번 코치 ${i + 1} ${count(t)}자 > ${LIM.coach}: ${t}`);
  if (count(g) > LIM.guide) bad(`${st.no}번 연습 ${i + 1} ${count(g)}자 > ${LIM.guide}: ${g}`);
  if (count(o) > LIM.ok) bad(`${st.no}번 완료 이름 ${i + 1} ${count(o)}자 > ${LIM.ok}: ${o}`); }); }
const tour = html.match(/const TOUR = (\[[\s\S]*?\]);/); const T = tour ? new Function("return " + tour[1])() : []; T.forEach(([, t, d]) => count(d) > LIM.tour && bad(`화면 안내 '${t}' ${count(d)}자 > ${LIM.tour}`));
const help = html.slice(html.indexOf('id="help"'), html.indexOf('id="tips"'));
for (const m of help.matchAll(/<h3><span class="n">(\d+)<\/span>([^<]+)<\/h3><p>([\s\S]*?)<\/p>/g)) if (count(m[3]) > LIM.help) bad(`도움말 ${m[1]} ${m[2]} ${count(m[3])}자 > ${LIM.help}`);
const tips = html.slice(html.indexOf('id="tips"'), html.indexOf('id="prep"'));
for (const m of tips.matchAll(/<div class="tip(?: prep)?"[^>]*><h3><span class="n">(\d+)<\/span>([^<]+)<\/h3>([\s\S]*?)<\/div>\n/g)) if (count(m[3].replace(/<svg[\s\S]*?<\/svg>/, "")) > LIM.tip) bad(`팁 ${m[1]} ${m[2]} ${count(m[3])}자 > ${LIM.tip}`);
const what = html.match(/const what = \{([\s\S]*?)\}\[opt\.kind\]/); if (what) for (const m of what[1].matchAll(/"([^"]+)"/g)) if (count(m[1]) > LIM.what) bad(`홈 설명 ${count(m[1])}자 > ${LIM.what}: ${m[1]}`);
for (const m of html.matchAll(/font-size:\s*(\d+)px/g)) if (+m[1] < 12) bad(`글자 ${m[1]}px < 12px`);
for (const m of html.matchAll(/font-size:\s*clamp\((\d+)px,/g)) if (+m[1] < 12) bad(`글자 하한 clamp(${m[1]}px) < 12px`);
console.log(fails ? `\n실패 ${fails}` : "화면 글자 법칙 통과"); process.exit(fails ? 1 : 0);
