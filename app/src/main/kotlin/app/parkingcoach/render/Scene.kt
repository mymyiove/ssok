package app.parkingcoach.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import app.parkingcoach.game.GameSnapshot
import app.parkingcoach.game.Hazard
import app.parkingcoach.game.Phase
import app.parkingcoach.sim.CarSpec
import app.parkingcoach.sim.HazType
import app.parkingcoach.sim.OBB
import app.parkingcoach.sim.Obstacle
import app.parkingcoach.sim.Pose
import app.parkingcoach.sim.Scenario
import app.parkingcoach.sim.Vec
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/**
 * 장면 그리기 공용(site/preview/view.js 를 그대로 옮김): 핀홀 + 어안 카메라, 지하주차장 천장·조명, 현대차 꼴 입체 차, 기둥·벽, 돌발(사람·카트),
 * 밤·비, 8방향 주차 거리 센서. 후방/전방 카메라·좌우 사이드미러가 같이 쓴다.
 */
object Scene {
    data class CamParams(val h: Double, val pitch: Double, val hfov: Double, val near: Double, val fish: Double, val mirror: Boolean)
    // 120° · 31° 숙임 · near 0.15(view.js 2026-10-28): 140° 는 50 cm 옆 차가 가장자리에서 지느러미처럼 늘어났다
    val REAR = CamParams(0.95, Math.toRadians(31.0), Math.toRadians(120.0), 0.15, 0.95, true)
    val FRONT = CamParams(0.95, Math.toRadians(31.0), Math.toRadians(120.0), 0.15, 0.95, false)
    val SIDE = CamParams(1.0, Math.toRadians(8.0), Math.toRadians(78.0), 0.12, 0.5, true)       // 볼록 거울 + 바깥 18°
    val SIDE_DOWN = SIDE.copy(pitch = Math.toRadians(15.0))                                       // R 연동 하향 미러

    // ---- 색 ----
    fun shade(c: Int, f: Double, a: Int = 255): Int = Color.argb(a,
        (Color.red(c) * f).toInt().coerceIn(0, 255), (Color.green(c) * f).toInt().coerceIn(0, 255), (Color.blue(c) * f).toInt().coerceIn(0, 255))
    fun rgba(r: Int, g: Int, b: Int, a: Double) = Color.argb((a * 255).toInt().coerceIn(0, 255), r, g, b)
    /** 밤 모드(AAOS 야간·values-night)인가 — 어라운드뷰·리플레이·미러 바탕 팔레트가 갈린다(시안 light 옵션) */
    fun night(ctx: android.content.Context) = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

