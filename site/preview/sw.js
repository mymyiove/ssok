// 쏙 PWA: 같은 폴더의 앱 파일만 캐시(네트워크 우선, 실패하면 캐시). 버전을 올리면 옛 캐시를 지운다.
const V = "ssok-v1", FILES = ["./", "./index.html", "./sim.js", "./view.js", "./manifest.webmanifest", "./icon.svg"];
self.addEventListener("install", e => { e.waitUntil(caches.open(V).then(c => c.addAll(FILES)).then(() => self.skipWaiting())); });
self.addEventListener("activate", e => { e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== V).map(k => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener("fetch", e => { if (e.request.method !== "GET" || new URL(e.request.url).origin !== location.origin) return;
  e.respondWith(fetch(e.request).then(r => { const copy = r.clone(); caches.open(V).then(c => c.put(e.request, copy)); return r; }).catch(() => caches.match(e.request))); });
