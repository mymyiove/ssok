package app.parkingcoach.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import app.parkingcoach.game.GameEngine
import app.parkingcoach.game.GameSnapshot
import app.parkingcoach.game.Phase
import app.parkingcoach.sim.CarModel
import app.parkingcoach.sim.CarSpec
import app.parkingcoach.sim.OBB
import app.parkingcoach.sim.Scenario
import app.parkingcoach.sim.Vec
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 어라운드뷰 탑뷰(view.js drawTopView): 아스팔트·주차선·현대차 꼴 탑뷰 차·가이드라인·앞바퀴·주차 거리 경고 호·돌발. 리플레이·미리보기도 같은 그리기를 쓴다. */
object TopDraw {
    class Map(val ox: Float, val oy: Float, val scale: Float) { fun X(x: Double) = ox + (x * scale).toFloat(); fun Y(y: Double) = oy - (y * scale).toFloat() }
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val path = Path(); private val rect = RectF()

    private fun poly(c: Canvas, m: Map, pts: List<Vec>, fill: Int?, stroke: Int?, lw: Float = 1f, dash: FloatArray? = null) {
        path.reset(); pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(m.X(p.x), m.Y(p.y)) else path.lineTo(m.X(p.x), m.Y(p.y)) }; path.close()
        if (fill != null) { pFill.color = fill; c.drawPath(path, pFill) }
        if (stroke != null) { pStroke.color = stroke; pStroke.strokeWidth = lw; pStroke.pathEffect = dash?.let { android.graphics.DashPathEffect(it, 0f) }; c.drawPath(path, pStroke); pStroke.pathEffect = null }
    }
    private fun line(c: Canvas, m: Map, pts: List<Vec>, col: Int, lw: Float, dash: FloatArray? = null) {
        if (pts.size < 2) return
        path.reset(); pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(m.X(p.x), m.Y(p.y)) else path.lineTo(m.X(p.x), m.Y(p.y)) }
        pStroke.color = col; pStroke.strokeWidth = lw; pStroke.pathEffect = dash?.let { android.graphics.DashPathEffect(it, 0f) }; c.drawPath(path, pStroke); pStroke.pathEffect = null
    }
    private fun rr(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, fill: Int) { rect.set(x, y, x + w, y + h); pFill.color = fill; pFill.shader = null; c.drawRoundRect(rect, r, r, pFill) }
    /** 현대 엠블럼 꼴(기울어진 H 를 감싼 타원, view.js drawEmblem): 내 차 보닛·트렁크에만 */
    private fun emblem(c: Canvas, x: Float, y: Float, w: Float, angDeg: Float, col: Int) {
        val h = w * 0.5f; val t = w * 0.09f; c.save(); c.translate(x, y); c.rotate(angDeg)
        pStroke.color = col; pStroke.strokeWidth = t; pStroke.pathEffect = null; rect.set(-(w / 2 - t / 2), -(h / 2 - t / 2), w / 2 - t / 2, h / 2 - t / 2); c.drawOval(rect, pStroke)
        val sk = w * 0.08f; val x0 = -w * 0.18f; val x1 = w * 0.18f; val y0 = -h * 0.26f; val y1 = h * 0.26f
        path.reset(); path.moveTo(x0 + sk, y0); path.lineTo(x0 - sk, y1); path.moveTo(x1 + sk, y0); path.lineTo(x1 - sk, y1); path.moveTo(x0, 0f); path.lineTo(x1, 0f); c.drawPath(path, pStroke); c.restore()
    }
    private fun trap(c: Canvas, x0: Float, w0: Float, x1: Float, w1: Float, fill: Int) { path.reset(); path.moveTo(x0, -w0 / 2); path.lineTo(x1, -w1 / 2); path.lineTo(x1, w1 / 2); path.lineTo(x0, w0 / 2); path.close(); pFill.color = fill; c.drawPath(path, pFill) }

    /** 탑뷰 차(현대차 꼴): 세단은 보닛이 길고 캐빈이 뒤로, SUV 는 캐빈이 길고 루프랙. 앞 가로 한 줄 램프(아반떼는 두 토막)·뒤 빨간 띠·미러. */
    fun drawCarTop(c: Canvas, m: Map, b: OBB, hex: Int, mine: Boolean, blink: Boolean, model: String) {
        val suv = CarSpec.byKey(model).shape == "suv"
        c.save(); c.translate(m.X(b.c.x), m.Y(b.c.y)); c.rotate(-Math.toDegrees(b.heading).toFloat())
        val L = (b.hx * 2 * m.scale).toFloat(); val Wd = (b.hy * 2 * m.scale).toFloat()
        rr(c, -L / 2 + 3, -Wd / 2 + 4, L, Wd, Wd * 0.18f, Scene.rgba(0, 0, 0, 0.4))
        for ((fx, fy) in listOf(0.3f to 0.5f, 0.3f to -0.5f, -0.3f to 0.5f, -0.3f to -0.5f)) rr(c, fx * L - Wd * 0.17f, fy * Wd - Wd * 0.07f, Wd * 0.34f, Wd * 0.14f, 2f, Color.rgb(16, 17, 20))
        rect.set(-L / 2, -Wd / 2, L / 2, Wd / 2); pFill.shader = LinearGradient(0f, -Wd / 2, 0f, Wd / 2, intArrayOf(Scene.shade(hex, 0.8), Scene.shade(hex, 1.08), Scene.shade(hex, 0.75)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        pFill.color = Color.WHITE; c.drawRoundRect(rect, Wd * (if (suv) 0.16f else 0.22f), Wd * (if (suv) 0.16f else 0.22f), pFill); pFill.shader = null
        val cab = if (suv) floatArrayOf(0.22f, 0.05f, -0.4f, -0.49f, 0.84f, 0.74f) else floatArrayOf(0.26f, 0.06f, -0.26f, -0.38f, 0.8f, 0.68f)   // f0 f1 r1 r0 w0 w1
        trap(c, cab[0] * L, Wd * cab[4], cab[1] * L, Wd * cab[5], Color.rgb(26, 40, 56))
        rr(c, cab[2] * L, -Wd * cab[5] / 2, (cab[1] - cab[2]) * L, Wd * cab[5], Wd * 0.06f, Scene.shade(hex, 1.15))
        trap(c, cab[2] * L, Wd * cab[5], cab[3] * L, Wd * cab[4], Color.rgb(26, 40, 56))
        if (suv) for (sy in floatArrayOf(-1f, 1f)) rr(c, cab[2] * L, sy * Wd * 0.33f - 1, (cab[1] - cab[2]) * L, 2f, 1f, Color.rgb(42, 44, 49))
        rr(c, cab[0] * L - L * 0.03f, -Wd * 0.62f, L * 0.05f, Wd * 0.12f, 2f, Scene.shade(hex, 0.9)); rr(c, cab[0] * L - L * 0.03f, Wd * 0.5f, L * 0.05f, Wd * 0.12f, 2f, Scene.shade(hex, 0.9))
        val segs = if (model == "AVANTE") listOf(-0.46f to -0.14f, 0.14f to 0.46f) else listOf(-0.46f to 0.46f)
        for ((s0, s1) in segs) rr(c, L / 2 - L * 0.035f, s0 * Wd, L * 0.03f, (s1 - s0) * Wd, 1.5f, Color.rgb(244, 246, 255))
        rr(c, -L / 2 + L * 0.005f, -Wd * 0.46f, L * 0.025f, Wd * 0.92f, 1.5f, Color.rgb(224, 52, 44))
        if (blink) for (sy in floatArrayOf(-1f, 1f)) { rr(c, L * 0.4f, sy * Wd * 0.44f - Wd * 0.06f, L * 0.08f, Wd * 0.12f, 2f, Color.rgb(255, 179, 64)); rr(c, -L * 0.47f, sy * Wd * 0.44f - Wd * 0.06f, L * 0.08f, Wd * 0.12f, 2f, Color.rgb(255, 179, 64)) }
        if (mine) { pStroke.color = Scene.rgba(255, 255, 255, 0.7); pStroke.strokeWidth = 1.5f; c.drawRoundRect(rect, Wd * 0.22f, Wd * 0.22f, pStroke)
            if (Wd > 18) { emblem(c, L * 0.42f, 0f, Wd * 0.22f, 90f, Color.rgb(232, 234, 239)); emblem(c, -L * 0.42f, 0f, Wd * 0.2f, 90f, Color.rgb(232, 234, 239)) } }   // 보닛·트렁크 엠블럼(내 차만, PM 2026-10-27)
        c.restore()
    }

    fun drawTopView(c: Canvas, W: Float, H: Float, model: CarModel, snap: GameSnapshot, sc: Scenario, steer: Double, guide: Boolean, blink: Boolean, pdw: Boolean, light: Boolean = false, stopMark: Vec? = null, ghost: Boolean = true): Map {
        val spec = model.spec
        val scale = min(W / (sc.bounds.hx * 2).toFloat(), H / (sc.bounds.hy * 2).toFloat()) * 0.96f
        val m = Map(W / 2 - (sc.bounds.c.x * scale).toFloat(), H / 2 + (sc.bounds.c.y * scale).toFloat(), scale)
        val env = sc.env; val under = env == "underground"
        // 라이트(플레오스 심플) = 밝은 회색 아스팔트 + 진한 회색 주차선 / 다크 = 원래 어두운 바닥 (view.js PAL)
        pFill.shader = null; pFill.color = if (light) (if (under) Color.rgb(217, 219, 224) else if (env == "night") Color.rgb(185, 188, 196) else if (env == "rain") Color.rgb(200, 203, 209) else Color.rgb(226, 228, 232))
            else (if (under) Color.rgb(46, 47, 52) else if (env == "night") Color.rgb(28, 30, 36) else if (env == "rain") Color.rgb(43, 46, 54) else Color.rgb(60, 62, 68)); c.drawRect(0f, 0f, W, H, pFill)
        Scene.noiseOver(c, W, H, if (light) 0.05 else 0.12)
        val markCol = if (light) Scene.rgba(60, 64, 72, 0.45) else Scene.rgba(240, 240, 235, 0.5); val slotCol = if (light) Scene.rgba(40, 44, 52, 0.9) else Scene.rgba(250, 250, 245, 0.95)
        sc.marks.forEach { poly(c, m, it.corners(), null, markCol, max(1.5f, 0.12f * scale)) }
        poly(c, m, sc.slot.corners(), if (snap.phase == Phase.PARKED) Scene.rgba(58, 209, 124, 0.3) else Scene.rgba(58, 209, 124, if (light) 0.12 else 0.08), slotCol, max(2f, 0.12f * scale))
        run { val u = Vec(cos(sc.slot.heading), sin(sc.slot.heading)); val cc = Vec(sc.slot.c.x + u.x * sc.slot.hx * 0.4, sc.slot.c.y + u.y * sc.slot.hx * 0.4)
            pText.textSize = max(12f, 0.6f * scale); pText.color = if (light) Color.rgb(31, 157, 85) else Scene.rgba(58, 209, 124, 0.8); pText.isFakeBoldText = true; c.drawText("P", m.X(cc.x), m.Y(cc.y) + pText.textSize * 0.35f, pText) }
        for (o in sc.obstacles) {
            if (o.isCar) { drawCarTop(c, m, o.box, o.color, false, false, o.model); continue }
            if (o.label == "기둥") { poly(c, m, o.box.corners(), Color.rgb(142, 139, 132), Color.rgb(240, 192, 32), max(2f, 0.08f * scale)); continue }
            poly(c, m, o.box.corners(), if (o.label == "연석") Color.rgb(139, 141, 147) else Color.rgb(111, 110, 106), null)
            if (o.label != "연석") { val cs = o.box.corners(); line(c, m, listOf(cs[0], cs[1]), Scene.rgba(232, 185, 42, 0.9), 2f) }
        }
        if (guide) { fun pr(st: Double, range: Double, n: Int, col: Int, lw: Float, dash: FloatArray? = null) { val (L, R) = model.predictedPath(snap.pose, st, snap.reverse, range, n); line(c, m, L, col, lw, dash); line(c, m, R, col, lw, dash) }
            pr(0.0, 3.0, 2, Scene.rgba(80, 170, 255, 0.9), 2f, floatArrayOf(8f, 6f)); pr(steer, 4.0, 40, Color.rgb(255, 210, 40), 3.5f); pr(steer, 0.5, 6, Color.rgb(255, 77, 77), 4f) }
        // 목표 자세 유령(PM 2026-10-27 "전진 주차인지 후진 주차인지 딱 안 보인다", view.js 와 같은 그림): 점선 차 + 앞코 + 후진/전진 글자. 들어가면 지운다
        if (ghost && snap.phase != Phase.PARKED) { val gh = if (sc.kind == app.parkingcoach.sim.Kind.PERP_FWD) sc.slot.heading + Math.PI else sc.slot.heading; val col = if (light) Scene.rgba(40, 44, 52, 0.6) else Scene.rgba(255, 255, 255, 0.6)
            c.save(); c.translate(m.X(sc.slot.c.x), m.Y(sc.slot.c.y)); c.rotate(-Math.toDegrees(gh).toFloat()); val L = (spec.length * scale).toFloat(); val Wd = (spec.width * scale).toFloat()
            rect.set(-L / 2, -Wd / 2, L / 2, Wd / 2); pStroke.color = col; pStroke.strokeWidth = max(1.5f, 0.06f * scale); pStroke.pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 5f), 0f); c.drawRoundRect(rect, Wd * 0.22f, Wd * 0.22f, pStroke); pStroke.pathEffect = null
            path.reset(); path.moveTo(L / 2 - Wd * 0.05f, 0f); path.lineTo(L / 2 - Wd * 0.45f, -Wd * 0.3f); path.lineTo(L / 2 - Wd * 0.45f, Wd * 0.3f); path.close(); pFill.shader = null; pFill.color = col; c.drawPath(path, pFill); c.restore()
            pText.textSize = max(11f, 0.5f * scale); pText.color = col; pText.isFakeBoldText = true; c.drawText(if (sc.forward) "전진" else "후진", m.X(sc.slot.c.x), m.Y(sc.slot.c.y) + pText.textSize * 0.35f, pText) }
        drawCarTop(c, m, snap.body, Color.rgb(47, 109, 246), true, blink, spec.key)
        val wr = model.wheelAngleRad(steer); val p = snap.pose
        for (sy in doubleArrayOf(spec.width / 2 - 0.12, -(spec.width / 2 - 0.12))) { val cx = p.x + spec.wheelbase * cos(p.heading) - sy * sin(p.heading); val cy = p.y + spec.wheelbase * sin(p.heading) + sy * cos(p.heading)
            poly(c, m, OBB(Vec(cx, cy), p.heading + wr, 0.32, 0.11).corners(), Color.rgb(12, 13, 16), null) }
        if (pdw && snap.phase != Phase.READY) { val b = snap.body
            for (sn in Scene.sensors(snap, sc)) { val lv = Scene.pdwLevel(sn.d); val ang = Scene.angleOf(b.c, sn.p); val base = hypot(sn.p.x - b.c.x, sn.p.y - b.c.y)
                for (k in 1..3) { val r = ((base + 0.12 + k * 0.2) * scale).toFloat(); rect.set(m.X(b.c.x) - r, m.Y(b.c.y) - r, m.X(b.c.x) + r, m.Y(b.c.y) + r)
                    pStroke.color = if (k <= lv) Scene.PDW_COL[lv] else if (light) Scene.rgba(0, 0, 0, 0.07) else Scene.PDW_COL[0]; pStroke.strokeWidth = max(2f, 0.1f * scale)
                    // 캔버스 각은 시계 방향(y 아래) 이라 -(ang) 부터 0.28 rad
                    c.drawArc(rect, Math.toDegrees(-(ang + 0.14)).toFloat(), Math.toDegrees(0.28).toFloat(), false, pStroke) } } }
        if (stopMark != null) { val x = m.X(stopMark.x); pStroke.color = Color.rgb(0, 170, 210); pStroke.strokeWidth = max(2f, 0.06f * scale); pStroke.pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 5f), 0f)
            c.drawLine(x, m.Y(stopMark.y + 2.4), x, m.Y(stopMark.y - 2.4), pStroke); pStroke.pathEffect = null
            pText.textSize = max(11f, 0.45f * scale); pText.color = Color.rgb(0, 170, 210); pText.isFakeBoldText = true; c.drawText("여기서 멈춤", x, m.Y(stopMark.y + 2.4) - 6, pText) }   // 연습 모드: 핸들을 돌릴 자리
        snap.hazard?.let { hz -> val x = m.X(hz.pos.x); val y = m.Y(hz.pos.y); val rr = max(8f, ((hz.type.r + 0.12) * scale).toFloat())
            pFill.color = Scene.rgba(255, 70, 70, 0.28); c.drawCircle(x, y, rr * 1.6f, pFill)                                                 // 경고 테
            pStroke.color = Color.rgb(255, 70, 70); pStroke.strokeWidth = 2f; pStroke.pathEffect = null
            if (hz.type == app.parkingcoach.sim.HazType.CART) { rect.set(x - rr * 0.9f, y - rr * 0.6f, x + rr * 0.9f, y + rr * 0.6f); pFill.color = Color.rgb(215, 218, 224); c.drawRect(rect, pFill); c.drawRect(rect, pStroke) }
            else { rect.set(x - rr * 1.1f, y - rr * 0.7f, x + rr * 1.1f, y + rr * 0.7f); pFill.color = if (hz.type == app.parkingcoach.sim.HazType.CHILD) Color.rgb(242, 201, 76) else Color.rgb(59, 111, 209); c.drawOval(rect, pFill)   // 어깨
                pFill.color = Color.rgb(233, 181, 143); c.drawCircle(x, y, rr * 0.55f, pFill); c.drawOval(rect, pStroke) } }                   // 머리(위에서 본 사람)
        Scene.vignette(c, W, H, min(W, H) * 0.4f, max(W, H) * 0.75f, if (light) 0.12 else 0.45)
        return m
    }
}

class TopView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var engine: GameEngine? = null
    var snap: GameSnapshot? = null
    var steer = 0.0
    var guide = true
    var blink = false
    var pdw = true
    /** 연습 모드: 핸들을 돌릴 자리(공식 자리) 점선 */
    var stopMark: Vec? = null
    /** 칸 안 목표 자세(점선 차 + 후진/전진). 미리보기·리플레이는 끈다 */
    var ghost = true
    override fun onDraw(c: Canvas) {
        val e = engine ?: return; val s = snap ?: return
        val W = width.toFloat(); val H = height.toFloat(); if (W <= 0 || H <= 0) return
        TopDraw.drawTopView(c, W, H, e.model, s, e.scenario, steer, guide, blink, pdw, light = !Scene.night(context), stopMark = stopMark, ghost = ghost)
    }
}
