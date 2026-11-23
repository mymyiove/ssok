// 쏙 PT 덱: 무대 맞춤 · 넘기기(키·휠·터치·클리커) · 장 안 단계 · 등장 효과 · 숫자 세기 · 아래 도로 진행 · 발표자 메모
(() => {
  const stage = document.getElementById("stage");
  const S = [...document.querySelectorAll(".s")];
  const main = S.filter(s => !s.dataset.apx).length;   // 부록은 진행(도로)에서 뺀다
  const road = document.getElementById("road"), car = road.querySelector(".car");
  const notes = document.getElementById("notes");
  let i = 0, step = 0, busy = false, timers = [];

  // 1920×1080 무대를 창에 맞춘다
  const fit = () => { const k = Math.min(innerWidth / 1920, innerHeight / 1080); stage.style.transform = `scale(${k})`; };
  addEventListener("resize", fit); fit();

  const ease = t => 1 - Math.pow(1 - t, 4);
  function count(el) {
    const to = +el.dataset.to, dec = +(el.dataset.dec || 0), pre = el.dataset.pre || "", suf = el.dataset.suf || "";
    const dur = 1500, t0 = performance.now();
    const tick = now => { const t = Math.min(1, (now - t0) / dur); el.textContent = pre + (to * ease(t)).toFixed(dec) + suf; if (t < 1) el._raf = requestAnimationFrame(tick); };
    cancelAnimationFrame(el._raf); el._raf = requestAnimationFrame(tick);
  }

  // 슬라이드 안 요소를 data-d(ms) 뒤에 켠다. 단계(step1) 안 요소는 그 단계가 열릴 때
  function play(root, base = 0) {
    root.querySelectorAll("[data-a]").forEach(el => {
      if (root.classList.contains("s") && el.closest(".step1")) return;
      const d = base + (+(el.dataset.d || 0));
      timers.push(setTimeout(() => { el.classList.add("go"); if (el.dataset.a === "count") count(el); }, d + 60));
    });
  }
  function reset(s) {
    s.classList.remove("st1", "vid", "pre");
    s.querySelectorAll(".go").forEach(el => el.classList.remove("go"));
    s.querySelectorAll("[data-a='count']").forEach(el => { cancelAnimationFrame(el._raf); el.textContent = (el.dataset.pre || "") + "0" + (el.dataset.suf || ""); });
    const v = s.querySelector("video"); if (v) { v.pause(); v.currentTime = 0; }
  }

  function show(n, dir = 1) {
    n = Math.max(0, Math.min(S.length - 1, n));
    if (n === i && S[i].classList.contains("on")) return;
    timers.forEach(clearTimeout); timers = [];
    const prev = S[i];
    prev.classList.remove("on"); prev.classList.add("out"); prev.classList.toggle("rev", dir < 0);
    setTimeout(() => { prev.classList.remove("out", "rev"); reset(prev); }, 800);
    i = n; step = 0;
    const cur = S[i]; reset(cur);
    cur.classList.toggle("back", dir < 0);
    requestAnimationFrame(() => requestAnimationFrame(() => { cur.classList.remove("back"); cur.classList.add("on"); play(cur, 250); }));
    document.querySelector(".lights").classList.toggle("on", i === 0);
    if (i === 0) { const L = document.querySelector(".lights"); L.classList.remove("lit"); void L.offsetWidth; L.classList.add("lit"); }
    history.replaceState(null, "", "#" + (i + 1));
    drive(); note();
  }

  // 아래 도로: 진행에 따라 차가 오른쪽 주차 칸으로. 마지막 장이면 후진해 칸에 쏙
  function drive() {
    const last = i >= main - 1, p = Math.min(1, i / (main - 1));
    road.classList.toggle("done", last && step >= 1);
    if (last && step >= 1) { car.style.left = "calc(97% - 55px)"; car.style.top = "18px"; car.style.transform = "rotate(-90deg)"; }
    else { car.style.left = `calc(3% + ${p * 82}% )`; car.style.top = "9px"; car.style.transform = "none"; }
  }
  function note() { if (!notes.hidden) notes.innerHTML = `${i + 1}/${S.length} · ${S[i].dataset.note || ""}<small>→ ${S[i + 1] ? (S[i + 1].dataset.note || "").slice(0, 40) + "…" : "끝"}</small>`; }

  function next() {
    if (busy) return; lock();
    const cur = S[i], steps = +(cur.dataset.steps || 0);
    if (step < steps) { step++; cur.classList.add("st1"); play(cur.querySelector(".step1"), 300); drive(); return; }
    if (i < S.length - 1) show(i + 1, 1);
  }
  function prev() {
    if (busy) return; lock();
    const cur = S[i];
    if (step > 0) { step = 0; cur.classList.remove("st1"); cur.querySelectorAll(".step1 .go").forEach(e => e.classList.remove("go")); drive(); return; }
    if (i > 0) show(i - 1, -1);
  }
  function lock() { busy = true; setTimeout(() => busy = false, 650); }

  addEventListener("keydown", e => {
    const k = e.key;
    if (["ArrowRight", "ArrowDown", "PageDown", " ", "Enter"].includes(k)) { e.preventDefault(); next(); }
    else if (["ArrowLeft", "ArrowUp", "PageUp", "Backspace"].includes(k)) { e.preventDefault(); prev(); }
    else if (k === "Home") show(0, -1);
    else if (k === "End") show(main - 1, 1);
    else if (k === "n" || k === "N") { notes.hidden = !notes.hidden; note(); }
    else if (k === "f" || k === "F") { document.fullscreenElement ? document.exitFullscreen() : document.documentElement.requestFullscreen().catch(() => {}); }
    else if ((k === "v" || k === "V") && S[i].classList.contains("s-live")) { const s = S[i], v = s.querySelector("video"); s.classList.remove("pre"); s.classList.toggle("vid"); s.classList.contains("vid") ? (v.currentTime = 0, v.play()) : v.pause(); }
    else if ((k === "p" || k === "P") && S[i].classList.contains("s-live")) { const s = S[i], f = s.querySelector("iframe"); if (!f.src) f.src = f.dataset.src; s.classList.remove("vid"); s.classList.toggle("pre"); }
    else if (/^[0-9]$/.test(k) && e.altKey) show(+k, 1);
  });

  // 휠 = 스크롤하듯 넘기기(트랙패드 관성은 한 번만)
  let wheelAt = 0, acc = 0;
  addEventListener("wheel", e => {
    if (S[i].classList.contains("pre")) return;
    e.preventDefault(); const now = performance.now();
    if (now - wheelAt > 300) acc = 0; wheelAt = now; acc += e.deltaY;
    if (acc > 60) { acc = -9999; next(); } else if (acc < -60) { acc = 9999; prev(); }
  }, { passive: false });
  let ty = null;
  addEventListener("touchstart", e => { ty = e.touches[0].clientY; }, { passive: true });
  addEventListener("touchend", e => { if (ty == null) return; const d = ty - e.changedTouches[0].clientY; if (d > 40) next(); else if (d < -40) prev(); ty = null; });
  addEventListener("click", e => { if (e.target.closest("iframe,video")) return; (e.clientX > innerWidth * .3 ? next : prev)(); });

  // 마우스: 움직이면 커서 보이고, 무대가 아주 살짝 따라 기운다
  let hideT;
  addEventListener("mousemove", e => {
    document.body.classList.add("cursor"); clearTimeout(hideT); hideT = setTimeout(() => document.body.classList.remove("cursor"), 1500);
    const x = e.clientX / innerWidth - .5, y = e.clientY / innerHeight - .5;
    S[i].querySelectorAll(".big, .photo, .cam, .wheel, .ssok, .q").forEach(el => el.style.translate = `${x * -14}px ${y * -10}px`);
  });

  const start = Math.max(0, Math.min(S.length - 1, (parseInt(location.hash.slice(1)) || 1) - 1));
  i = start; S[i].classList.add("on"); play(S[i], 300);
  if (i === 0) { const L = document.querySelector(".lights"); L.classList.add("on", "lit"); }
  drive();
})();