    // ---- 노이즈(카메라 질감·아스팔트) ----
    private var noiseShader: BitmapShader? = null
    fun noise(): BitmapShader {
        noiseShader?.let { return it }
        val bm = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888); val rnd = Random(7)
        val px = IntArray(160 * 160) { val v = (128 + (rnd.nextDouble() - 0.5) * 90).toInt().coerceIn(0, 255); Color.rgb(v, v, v) }
        bm.setPixels(px, 0, 160, 0, 0, 160, 160)
        return BitmapShader(bm, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).also { noiseShader = it }
    }

    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val pNoise = Paint()
    private val path = Path()

    /** 화면 다각형 + 정렬 깊이(카메라에서의 실제 거리). parts 가 있으면 1.2 m 조각들(긴 면을 잘라 순서를 지킨다) */
    class Poly(val pts: List<FloatArray>, val d: Double, val parts: List<Poly>? = null) { val size get() = pts.size; fun isEmpty() = pts.isEmpty() && parts.isNullOrEmpty() }
    // 면 단위 정렬(view.js 와 같다): BUF 가 켜져 있으면 면을 모았다가 먼 것부터. BIAS = 면 위에 붙는 것을 앞으로, PIN = 붙은 면의 깊이에 꽂기(같은 깊이는 넣은 순서)
    private class Entry(val pts: List<FloatArray>, val color: Int, val d: Double, val seq: Int)
    private var BUF: ArrayList<Entry>? = null; private var BIAS = 0.0; private var PIN: Double? = null; private var SEQ = 0
    private fun minD(vararg ps: Poly?): Double { var m = Double.POSITIVE_INFINITY; for (p in ps) { if (p == null) continue; if (p.parts != null) { for (q in p.parts) if (q.size >= 3 && q.d < m) m = q.d } else if (p.size >= 3 && p.d < m) m = p.d }; return m }
    private fun flushBuf(c: Canvas) { val b = BUF ?: return; BUF = null; b.sortWith(compareByDescending<Entry> { it.d }.thenBy { it.seq }); for (e in b) fillPoly(c, e.pts, e.color) }
    fun fillPoly(c: Canvas, p: Poly, color: Int) {
        if (p.parts != null) { for (q in p.parts) fillPoly(c, q, color); return }
        if (p.size < 3) return
        val buf = BUF; if (buf != null) { buf += Entry(p.pts, color, PIN ?: (p.d - BIAS), SEQ++); return }
        fillPoly(c, p.pts, color)
    }
    fun fillPoly(c: Canvas, pts: List<FloatArray>, color: Int) {
        if (pts.size < 3) return
        path.reset(); path.moveTo(pts[0][0], pts[0][1]); for (i in 1 until pts.size) path.lineTo(pts[i][0], pts[i][1]); path.close()
        pFill.color = color; pFill.shader = null; c.drawPath(path, pFill)
    }
    fun fillPolyShader(c: Canvas, pts: List<FloatArray>, sh: Shader) {
        if (pts.size < 3) return
        path.reset(); path.moveTo(pts[0][0], pts[0][1]); for (i in 1 until pts.size) path.lineTo(pts[i][0], pts[i][1]); path.close()
        pFill.color = Color.WHITE; pFill.shader = sh; c.drawPath(path, pFill); pFill.shader = null
    }
    fun strokeSegs(c: Canvas, segs: List<Pair<FloatArray, FloatArray>>, color: Int, lw: Float, dash: FloatArray? = null) {
        if (segs.isEmpty()) return
        path.reset(); for ((a, b) in segs) { path.moveTo(a[0], a[1]); path.lineTo(b[0], b[1]) }
        pStroke.color = color; pStroke.strokeWidth = lw; pStroke.pathEffect = dash?.let { android.graphics.DashPathEffect(it, 0f) }
        c.drawPath(path, pStroke); pStroke.pathEffect = null
    }
    fun noiseOver(c: Canvas, w: Float, h: Float, alpha: Double) { pNoise.shader = noise(); pNoise.alpha = (alpha * 255).toInt(); c.drawRect(0f, 0f, w, h, pNoise) }

    /** 카메라: pos 월드, yaw 보는 방향. toCam → [right, up, depth], toScreen 은 핀홀 + 어안. */
    class Cam(val c: Vec, yaw: Double, val P: CamParams, val W: Float, val H: Float) {
        private val f = (W / 2) / tan(P.hfov / 2).toFloat()
        private val fw = doubleArrayOf(cos(yaw), sin(yaw)); private val rt = doubleArrayOf(sin(yaw), -cos(yaw))
        fun toCam(p: Vec, z0: Double = 0.0): DoubleArray {
            val dx = p.x - c.x; val dy = p.y - c.y
            val along = dx * fw[0] + dy * fw[1]; var right = dx * rt[0] + dy * rt[1]; if (P.mirror) right = -right
            val yc = z0 - P.h
            return doubleArrayOf(right, along * sin(P.pitch) + yc * cos(P.pitch), along * cos(P.pitch) - yc * sin(P.pitch))
        }
        fun toScreen(cc: DoubleArray): FloatArray {
            var nx = (f * cc[0] / cc[2]) / (W / 2); var ny = (f * cc[1] / cc[2]) / (W / 2)
            val r = hypot(nx, ny)
            if (r > 1e-6 && P.fish > 0) { val k = (atan(r * P.fish) / P.fish) / r; nx *= k; ny *= k }
            return floatArrayOf((W / 2 + nx * W / 2).toFloat(), (H / 2 - ny * W / 2).toFloat())
        }
        /** 앞 평면(near) + 옆 네 평면(광축에서 82°)으로 자른다: 화면 밖 아주 옆까지 뻗은 면이 어안 가장자리로 몰려 뾰족해지던 것 */
        fun clipNear(poly: List<DoubleArray>): List<DoubleArray> {
            val n = P.near; val T = 7.1
            val planes: List<(DoubleArray) -> Double> = listOf({ p -> p[2] - n }, { p -> T * p[2] - p[0] }, { p -> T * p[2] + p[0] }, { p -> T * p[2] - p[1] }, { p -> T * p[2] + p[1] })
            var out = poly
            for (f in planes) { if (out.isEmpty()) break; val src = out; val o = ArrayList<DoubleArray>(src.size + 2)
                for (i in src.indices) { val a = src[i]; val b = src[(i + 1) % src.size]; val fa = f(a); val fb = f(b)
                    if (fa >= 0) o += a
                    if ((fa >= 0) != (fb >= 0)) { val t = fa / (fa - fb); o += doubleArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t) } }
                out = o }
            return out
        }
        /** 잘린 다각형을 화면으로: 변을 잘게 나눠 하나씩 휘게(어안) 옮긴다(곧은 선으로 이으면 가까운 차가 쐐기처럼 찢어진다) */
        fun proj(cl: List<DoubleArray>): Poly {
            val out = ArrayList<FloatArray>(); val d = if (cl.isEmpty()) 0.0 else cl.sumOf { kotlin.math.sqrt(it[0] * it[0] + it[1] * it[1] + it[2] * it[2]) } / cl.size
            for (i in cl.indices) { val a = cl[i]; val b = cl[(i + 1) % cl.size]; out += toScreen(a)
                val len = kotlin.math.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]) + (b[2] - a[2]) * (b[2] - a[2])); val dmin = max(P.near, min(a[2], b[2])); val m = min(32, max(1, Math.ceil(len / (dmin * 0.2)).toInt()))
                for (j in 1 until m) { val t = j.toDouble() / m; out += toScreen(doubleArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t)) } }
            return Poly(out, d)
        }
        fun proj3(pts: List<DoubleArray>): Poly = proj(clipNear(pts.map { toCam(Vec(it[0], it[1]), it[2]) }))
        /** 바닥 다각형(높이 z), 변마다 sub 등분 */
        fun poly(pts: List<Vec>, z: Double, sub: Int = 6): Poly {
            val w = ArrayList<Vec>(pts.size * sub)
            for (i in pts.indices) { val a = pts[i]; val b = pts[(i + 1) % pts.size]; for (j in 0 until sub) { val t = j.toDouble() / sub; w += Vec(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t) } }
            return proj(clipNear(w.map { toCam(it, z) }))
        }
        /** 수직 사각형: 아래 변 a→b(za) 와 같은 위 변(zb) */
        fun quad(a: Vec, b: Vec, za: Double, zb: Double): Poly = quad2(a, b, za, a, b, zb)
        /** 아래 변 a→b(za) 와 위 변 c→d(zb) 가 다른 사각형. 정렬 중이면 1.2 m 조각으로 나눈다 */
        fun quad2(a: Vec, b: Vec, za: Double, c: Vec, d: Vec, zb: Double): Poly {
            val L = hypot(b.x - a.x, b.y - a.y); val n = if (BUF != null) Math.ceil(L / 1.2).toInt() else 1
            fun ip(p: Vec, q: Vec, t: Double) = Vec(p.x + (q.x - p.x) * t, p.y + (q.y - p.y) * t)
            if (n > 1) return Poly(emptyList(), 0.0, (0 until n).map { k -> val t0 = k.toDouble() / n; val t1 = (k + 1.0) / n; one(ip(a, b, t0), ip(a, b, t1), za, ip(c, d, t0), ip(c, d, t1), zb) })
            return one(a, b, za, c, d, zb)
        }
        private fun one(a: Vec, b: Vec, za: Double, c: Vec, d: Vec, zb: Double): Poly {
            val m = 6; val cam = ArrayList<DoubleArray>(2 * (m + 1))
            for (j in 0..m) { val t = j.toDouble() / m; cam += toCam(Vec(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t), za) }
            for (j in m downTo 0) { val t = j.toDouble() / m; cam += toCam(Vec(c.x + (d.x - c.x) * t, c.y + (d.y - c.y) * t), zb) }
            return proj(clipNear(cam))
        }
        /** 면이 카메라를 보나: 변 a→b 의 진짜 법선(바깥 = ctr 반대쪽)으로(뒤쪽 면·램프가 비치던 '투시' 막음) */
        fun sees(ctr: Vec, a: Vec, b: Vec): Boolean { val mx = (a.x + b.x) / 2; val my = (a.y + b.y) / 2; var nx = b.y - a.y; var ny = a.x - b.x
            if (nx * (mx - ctr.x) + ny * (my - ctr.y) < 0) { nx = -nx; ny = -ny }; return nx * (c.x - mx) + ny * (c.y - my) > 0 }
        fun path(pts: List<Vec>, z: Double = 0.0): List<Pair<FloatArray, FloatArray>> {
            val n = P.near; val cam = pts.map { toCam(it, z) }; val segs = ArrayList<Pair<FloatArray, FloatArray>>()
            for (i in 1 until cam.size) { val a = cam[i - 1]; val b = cam[i]; val ia = a[2] >= n; val ib = b[2] >= n; if (!ia && !ib) continue
                var p = a; var q = b
                if (ia != ib) { val t = (n - a[2]) / (b[2] - a[2]); val mid = doubleArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, n); if (ia) q = mid else p = mid }
                segs += toScreen(p) to toScreen(q) }
            return segs
        }
        fun depth(p: Vec) = toCam(p, 0.0)[2]
        /** 면의 밝기: 면 법선이 카메라를 향할수록 밝다 */
        fun light(a: Vec, b: Vec): Double {
            val nx = b.y - a.y; val ny = -(b.x - a.x); val len = hypot(nx, ny).let { if (it == 0.0) 1.0 else it }
            val mx = (a.x + b.x) / 2 - c.x; val my = (a.y + b.y) / 2 - c.y; val ml = hypot(mx, my).let { if (it == 0.0) 1.0 else it }
            val d = -(nx * mx + ny * my) / (len * ml); return 0.55 + 0.45 * max(0.0, d)
        }
    }

    fun drawBox(cam: Cam, c: Canvas, b: OBB, z0: Double, z1: Double, base: Int, top: Int = base) {
        val cs = b.corners()
        val faces = (0 until 4).map { i -> cs[i] to cs[(i + 1) % 4] }.sortedByDescending { (a, d) -> cam.depth(a) + cam.depth(d) }
        for ((a, d) in faces) if (cam.sees(b.c, a, d)) fillPoly(c, cam.quad(a, d, z0, z1), shade(base, cam.light(a, d)))
        if (z1 < cam.P.h) fillPoly(c, cam.poly(cs, z1, 3), shade(top, 1.05))
    }

    /** 차(현대차 꼴, view.js drawCar3D 와 같다): 아랫면 · 차체(앞코·트렁크 눕힘) · 바퀴·아치·문턱·도어 선 · 캐빈 틀 + 유리창(옆은 B필러로 둘) · 범퍼·그릴·번호판 · 램프. 면마다 보이는 쪽만, 붙는 것은 자기 면 깊이에 */
    fun drawCar3D(cam: Cam, c: Canvas, b: OBB, hex: Int, blink: Boolean, model: String) {
        val M = CarSpec.byKey(model); val suv = M.shape == "suv"; val hoodH = M.hoodH; val roofH = M.roofH
        val u = Vec(cos(b.heading), sin(b.heading)); val v = Vec(-sin(b.heading), cos(b.heading))
        fun at(du: Double, dv: Double) = Vec(b.c.x + u.x * b.hx * du + v.x * b.hy * dv, b.c.y + u.y * b.hx * du + v.y * b.hy * dv)
        for ((du, dv) in listOf(0.6 to 0.86, 0.6 to -0.86, -0.6 to 0.86, -0.6 to -0.86)) drawBox(cam, c, OBB(at(du, dv), b.heading, 0.34, 0.11), 0.0, 0.26, Color.rgb(21, 22, 26), Color.rgb(28, 29, 34))
        fillPoly(c, cam.poly(OBB(b.c, b.heading, b.hx + 0.15, b.hy + 0.2).corners(), 0.005, 3), rgba(0, 0, 0, 0.35))
        run { val lo = OBB(b.c, b.heading, b.hx * 0.96, b.hy * 0.93).corners(); BIAS = -0.3   // 아랫면(띄우고 안으로), 바퀴 뒤로
            for (i in 0 until 4) { val a = lo[i]; val d = lo[(i + 1) % 4]; if (cam.sees(b.c, a, d)) fillPoly(c, cam.quad(a, d, 0.1, 0.26), shade(Color.rgb(36, 38, 44), cam.light(a, d))) }; BIAS = 0.0 }
        val cs = b.corners()
        val faces = (0 until 4).sortedByDescending { i -> cam.depth(cs[i]) + cam.depth(cs[(i + 1) % 4]) }
        val FT = 0.93; val RT = 0.96; val top = listOf(at(FT, 0.96), at(FT, -0.96), at(-RT, -0.96), at(-RT, 0.96))
        fun duF(z: Double): Double = 1 - (1 - FT) * max(0.0, z - 0.25) / (hoodH - 0.25)
        fun duR(z: Double): Double = -(1 - (1 - RT) * max(0.0, z - 0.25) / (hoodH - 0.25))
        val pinF = DoubleArray(4) { Double.POSITIVE_INFINITY }
        for (i in faces) { val a = cs[i]; val d = cs[(i + 1) % 4]; if (!cam.sees(b.c, a, d)) continue
            val p = cam.quad2(a, d, 0.25, top[i], top[(i + 1) % 4], hoodH); pinF[i] = minD(p) - 0.01; fillPoly(c, p, shade(hex, cam.light(a, d))) }
        fun pinAt(i: Int) { if (BUF != null && pinF[i].isFinite()) PIN = pinF[i] else { PIN = null; BIAS = 0.3 } }
        for (dv in doubleArrayOf(1.0, -1.0)) { if (!cam.sees(b.c, at(1.0, dv), at(-1.0, dv))) continue; pinAt(if (dv == 1.0) 3 else 1)
            fillPoly(c, cam.quad2(at(0.98, dv), at(-0.98, dv), 0.25, at(0.98, dv * 1.01), at(-0.98, dv * 1.01), 0.4), shade(hex, 0.45))
            for (du in doubleArrayOf(0.12, -0.42)) fillPoly(c, cam.quad2(at(du, dv * 1.012), at(du - 0.012, dv * 1.012), 0.42, at(du, dv * 1.012), at(du - 0.012, dv * 1.012), hoodH - 0.04), shade(hex, 0.5))
            val rr = 0.36 / b.hx
            for (wc in doubleArrayOf(0.6, -0.6)) { val arch = ArrayList<DoubleArray>(); val tire = ArrayList<DoubleArray>(); val rim = ArrayList<DoubleArray>()
                for (k in 0..12) { val t = k / 12.0 * Math.PI; val q = at(wc + cos(t) * rr * 1.18, dv * 1.015); arch += doubleArrayOf(q.x, q.y, 0.34 + sin(t) * 0.42) }
                for (k in 0 until 16) { val t = k / 16.0 * 2 * Math.PI; val q = at(wc + cos(t) * rr, dv * 1.02); tire += doubleArrayOf(q.x, q.y, 0.34 + sin(t) * 0.34); val w = at(wc + cos(t) * rr * 0.6, dv * 1.025); rim += doubleArrayOf(w.x, w.y, 0.34 + sin(t) * 0.2) }
                val pa = cam.proj3(arch); val pt = cam.proj3(tire); val pm = cam.proj3(rim); val sv = PIN
                if (BUF != null && pa.size >= 3) PIN = min(if (sv == null) Double.POSITIVE_INFINITY else sv + 0.6, pa.d - 0.3)   // 바퀴는 자기 자리 깊이에
                fillPoly(c, pa, Color.rgb(13, 14, 17)); fillPoly(c, pt, Color.rgb(27, 28, 32)); fillPoly(c, pm, Color.rgb(125, 129, 137)); PIN = sv } }
        BIAS = 0.0; PIN = null
        if (cam.P.h > hoodH) fillPoly(c, cam.poly(top, hoodH, 3), shade(hex, 1.08))
        val cab = if (suv) doubleArrayOf(0.22, -0.98, 0.02, -0.86, 0.9, 0.8) else doubleArrayOf(0.3, -0.72, 0.02, -0.48, 0.88, 0.74)   // f0 r0 f1 r1 w0 w1
        val lo = listOf(at(cab[0], cab[4]), at(cab[0], -cab[4]), at(cab[1], -cab[4]), at(cab[1], cab[4]))
        val hi = listOf(at(cab[2], cab[5]), at(cab[2], -cab[5]), at(cab[3], -cab[5]), at(cab[3], cab[5]))
        val cf = (0 until 4).sortedByDescending { i -> cam.depth(lo[i]) + cam.depth(lo[(i + 1) % 4]) }
        for (i in cf) { val a = lo[i]; val d = lo[(i + 1) % 4]; val ha = hi[i]; val hc = hi[(i + 1) % 4]; if (!cam.sees(b.c, a, d)) continue; val L = cam.light(a, d)
            val cp = cam.quad2(a, d, hoodH, ha, hc, roofH); fillPoly(c, cp, shade(hex, L * 0.92)); val pinC = minD(cp) - 0.01
            fun P(t: Double, f: Double): Vec { val p0 = Vec(a.x + (d.x - a.x) * t, a.y + (d.y - a.y) * t); val p1 = Vec(ha.x + (hc.x - ha.x) * t, ha.y + (hc.y - ha.y) * t); return Vec(p0.x + (p1.x - p0.x) * f, p0.y + (p1.y - p0.y) * f) }
            fun zf(f: Double) = hoodH + (roofH - hoodH) * f
            val f0 = 0.1; val f1 = 0.84; val wins = if (i == 1 || i == 3) listOf(0.07 to 0.47, 0.53 to 0.93) else listOf(0.08 to 0.92)
            if (BUF != null && pinC.isFinite()) PIN = pinC else BIAS = 0.3
            for ((t0, t1) in wins) { fillPoly(c, cam.quad2(P(t0, f0), P(t1, f0), zf(f0), P(t0, f1), P(t1, f1), zf(f1)), shade(if (i == 0 || i == 2) Color.rgb(77, 97, 119) else Color.rgb(86, 105, 126), L * 1.05))
                fillPoly(c, cam.quad2(P(t0, f1 - 0.18), P(t1, f1 - 0.18), zf(f1 - 0.18), P(t0, f1), P(t1, f1), zf(f1)), shade(Color.rgb(143, 163, 184), L)) }
            BIAS = 0.0; PIN = null }
        if (cam.P.h > roofH) fillPoly(c, cam.poly(hi, roofH, 3), hex)
        val barZ = hoodH - 0.12; val segs = if (model == "AVANTE") listOf(-0.92 to -0.3, 0.3 to 0.92) else listOf(-0.92 to 0.92)
        val seeF = cam.sees(b.c, at(1.0, -1.0), at(1.0, 1.0)); val seeR = cam.sees(b.c, at(-1.0, 1.0), at(-1.0, -1.0))
        fun face(front: Boolean, w0: Double, w1: Double, z0: Double, z1: Double, col: Int) { val o = if (front) 0.004 else -0.004; val a0 = (if (front) duF(z0) else duR(z0)) + o; val a1 = (if (front) duF(z1) else duR(z1)) + o
            fillPoly(c, cam.quad2(at(a0, w0), at(a0, w1), z0, at(a1, w0), at(a1, w1), z1), col) }
        val AMB = Color.rgb(255, 179, 64)
        if (seeF) { pinAt(0); face(true, -0.97, 0.97, 0.25, 0.42, shade(hex, 0.62)); face(true, -0.42, 0.42, 0.44, barZ - 0.03, Color.rgb(22, 24, 29)); face(true, -0.17, 0.17, 0.29, 0.38, Color.rgb(233, 235, 239))
            for ((s0, s1) in segs) face(true, s0 * 0.93, s1 * 0.93, barZ + 0.06, barZ + 0.085, Color.rgb(244, 246, 255))
            for (sd in doubleArrayOf(1.0, -1.0)) face(true, sd * 0.6, sd * 0.93, barZ - 0.02, barZ + 0.06, if (blink) AMB else Color.rgb(223, 233, 255)) }
        if (seeR) { pinAt(2); face(false, -0.97, 0.97, 0.25, 0.44, shade(hex, 0.62)); face(false, -0.2, 0.2, 0.47, 0.6, Color.rgb(233, 235, 239))
            face(false, -0.92, 0.92, barZ + 0.03, barZ + 0.08, Color.rgb(224, 52, 44))
            for (sd in doubleArrayOf(1.0, -1.0)) face(false, sd * 0.62, sd * 0.93, barZ - 0.03, barZ + 0.08, if (blink) AMB else Color.rgb(200, 34, 28)) }
        BIAS = 0.0; PIN = null
        if (blink) for (dv in doubleArrayOf(1.0, -1.0)) fillPoly(c, cam.quad(at(0.3, dv), at(0.45, dv), hoodH - 0.2, hoodH - 0.1), AMB)   // 사이드미러 상자는 뺐다(떠 보였다)
    }
    fun drawPillar(cam: Cam, c: Canvas, b: OBB) {
        drawBox(cam, c, b, 0.0, 2.6, Color.rgb(154, 151, 143), Color.rgb(167, 164, 156))
        val cs = b.corners()
        for (i in 0 until 4) { val a = cs[i]; val d = cs[(i + 1) % 4]; if (!cam.sees(b.c, a, d)) continue
            run { val sv = BUF; BUF = null; val fp = cam.quad(a, d, 0.0, 2.6); BUF = sv; val m = minD(fp); if (BUF != null && m.isFinite()) PIN = m - 0.01 else BIAS = 0.2 }
            fillPoly(c, cam.quad(a, d, 0.25, 1.05), shade(Color.rgb(240, 192, 32), cam.light(a, d)))
            for (k in 0 until 3) { val t0 = k / 3.0 + 0.08; val t1 = k / 3.0 + 0.2
                fillPoly(c, cam.quad(Vec(a.x + (d.x - a.x) * t0, a.y + (d.y - a.y) * t0), Vec(a.x + (d.x - a.x) * t1, a.y + (d.y - a.y) * t1), 0.25, 1.05), shade(Color.rgb(21, 21, 26), cam.light(a, d))) } }
        BIAS = 0.0; PIN = null
    }
    fun drawWall(cam: Cam, c: Canvas, o: Obstacle) {
        if (o.label == "연석") { drawBox(cam, c, o.box, 0.0, 0.15, Color.rgb(93, 95, 102), Color.rgb(106, 108, 115)); return }
        drawBox(cam, c, o.box, 0.0, 2.6, Color.rgb(134, 132, 125), Color.rgb(143, 141, 134))
        val cs = o.box.corners(); BIAS = 0.2; for (i in 0 until 4) { val a = cs[i]; val d = cs[(i + 1) % 4]; if (cam.sees(o.box.c, a, d)) fillPoly(c, cam.quad(a, d, 0.45, 0.6), shade(Color.rgb(232, 185, 42), cam.light(a, d) * 0.9)) }; BIAS = 0.0
    }
    /** 돌발 인물·카트(view.js drawHazard3D 와 같다): 둥근 그림자 · 철망 바구니 카트 · 보폭 있는 다리와 흔드는 팔(아래팔 살색) */
    fun drawHazard3D(cam: Cam, c: Canvas, hz: Hazard, pose: Pose) {
        val r = hz.type.r; val h = hz.type.height; val p = hz.pos; val face = pose.heading + Math.PI / 2
        val moving = !hz.wasStopped || hz.pauseLeft <= 0; val sw = if (moving) sin(System.nanoTime() / 1.5e8) * 0.32 else 0.0
        val u = Vec(cos(face), sin(face)); val v = Vec(-sin(face), cos(face))
        fun at(dx: Double, dy: Double) = Vec(p.x + u.x * dx + v.x * dy, p.y + u.y * dx + v.y * dy)
        fillPoly(c, cam.poly((0 until 12).map { k -> val a = k / 12.0 * 2 * Math.PI; Vec(p.x + cos(a) * r * 1.1, p.y + sin(a) * r * 0.9) }, 0.005, 1), rgba(0, 0, 0, 0.28))
        if (hz.type == HazType.CART) {
            val wire = Color.rgb(174, 178, 186); val wireT = Color.rgb(207, 210, 216)
            drawBox(cam, c, OBB(p, face, 0.45, 0.3), 0.3, 0.34, Color.rgb(141, 144, 153), Color.rgb(162, 165, 173))
            fun bar(x: Double, y: Double, z0: Double, z1: Double, w: Double) = drawBox(cam, c, OBB(at(x, y), face, w, w), z0, z1, wire, wireT)
            for (x in doubleArrayOf(-0.43, -0.15, 0.15, 0.43)) for (y in doubleArrayOf(0.28, -0.28)) bar(x, y, 0.34, 0.92, 0.015)
            for (y in doubleArrayOf(-0.1, 0.1)) for (x in doubleArrayOf(0.43, -0.43)) bar(x, y, 0.34, 0.92, 0.015)
            for (y in doubleArrayOf(0.28, -0.28)) drawBox(cam, c, OBB(at(0.0, y), face, 0.45, 0.012), 0.6, 0.625, wire, wireT)
            for (x in doubleArrayOf(0.43, -0.43)) drawBox(cam, c, OBB(at(x, 0.0), face, 0.012, 0.3), 0.6, 0.625, wire, wireT)
            for ((q, hx) in listOf(doubleArrayOf(0.43, 0.0) to doubleArrayOf(0.025, 0.31), doubleArrayOf(-0.43, 0.0) to doubleArrayOf(0.025, 0.31), doubleArrayOf(0.0, 0.29) to doubleArrayOf(0.46, 0.025), doubleArrayOf(0.0, -0.29) to doubleArrayOf(0.46, 0.025)))
                drawBox(cam, c, OBB(at(q[0], q[1]), face, hx[0], hx[1]), 0.9, 0.95, Color.rgb(201, 204, 211), Color.rgb(227, 229, 234))   // 윗테
            for ((dx, dy) in listOf(0.36 to 0.24, 0.36 to -0.24, -0.36 to 0.24, -0.36 to -0.24)) { drawBox(cam, c, OBB(at(dx, dy), face, 0.05, 0.05), 0.0, 0.14, Color.rgb(31, 33, 38), Color.rgb(42, 45, 51)); drawBox(cam, c, OBB(at(dx, dy), face, 0.025, 0.025), 0.14, 0.3, Color.rgb(141, 144, 153), Color.rgb(162, 165, 173)) }
            drawBox(cam, c, OBB(at(-0.43, 0.0), face, 0.05, 0.3), 0.9, 0.99, Color.rgb(216, 54, 47), Color.rgb(226, 74, 67)); return }   // 손잡이 = 바구니 뒤 윗모서리
        val kid = hz.type == HazType.CHILD; val skin = Color.rgb(233, 181, 143); val shirt = if (kid) Color.rgb(242, 201, 76) else Color.rgb(59, 111, 209); val pants = if (kid) Color.rgb(90, 123, 209) else Color.rgb(44, 49, 64); val shoe = Color.rgb(26, 28, 34); val hair = if (kid) Color.rgb(74, 51, 40) else Color.rgb(43, 33, 28)
        val lw = r * 0.26; val legH = h * 0.47; val legX = r * 0.42; val st = if (moving) sw * 0.9 else 0.12; val arm = if (moving) -sw * 0.8 else -0.08
        drawBox(cam, c, OBB(at(st, legX), face, lw, lw), 0.0, legH, pants, pants); drawBox(cam, c, OBB(at(-st, -legX), face, lw, lw), 0.0, legH, pants, pants)
        drawBox(cam, c, OBB(at(st + 0.03, legX), face, lw * 1.3, lw * 1.1), 0.0, 0.07, shoe, shoe); drawBox(cam, c, OBB(at(-st + 0.03, -legX), face, lw * 1.3, lw * 1.1), 0.0, 0.07, shoe, shoe)
        drawBox(cam, c, OBB(p, face, r * 0.55, r * 1.0), legH, h * 0.85, shirt, shirt)
        val armX = r * 1.0 + lw * 0.7
        for (sd in doubleArrayOf(1.0, -1.0)) { val ab = OBB(at(arm * sd, armX * sd), face, lw * 0.85, lw * 0.85); drawBox(cam, c, ab, h * 0.5, h * 0.66, skin, skin); drawBox(cam, c, ab, h * 0.66, h * 0.84, shirt, shirt) }
        drawBox(cam, c, OBB(p, face, r * 0.5, r * 0.5), h * 0.85, h, skin, skin)
        drawBox(cam, c, OBB(p, face, r * 0.52, r * 0.52), h * 0.97, h + 0.03, hair, hair)
    }

    private fun lineBand(a: Vec, b: Vec, w: Double): List<Vec> { val dx = b.x - a.x; val dy = b.y - a.y; val L = hypot(dx, dy).let { if (it == 0.0) 1.0 else it }
        val nx = -dy / L * w / 2; val ny = dx / L * w / 2; return listOf(Vec(a.x + nx, a.y + ny), Vec(b.x + nx, b.y + ny), Vec(b.x - nx, b.y - ny), Vec(a.x - nx, a.y - ny)) }

    /** 사이드미러에 비치는 내 차 옆구리(PM 2026-10-26 '더 현실적으로', view.js drawOwnSide 와 같은 수식): 실·도어 패널(어깨선)·이음선·손잡이·창 띠(B·C 필러)·뒷바퀴 아치와 타이어·휠·돌아간 램프. side = 보는 거울 쪽(+1 오른쪽). */
    fun drawOwnSide(cam: Cam, c: Canvas, b: OBB, hex: Int, blink: Boolean, model: String, side: Int) {
        val M = CarSpec.byKey(model); val suv = M.shape == "suv"; val hoodH = M.hoodH; val roofH = M.roofH
        val u = Vec(cos(b.heading), sin(b.heading)); val v = Vec(-sin(b.heading), cos(b.heading)); val dvS = -side.toDouble()
        fun at(du: Double, dv: Double) = Vec(b.c.x + u.x * b.hx * du + v.x * b.hy * dv, b.c.y + u.y * b.hx * du + v.y * b.hy * dv)
        fun p3(pts: List<DoubleArray>): List<FloatArray> = cam.clipNear(pts.map { cam.toCam(Vec(it[0], it[1]), it[2]) }).map { cam.toScreen(it) }
        fun band(du0: Double, du1: Double, dv0: Double, z0: Double, dv1: Double, z1: Double, col: Int) = fillPoly(c, cam.quad2(at(du0, dvS * dv0), at(du1, dvS * dv0), z0, at(du0, dvS * dv1), at(du1, dvS * dv1), z1), col)
        val F = 0.98; val R = -1.0; val f0 = if (suv) 0.22 else 0.3; val r0 = if (suv) -0.98 else -0.72
        val sh = hoodH - 0.1
        fillPoly(c, cam.poly(OBB(b.c, b.heading, b.hx + 0.15, b.hy + 0.2).corners(), 0.005, 3), rgba(0, 0, 0, 0.35))
        fillPoly(c, cam.quad(at(R, -0.92), at(R, 0.92), 0.25, hoodH), shade(hex, 0.72)); fillPoly(c, cam.quad(at(R, -0.92), at(R, 0.92), hoodH - 0.1, hoodH - 0.03), Color.rgb(224, 52, 44))
        band(F, R, 0.96, 0.22, 1.0, 0.42, shade(hex, 0.5))
        band(F, R, 1.0, 0.42, 1.02, 0.62, shade(hex, 0.78)); band(F, R, 1.02, 0.62, 1.035, sh - 0.08, shade(hex, 0.96)); band(F, R, 1.035, sh - 0.08, 1.035, sh, shade(hex, 1.22))
        band(F, R, 1.035, sh, 1.0, hoodH, hex)
        band(F, R, 1.0, 0.405, 1.0, 0.425, shade(hex, 0.35))
        for (du in doubleArrayOf(0.08, r0 + 0.02)) band(du, du - 0.014, 1.04, 0.44, 1.04, sh - 0.02, shade(hex, 0.45))
        band(-0.22, -0.4, 1.045, sh - 0.3, 1.045, sh - 0.24, shade(hex, 0.6)); band(-0.22, -0.4, 1.05, sh - 0.3, 1.05, sh - 0.285, shade(hex, 1.3))
        val g0 = min(F, f0 + 0.04); val g1 = r0; band(g0, g1, 1.0, hoodH, 0.94, roofH - 0.02, Color.rgb(27, 38, 51))
        fillPoly(c, cam.quad2(at(g0 - 0.02, dvS * 1.0), at(g0 + 0.02, dvS * 1.0), hoodH, at(g0 - 0.03, dvS * 0.95), at(g0 + 0.01, dvS * 0.95), roofH - 0.02), Color.rgb(14, 17, 22))
        val bp = (f0 + r0) / 2 - 0.06; band(bp + 0.02, bp - 0.02, 1.005, hoodH, 0.95, roofH - 0.02, Color.rgb(14, 17, 22))
        fillPoly(c, cam.quad2(at(g1 + 0.12, dvS * 1.0), at(g1, dvS * 1.0), hoodH, at(g1 + 0.02, dvS * 0.95), at(g1 - 0.06, dvS * 0.95), roofH - 0.02), shade(hex, 0.85))
        band(F, R, 0.94, roofH - 0.02, 0.9, roofH, shade(hex, 1.1))
        val rr = 0.36 / b.hx; val zc = 0.34
        val arch = ArrayList<DoubleArray>(); val tire = ArrayList<DoubleArray>(); val rim = ArrayList<DoubleArray>()
        for (i in 0..14) { val t = i / 14.0 * Math.PI; val q = at(-0.6 + cos(t) * rr * 1.15, dvS * 1.01); arch += doubleArrayOf(q.x, q.y, zc + sin(t) * 0.36 * 1.15) }
        run { val q1 = at(-0.6 - rr * 1.15, dvS * 1.01); arch += doubleArrayOf(q1.x, q1.y, 0.12); val q2 = at(-0.6 + rr * 1.15, dvS * 1.01); arch += doubleArrayOf(q2.x, q2.y, 0.12) }
        for (i in 0 until 20) { val t = i / 20.0 * 2 * Math.PI; val q = at(-0.6 + cos(t) * rr, dvS * 0.985); tire += doubleArrayOf(q.x, q.y, zc + sin(t) * 0.34); val w = at(-0.6 + cos(t) * rr * 0.62, dvS * 0.99); rim += doubleArrayOf(w.x, w.y, zc + sin(t) * 0.34 * 0.62) }
        fillPoly(c, p3(arch), Color.rgb(16, 17, 20)); fillPoly(c, p3(tire), Color.rgb(28, 29, 34)); fillPoly(c, p3(rim), Color.rgb(141, 145, 153))
        fillPoly(c, p3(rim.mapIndexed { i, q -> if (i % 2 == 1) q else doubleArrayOf(q[0], q[1], zc + (q[2] - zc) * 0.3) }), Color.rgb(92, 96, 104))
        fillPoly(c, cam.quad(at(R + 0.02, dvS * 1.0), at(R + 0.14, dvS * 1.0), hoodH - 0.14, hoodH - 0.02), if (blink) Color.rgb(255, 179, 64) else Color.rgb(200, 34, 28))
        if (blink) fillPoly(c, cam.quad(at(0.3, dvS * 1.04), at(0.45, dvS * 1.04), hoodH - 0.2, hoodH - 0.1), Color.rgb(255, 179, 64))
    }
    /** 장면: 바닥·천장·주차선·장애물·돌발 (+ ownBody 면 내 차 옆구리). */
    fun drawScene(cam: Cam, c: Canvas, W: Float, H: Float, spec: CarSpec, snap: GameSnapshot, sc: Scenario, ownBody: Boolean, blink: Boolean, ownSide: Int = 0) {
        val env = sc.env; val under = env == "underground"; val night = env == "night"; val rain = env == "rain"; val P = cam.P
        val pBg = Paint()
        if (under) { pBg.color = Color.rgb(42, 43, 47); c.drawRect(0f, 0f, W, H, pBg) }
        else { val (c0, c1) = if (night) Color.rgb(7, 10, 20) to Color.rgb(26, 34, 53) else if (rain) Color.rgb(74, 80, 88) to Color.rgb(138, 144, 153) else Color.rgb(95, 127, 168) to Color.rgb(185, 198, 214)
            pBg.shader = LinearGradient(0f, 0f, 0f, H * 0.55f, c0, c1, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, W, H, pBg); pBg.shader = null }
        val B = sc.bounds; val big = OBB(B.c, 0.0, B.hx + 14, B.hy + 14)
        fillPoly(c, cam.poly(big.corners(), 0.0, 10), if (under) Color.rgb(58, 59, 64) else if (night) Color.rgb(31, 33, 38) else if (rain) Color.rgb(51, 54, 60) else Color.rgb(75, 77, 82))
        if (night || rain) for (l in listOf(Vec(B.c.x - B.hx, B.c.y + B.hy + 1), Vec(B.c.x + B.hx, B.c.y + B.hy + 1), Vec(B.c.x, B.c.y - B.hy - 1))) {
            val pts = cam.poly(OBB(l, 0.0, 3.2, 3.2).corners(), 0.002, 8); if (pts.isEmpty()) continue
            fillPoly(c, pts, if (night) rgba(255, 214, 150, 0.13) else rgba(200, 210, 230, 0.10)) }
        val hz0 = cam.toScreen(doubleArrayOf(0.0, (0 - P.h) * cos(P.pitch) + 60 * sin(P.pitch), 60 * cos(P.pitch) - (0 - P.h) * sin(P.pitch)))[1]
        val top = max(0f, hz0)
        pBg.shader = LinearGradient(0f, hz0, 0f, H, intArrayOf(if (under) rgba(20, 20, 24, 0.55) else rgba(120, 130, 150, 0.35), Color.TRANSPARENT, rgba(0, 0, 0, 0.25)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, top, W, H, pBg); pBg.shader = null
        if (under) {
            fillPoly(c, cam.poly(big.corners(), 2.7, 10), Color.rgb(74, 75, 80))
            val x0 = Math.floor((B.c.x - B.hx - 10) / 5) * 5; val x1 = B.c.x + B.hx + 10
            var x = x0
            while (x <= x1) { var y = Math.floor(B.c.y - B.hy - 8)
                while (y <= B.c.y + B.hy + 8) { val pts = cam.poly(OBB(Vec(x, y + 3), 0.0, 0.15, 1.3).corners(), 2.62, 2); fillPoly(c, pts, Color.rgb(244, 241, 216))
                    if (pts.size > 0) { val cx = pts.pts.map { it[0] }.average().toFloat(); val cy = pts.pts.map { it[1] }.average().toFloat()
                        pBg.shader = RadialGradient(cx, cy, 60f, rgba(255, 250, 210, 0.35), Color.TRANSPARENT, Shader.TileMode.CLAMP); c.drawRect(cx - 60, cy - 60, cx + 60, cy + 60, pBg); pBg.shader = null }
                    y += 6 }
                x += 5 }
        }
        // 0.4 m 조각(카메라 밑을 지나는 긴 선이 '서 있는 지느러미'가 되던 것) · 사선 칸은 칸 사이 긴 선만(톱니처럼 겹쳤다)
        fun slotLines(b: OBB, col: Int, longOnly: Boolean = false) { val cs = b.corners(); for (i in 0 until 4) { if (longOnly && i % 2 == 0) continue; val p = cs[i]; val q = cs[(i + 1) % 4]; val n = max(1, Math.ceil(hypot(q.x - p.x, q.y - p.y) / 0.4).toInt())
            for (k in 0 until n) { val t0 = k.toDouble() / n; val t1 = (k + 1.0) / n; fillPoly(c, cam.poly(lineBand(Vec(p.x + (q.x - p.x) * t0, p.y + (q.y - p.y) * t0), Vec(p.x + (q.x - p.x) * t1, p.y + (q.y - p.y) * t1), 0.12), 0.003, 2), col) } } }
        sc.marks.forEach { slotLines(it, if (night) rgba(235, 235, 230, 0.4) else rgba(235, 235, 230, 0.55), sc.kind == app.parkingcoach.sim.Kind.ANGLED) }
        if (snap.phase == Phase.PARKED) fillPoly(c, cam.poly(sc.slot.corners(), 0.002, 4), rgba(58, 209, 124, 0.25))
        slotLines(sc.slot, rgba(250, 250, 245, 0.95))
        run { val u = Vec(cos(sc.slot.heading), sin(sc.slot.heading)); val cc = Vec(sc.slot.c.x + u.x * sc.slot.hx * 0.4, sc.slot.c.y + u.y * sc.slot.hx * 0.4)
            fillPoly(c, cam.poly(OBB(cc, sc.slot.heading, 0.35, 0.25).corners(), 0.004, 8), rgba(58, 209, 124, 0.55)) }
        // 면 단위 정렬로 모았다가 먼 것부터. 렌즈가 다른 차에 맞닿으면(20 cm 안) 그 차는 그리지 않고 화면을 그 차 색으로 덮는다
        var touch: Obstacle? = null; BUF = ArrayList(); SEQ = 0
        for (o in sc.obstacles.sortedByDescending { cam.depth(it.box.c) }) {
            if (o.isCar && o.box.distTo(cam.c) < 0.2) { touch = o; continue }
            if (o.isCar) drawCar3D(cam, c, o.box, o.color, false, o.model) else if (o.label == "기둥") drawPillar(cam, c, o.box) else drawWall(cam, c, o)
        }
        snap.hazard?.let { drawHazard3D(cam, c, it, snap.pose) }
        flushBuf(c)
        touch?.let { t -> pBg.shader = LinearGradient(0f, 0f, W * 0.3f, H, intArrayOf(shade(t.color, 0.7), shade(t.color, 0.45), shade(t.color, 0.3)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP); c.drawRect(0f, 0f, W, H, pBg)
            pBg.shader = LinearGradient(0f, H * 0.2f, 0f, H * 0.5f, intArrayOf(Color.TRANSPARENT, rgba(255, 255, 255, 0.12), Color.TRANSPARENT), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP); c.drawRect(0f, H * 0.2f, W, H * 0.5f, pBg); pBg.shader = null }
        if (ownSide != 0) drawOwnSide(cam, c, snap.body, Color.rgb(47, 109, 246), blink, spec.key, ownSide) else if (ownBody) drawCar3D(cam, c, snap.body, Color.rgb(47, 109, 246), blink, spec.key)
    }

    private val rainRnd = Random(3)
    fun drawRain(c: Canvas, W: Float, H: Float) {
        pStroke.color = rgba(220, 230, 245, 0.35); pStroke.strokeWidth = max(1f, W / 600); path.reset()
        for (i in 0 until 70) { val x = rainRnd.nextFloat() * W; val y = rainRnd.nextFloat() * H; val L = H * (0.02f + rainRnd.nextFloat() * 0.05f); path.moveTo(x, y); path.lineTo(x - L * 0.15f, y + L) }
        c.drawPath(path, pStroke); pFill.color = rgba(150, 165, 190, 0.08); c.drawRect(0f, 0f, W, H, pFill)
    }
    fun drawNight(c: Canvas, W: Float, H: Float) { pFill.color = rgba(5, 8, 16, 0.28); c.drawRect(0f, 0f, W, H, pFill) }
    fun vignette(c: Canvas, W: Float, H: Float, r0: Float, r1: Float, a: Double) {
        val p = Paint(); p.shader = RadialGradient(W / 2, H / 2, r1, intArrayOf(Color.TRANSPARENT, rgba(0, 0, 0, a)), floatArrayOf(r0 / r1, 1f), Shader.TileMode.CLAMP); c.drawRect(0f, 0f, W, H, p)
    }

    /** 8방향 주차 거리 센서(현대 PDW): 앞 3 · 뒤 3 · 옆 2 자리에서 가장 가까운 장애물·사람까지 거리(m) */
    data class Sensor(val k: String, val p: Vec, val d: Double, val du: Double, val dv: Double)
    fun sensors(snap: GameSnapshot, sc: Scenario): List<Sensor> {
        val b = snap.body; val u = Vec(cos(b.heading), sin(b.heading)); val v = Vec(-sin(b.heading), cos(b.heading))
        fun at(du: Double, dv: Double) = Vec(b.c.x + u.x * b.hx * du + v.x * b.hy * dv, b.c.y + u.y * b.hx * du + v.y * b.hy * dv)
        val spots = listOf(Triple("FL", 1.0, 0.7), Triple("F", 1.0, 0.0), Triple("FR", 1.0, -0.7), Triple("RL", -1.0, 0.7), Triple("R", -1.0, 0.0), Triple("RR", -1.0, -0.7), Triple("L", 0.0, 1.0), Triple("Rt", 0.0, -1.0))
        return spots.map { (k, du, dv) -> val p = at(du, dv); var d = 9.0
            for (o in sc.obstacles) d = min(d, o.box.distTo(p))
            snap.hazard?.let { d = min(d, max(0.0, hypot(it.pos.x - p.x, it.pos.y - p.y) - it.type.r)) }
            if (du == 0.0 && d >= 0.3) d = 9.0   // 측방은 30 cm 안만(기아 KA4 2026 설명서, sensors() 시안과 같다)
            Sensor(k, p, d, du, dv) }
    }
    fun pdwLevel(d: Double) = if (d < 0.3) 3 else if (d < 0.6) 2 else if (d < 1.2) 1 else 0
    val PDW_COL = intArrayOf(rgba(255, 255, 255, 0.10), Color.rgb(255, 210, 40), Color.rgb(255, 140, 43), Color.rgb(255, 77, 77))
    fun angleOf(a: Vec, b: Vec) = atan2(b.y - a.y, b.x - a.x)
}
