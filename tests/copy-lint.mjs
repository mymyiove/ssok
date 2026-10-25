// node tests/copy-lint.mjs — 소개 페이지 글자 수 법칙(PM 2026-10-25 '한 화면당 최대 글자수').
// 한 화면 = 섹션 하나. 글자 수는 공백·문장부호를 뺀 글자(한글·영문·숫자)만 센다.
//   제목(h1/h2) ≤ 16자 · 부제(.sub) ≤ 40자 · 카드 하나 ≤ 60자 · 섹션 전체 ≤ 360자(카드 격자 섹션은 ≤ 520자)
import { readFileSync } from "node:fs";
const html = readFileSync(new URL("../site/index.html", import.meta.url), "utf8");
const LIM = { title: 16, sub: 40, card: 60, section: 360, grid: 520 };
const strip = h => h.replace(/<script[\s\S]*?<\/script>/g, "").replace(/<[^>]+>/g, " ");
const count = t => (strip(t).match(/[가-힣A-Za-z0-9]/g) || []).length;
const pick = (h, re) => [...h.matchAll(re)].map(m => m[1]);
let fails = 0; const bad = (m) => { fails++; console.log("  ✗ " + m); };
const blocks = [...html.matchAll(/<(header|section)\b[^>]*id="([^"]+)"[\s\S]*?<\/\1>/g)];
for (const b of blocks) {
  const id = b[2], h = b[0]; const total = count(h);
  const isGrid = /class="(grid|formula|tiers|sigs|nums|brand)"/.test(h);
  const lim = isGrid ? LIM.grid : LIM.section;
  const t = pick(h, /<h[12][^>]*>([\s\S]*?)<\/h[12]>/g).map(count), subs = pick(h, /<p class="sub">([\s\S]*?)<\/p>/g).map(count);
  const cards = pick(h, /<div class="(?:tile(?: img)?|f|num|sig|tier|chip|bcard)"[^>]*>([\s\S]*?)(?=<div class="(?:tile(?: img)?|f|num|sig|tier|chip|bcard)"|<\/section>)/g).map(count);
  const txts = pick(h, /<div class="txt">([\s\S]*?)<\/div><\/div>/g).map(count);
  console.log(`${id.padEnd(8)} 전체 ${String(total).padStart(4)}/${lim}  제목 ${t.join("·") || "-"}  부제 ${subs.join("·") || "-"}  카드 ${[...cards, ...txts].join("·") || "-"}`);
  if (total > lim) bad(`${id}: 섹션 ${total}자 > ${lim}`);
  t.forEach(n => n > LIM.title && bad(`${id}: 제목 ${n}자 > ${LIM.title}`));
  subs.forEach(n => n > LIM.sub && bad(`${id}: 부제 ${n}자 > ${LIM.sub}`));
  [...cards, ...txts].forEach(n => n > LIM.card && bad(`${id}: 카드 ${n}자 > ${LIM.card}`));
}
console.log(fails ? `\n실패 ${fails}` : "\n글자 수 법칙 통과"); process.exit(fails ? 1 : 0);
