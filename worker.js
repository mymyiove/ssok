// 정적 자산만 서빙. 심사 전까지 검색엔진 색인 금지(X-Robots-Tag) — 11.15 뒤에 풀 것.
export default {
  async fetch(request, env) {
    const res = await env.ASSETS.fetch(request);
    const h = new Headers(res.headers);
    h.set("X-Robots-Tag", "noindex, nofollow");
    return new Response(res.body, { status: res.status, headers: h });
  },
};
