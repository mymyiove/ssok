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
    val REAR = CamParams(0.95, Math.toRadians(25.0), Math.toRadians(140.0), 0.12, 0.95, true)
    val FRONT = CamParams(0.95, Math.toRadians(25.0), Math.toRadians(140.0), 0.12, 0.95, false)
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
        fun clipNear(poly: List<DoubleArray>): List<DoubleArray> {
            val out = ArrayList<DoubleArray>(poly.size + 2); val n = P.near
            for (i in poly.indices) { val a = poly[i]; val b = poly[(i + 1) % poly.size]; val ia = a[2] >= n; val ib = b[2] >= n
                if (ia) out += a
                if (ia != ib) { val t = (n - a[2]) / (b[2] - a[2]); out += doubleArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, n) } }
            return out
        }
        /** 바닥 다각형(높이 z), 변마다 sub 등분 → 어안이 자연스럽다 */
        fun poly(pts: List<Vec>, z: Double, sub: Int = 6): List<FloatArray> {
            val w = ArrayList<Vec>(pts.size * sub)
            for (i in pts.indices) { val a = pts[i]; val b = pts[(i + 1) % pts.size]; for (j in 0 until sub) { val t = j.toDouble() / sub; w += Vec(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t) } }
            return clipNear(w.map { toCam(it, z) }).map { toScreen(it) }
        }
        /** 수직 사각형: 아래 변 a→b(za) 와 같은 위 변(zb) */
        fun quad(a: Vec, b: Vec, za: Double, zb: Double): List<FloatArray> = quad2(a, b, za, a, b, zb)
        /** 아래 변 a→b(za) 와 위 변 c→d(zb) 가 다른 사각형(기운 캐빈 유리) */
        fun quad2(a: Vec, b: Vec, za: Double, c: Vec, d: Vec, zb: Double): List<FloatArray> {
            val m = 6; val cam = ArrayList<DoubleArray>(2 * (m + 1))
            for (j in 0..m) { val t = j.toDouble() / m; cam += toCam(Vec(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t), za) }
            for (j in m downTo 0) { val t = j.toDouble() / m; cam += toCam(Vec(c.x + (d.x - c.x) * t, c.y + (d.y - c.y) * t), zb) }
            return clipNear(cam).map { toScreen(it) }
        }
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
        for ((a, d) in faces) fillPoly(c, cam.quad(a, d, z0, z1), shade(base, cam.light(a, d)))
        fillPoly(c, cam.poly(cs, z1, 3), shade(top, 1.05))
    }

    /** 차(현대차 꼴): 차체 + 기운 캐빈(세단은 뒤로 앉고 SUV 는 길고 높다) + 앞 가로 한 줄 램프(아반떼는 두 토막)·뒤 빨간 띠 + 바퀴·미러·그림자 */
    fun drawCar3D(cam: Cam, c: Canvas, b: OBB, hex: Int, blink: Boolean, model: String) {
        val M = CarSpec.byKey(model); val suv = M.shape == "suv"; val hoodH = M.hoodH; val roofH = M.roofH
        val u = Vec(cos(b.heading), sin(b.heading)); val v = Vec(-sin(b.heading), cos(b.heading))
        fun at(du: Double, dv: Double) = Vec(b.c.x + u.x * b.hx * du + v.x * b.hy * dv, b.c.y + u.y * b.hx * du + v.y * b.hy * dv)
        for ((du, dv) in listOf(0.6 to 0.86, 0.6 to -0.86, -0.6 to 0.86, -0.6 to -0.86)) drawBox(cam, c, OBB(at(du, dv), b.heading, 0.34, 0.11), 0.0, 0.64, Color.rgb(21, 22, 26), Color.rgb(28, 29, 34))
        fillPoly(c, cam.poly(OBB(b.c, b.heading, b.hx + 0.15, b.hy + 0.2).corners(), 0.005, 3), rgba(0, 0, 0, 0.35))
        val cs = b.corners()
        val faces = (0 until 4).sortedByDescending { i -> cam.depth(cs[i]) + cam.depth(cs[(i + 1) % 4]) }
        val top = OBB(b.c, b.heading, b.hx * 0.99, b.hy * 0.96).corners()
        for (i in faces) fillPoly(c, cam.quad2(cs[i], cs[(i + 1) % 4], 0.25, top[i], top[(i + 1) % 4], hoodH), shade(hex, cam.light(cs[i], cs[(i + 1) % 4])))
        fillPoly(c, cam.poly(top, hoodH, 3), shade(hex, 1.08))
        val cab = if (suv) doubleArrayOf(0.22, -0.98, 0.02, -0.86, 0.9, 0.8) else doubleArrayOf(0.3, -0.72, 0.02, -0.48, 0.88, 0.74)   // f0 r0 f1 r1 w0 w1
        val lo = listOf(at(cab[0], cab[4]), at(cab[0], -cab[4]), at(cab[1], -cab[4]), at(cab[1], cab[4]))
        val hi = listOf(at(cab[2], cab[5]), at(cab[2], -cab[5]), at(cab[3], -cab[5]), at(cab[3], cab[5]))
        val cf = (0 until 4).sortedByDescending { i -> cam.depth(lo[i]) + cam.depth(lo[(i + 1) % 4]) }
        for (i in cf) { val glass = if (i == 0 || i == 2) Color.rgb(27, 38, 51) else Color.rgb(34, 48, 64)
            fillPoly(c, cam.quad2(lo[i], lo[(i + 1) % 4], hoodH, hi[i], hi[(i + 1) % 4], roofH), shade(glass, cam.light(lo[i], lo[(i + 1) % 4]) * 1.1)) }
        fillPoly(c, cam.poly(hi, roofH, 3), hex)
        if (suv) for (dv in doubleArrayOf(0.62, -0.62)) fillPoly(c, cam.quad(at(cab[2] - 0.02, dv), at(cab[3] + 0.02, dv), roofH, roofH + 0.05), Color.rgb(42, 44, 49))
        val barZ = hoodH - 0.12
        val segs = if (model == "AVANTE") listOf(-0.92 to -0.3, 0.3 to 0.92) else listOf(-0.92 to 0.92)
        for ((s0, s1) in segs) fillPoly(c, cam.quad(at(1.0, s0), at(1.0, s1), barZ, barZ + 0.05), Color.rgb(244, 246, 255))
        fillPoly(c, cam.quad(at(-1.0, -0.92), at(-1.0, 0.92), barZ + 0.02, barZ + 0.09), Color.rgb(224, 52, 44))
        for (side in doubleArrayOf(1.0, -1.0)) for (dv in doubleArrayOf(0.86, -0.86)) { val cc = at(side, dv)
            val a = Vec(cc.x - v.x * 0.14, cc.y - v.y * 0.14); val d = Vec(cc.x + v.x * 0.14, cc.y + v.y * 0.14)
            fillPoly(c, cam.quad(a, d, barZ - 0.04, barZ + 0.12), if (blink) Color.rgb(255, 179, 64) else if (side == 1.0) Color.rgb(223, 233, 255) else Color.rgb(200, 34, 28)) }
        for (dv in doubleArrayOf(1.0, -1.0)) { val cc = at(cab[0] - 0.04, dv * 1.06); drawBox(cam, c, OBB(cc, b.heading, 0.08, 0.06), hoodH + 0.08, hoodH + 0.2, shade(hex, 0.9), shade(hex, 1.05))
            if (blink) fillPoly(c, cam.quad(at(0.3, dv), at(0.45, dv), hoodH - 0.2, hoodH - 0.1), Color.rgb(255, 179, 64)) }
    }
    fun drawPillar(cam: Cam, c: Canvas, b: OBB) {
        drawBox(cam, c, b, 0.0, 2.6, Color.rgb(154, 151, 143), Color.rgb(167, 164, 156))
        val cs = b.corners()
        for (i in 0 until 4) { val a = cs[i]; val d = cs[(i + 1) % 4]; if (cam.depth(a) < 0 && cam.depth(d) < 0) continue
            fillPoly(c, cam.quad(a, d, 0.25, 1.05), shade(Color.rgb(240, 192, 32), cam.light(a, d)))
            for (k in 0 until 3) { val t0 = k / 3.0 + 0.08; val t1 = k / 3.0 + 0.2
                fillPoly(c, cam.quad(Vec(a.x + (d.x - a.x) * t0, a.y + (d.y - a.y) * t0), Vec(a.x + (d.x - a.x) * t1, a.y + (d.y - a.y) * t1), 0.25, 1.05), shade(Color.rgb(21, 21, 26), cam.light(a, d))) } }
    }
    fun drawWall(cam: Cam, c: Canvas, o: Obstacle) {
        if (o.label == "연석") { drawBox(cam, c, o.box, 0.0, 0.15, Color.rgb(139, 141, 147), Color.rgb(154, 156, 163)); return }
        drawBox(cam, c, o.box, 0.0, 2.6, Color.rgb(134, 132, 125), Color.rgb(143, 141, 134))
        val cs = o.box.corners(); for (i in 0 until 4) { val a = cs[i]; val d = cs[(i + 1) % 4]; fillPoly(c, cam.quad(a, d, 0.45, 0.6), shade(Color.rgb(232, 185, 42), cam.light(a, d) * 0.9)) }
    }
    /** 돌발 인물·카트 v2(PM 2026-10-27 "싼티 나게 글로만 말고 마인크래프트 사람처럼이라도", view.js drawHazard3D 와 같은 그림): 블록 사람(다리·신발·몸통·팔·머리·머리카락, 움직일 땐 팔다리가 흔들림) · 카트(바구니·바퀴 넷·손잡이) */
    fun drawHazard3D(cam: Cam, c: Canvas, hz: Hazard, pose: Pose) {
        val r = hz.type.r; val h = hz.type.height; val p = hz.pos; val face = pose.heading + Math.PI / 2
        val moving = !hz.wasStopped || hz.pauseLeft <= 0; val sw = if (moving) sin(System.nanoTime() / 1.5e8) * 0.32 else 0.0
        val u = Vec(cos(face), sin(face)); val v = Vec(-sin(face), cos(face))
        fun at(dx: Double, dy: Double) = Vec(p.x + u.x * dx + v.x * dy, p.y + u.y * dx + v.y * dy)
        fillPoly(c, cam.poly(OBB(p, face, r * 1.4, r * 1.4).corners(), 0.005, 3), rgba(0, 0, 0, 0.35))
        if (hz.type == HazType.CART) {
            drawBox(cam, c, OBB(p, face, 0.45, 0.3), 0.3, 0.95, Color.rgb(185, 188, 196), Color.rgb(215, 218, 224))                                   // 바구니
            for ((dx, dy) in listOf(0.36 to 0.24, 0.36 to -0.24, -0.36 to 0.24, -0.36 to -0.24)) drawBox(cam, c, OBB(at(dx, dy), face, 0.05, 0.05), 0.0, 0.14, Color.rgb(31, 33, 38), Color.rgb(42, 45, 51))   // 바퀴
            drawBox(cam, c, OBB(at(-0.5, 0.0), face, 0.04, 0.3), 0.95, 1.05, Color.rgb(216, 54, 47), Color.rgb(226, 74, 67)); return }                 // 손잡이
        val kid = hz.type == HazType.CHILD; val skin = Color.rgb(233, 181, 143); val shirt = if (kid) Color.rgb(242, 201, 76) else Color.rgb(59, 111, 209); val pants = if (kid) Color.rgb(90, 123, 209) else Color.rgb(44, 49, 64); val shoe = Color.rgb(26, 28, 34); val hair = if (kid) Color.rgb(74, 51, 40) else Color.rgb(43, 33, 28)
        val lw = r * 0.32; val legH = h * 0.47; val legX = r * 0.28
        drawBox(cam, c, OBB(at(sw * 0.35, legX), face, lw, lw), 0.0, legH, pants, pants); drawBox(cam, c, OBB(at(-sw * 0.35, -legX), face, lw, lw), 0.0, legH, pants, pants)   // 다리
        drawBox(cam, c, OBB(at(sw * 0.35, legX), face, lw * 1.1, lw * 1.1), 0.0, 0.07, shoe, shoe); drawBox(cam, c, OBB(at(-sw * 0.35, -legX), face, lw * 1.1, lw * 1.1), 0.0, 0.07, shoe, shoe)
        drawBox(cam, c, OBB(p, face, r * 0.55, r * 1.0), legH, h * 0.85, shirt, shirt)                                                                  // 몸통
        val armX = r * 1.0 + lw * 0.6
        drawBox(cam, c, OBB(at(-sw * 0.4, armX), face, lw * 0.8, lw * 0.8), h * 0.5, h * 0.85, shirt, skin); drawBox(cam, c, OBB(at(sw * 0.4, -armX), face, lw * 0.8, lw * 0.8), h * 0.5, h * 0.85, shirt, skin)   // 팔
        drawBox(cam, c, OBB(p, face, r * 0.5, r * 0.5), h * 0.85, h, skin, skin)                                                                        // 머리
        drawBox(cam, c, OBB(p, face, r * 0.52, r * 0.52), h * 0.97, h + 0.03, hair, hair)                                                               // 머리카락
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
                    if (pts.isNotEmpty()) { val cx = pts.map { it[0] }.average().toFloat(); val cy = pts.map { it[1] }.average().toFloat()
                        pBg.shader = RadialGradient(cx, cy, 60f, rgba(255, 250, 210, 0.35), Color.TRANSPARENT, Shader.TileMode.CLAMP); c.drawRect(cx - 60, cy - 60, cx + 60, cy + 60, pBg); pBg.shader = null }
                    y += 6 }
                x += 5 }
        }
        fun slotLines(b: OBB, col: Int) { val cs = b.corners(); for (i in 0 until 4) fillPoly(c, cam.poly(lineBand(cs[i], cs[(i + 1) % 4], 0.12), 0.003, 4), col) }
        sc.marks.forEach { slotLines(it, if (night) rgba(235, 235, 230, 0.4) else rgba(235, 235, 230, 0.55)) }
        if (snap.phase == Phase.PARKED) fillPoly(c, cam.poly(sc.slot.corners(), 0.002, 4), rgba(58, 209, 124, 0.25))
        slotLines(sc.slot, rgba(250, 250, 245, 0.95))
        run { val u = Vec(cos(sc.slot.heading), sin(sc.slot.heading)); val cc = Vec(sc.slot.c.x + u.x * sc.slot.hx * 0.4, sc.slot.c.y + u.y * sc.slot.hx * 0.4)
            fillPoly(c, cam.poly(OBB(cc, sc.slot.heading, 0.35, 0.25).corners(), 0.004, 3), rgba(58, 209, 124, 0.55)) }
        for (o in sc.obstacles.sortedByDescending { cam.depth(it.box.c) }) {
            if (o.isCar) drawCar3D(cam, c, o.box, o.color, false, o.model) else if (o.label == "기둥") drawPillar(cam, c, o.box) else drawWall(cam, c, o)
        }
        snap.hazard?.let { drawHazard3D(cam, c, it, snap.pose) }
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
            Sensor(k, p, d, du, dv) }
    }
    fun pdwLevel(d: Double) = if (d < 0.3) 3 else if (d < 0.6) 2 else if (d < 1.2) 1 else 0
    val PDW_COL = intArrayOf(rgba(255, 255, 255, 0.10), Color.rgb(255, 210, 40), Color.rgb(255, 140, 43), Color.rgb(255, 77, 77))
    fun angleOf(a: Vec, b: Vec) = atan2(b.y - a.y, b.x - a.x)
}
