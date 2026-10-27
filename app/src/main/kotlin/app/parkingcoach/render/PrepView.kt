package app.parkingcoach.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 타기 전 준비(PM 2026-10-27, 시안 index.html prepGeom/drawPrep 과 같은 수식·그림): 옆에서 본 운전석 한 장 — 의자를 좌우로, 핸들을 위아래·앞뒤로 끌어 맞춘다.
 * 사람은 머리 높이 1u = 34px 비율의 픽토그램(몸통 2.3u · 허벅지 1.9u · 정강이 2u · 위팔 1.3u · 아래팔 1.2u), 무릎·팔꿈치는 두 마디 IK(무릎 위로 · 팔꿈치 아래로),
 * 안 닿으면 발·손이 페달·핸들 앞에서 멈춘다(늘어나지 않는다). 판정 = 무릎 굽힘 18~38° · 팔 거리(손목이 림 위) · 계기판이 림에 안 가림.
 * 룸미러 탭 = 앞유리 위 거울 끌기(뒷유리가 한가운데). 기준 좌표 640×360, 뷰 폭에 맞춰 늘린다.
 */
class PrepView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var seat = 0.45; var wx = 0.8; var wy = 0.2; var mx = 22.0; var my = -14.0
    /** "seat"(의자·핸들) | "room"(룸미러) */
    var tab = "seat"; set(v) { field = v; invalidate() }
    var onChange: (() -> Unit)? = null

    class Geom(val u: Double, val sx: Double, val H: DoubleArray, val F: DoubleArray, val FT: DoubleArray, val K: DoubleArray, val bend: Double, val reach: Boolean,
               val S0: DoubleArray, val HD: DoubleArray, val E: DoubleArray, val W: DoubleArray, val R: Double, val T: DoubleArray, val HT: DoubleArray, val da: Double, val EL: DoubleArray,
               val CL: DoubleArray, val lean: Double, val okKnee: Boolean, val okReach: Boolean, val okView: Boolean)
    /** 옆모습 기하(시안 prepGeom 그대로). 페달 (190,288) · 바닥 300 · 의자 x 250~360 */
    fun geom(): Geom {
        val u = 34.0; val sx = 250 + seat * 110; val H = doubleArrayOf(sx + 4, 232.0); val F = doubleArrayOf(190.0, 288.0); val th = 1.9 * u; val sh = 2.0 * u
        val d = hypot(F[0] - H[0], F[1] - H[1]); val reach = d <= th + sh - 2; val dd = min(d, th + sh - 2)
        val a = acos(((th * th + dd * dd - sh * sh) / (2 * th * dd)).coerceIn(-1.0, 1.0)); val base = atan2(F[1] - H[1], F[0] - H[0])
        val K = doubleArrayOf(H[0] + th * cos(base + a), H[1] + th * sin(base + a))                                                            // 무릎은 위로
        val bend = if (reach) 180 - Math.toDegrees(acos(((th * th + sh * sh - dd * dd) / (2 * th * sh)).coerceIn(-1.0, 1.0))) else 0.0
        val FT = if (reach) F else doubleArrayOf(H[0] + (F[0] - H[0]) / d * (th + sh), H[1] + (F[1] - H[1]) / d * (th + sh))                 // 안 닿으면 발이 페달 앞에서 멈춘다
        val lean = 0.26; val S0 = doubleArrayOf(H[0] + 2.3 * u * sin(lean), H[1] - 2.3 * u * cos(lean)); val HD = doubleArrayOf(S0[0] + 1.3 * u * sin(lean) - 4, S0[1] - 1.3 * u * cos(lean)); val E = doubleArrayOf(HD[0] - 7, HD[1] - 2)
        val W = doubleArrayOf(205 + wx * 50, 150 + wy * 50); val R = 27.0; val T = doubleArrayOf(W[0] + 3, W[1] - R); val da = hypot(T[0] - S0[0], T[1] - S0[1])
        val ua = 1.3 * u; val fa = 1.2 * u; val dA = min(da, ua + fa - 2); val ang = acos(((ua * ua + dA * dA - fa * fa) / (2 * ua * dA)).coerceIn(-1.0, 1.0)); val bA = atan2(T[1] - S0[1], T[0] - S0[0])
        val EL = doubleArrayOf(S0[0] + ua * cos(bA - ang), S0[1] + ua * sin(bA - ang))                                                          // 팔꿈치는 아래로
        val HT = if (da <= ua + fa - 2) T else doubleArrayOf(S0[0] + (T[0] - S0[0]) / da * (ua + fa), S0[1] + (T[1] - S0[1]) / da * (ua + fa))   // 안 닿으면 손이 핸들 앞에서 멈춘다
        val CL = doubleArrayOf(128.0, 196.0, 126.0, 152.0); val cx = (CL[0] + CL[1]) / 2; val yl = E[1] + (CL[2] - E[1]) * (W[0] - E[0]) / (cx - E[0])
        val okKnee = reach && bend >= 18 && bend <= 38; val okReach = da >= ua + fa - 10 && da <= ua + fa - 1; val okView = W[1] - R > yl + 3 && W[1] + R < K[1] - 6
        return Geom(u, sx, H, F, FT, K, bend, reach, S0, HD, E, W, R, T, HT, da, EL, CL, lean, okKnee, okReach, okView)
    }
    val roomOk get() = Math.abs(mx) < 9 && Math.abs(my) < 7

    // ---- 터치: 룸미러 탭은 거울, 의자 탭은 핸들 근처(60px)면 핸들 아니면 의자 ----
    private var drag: DoubleArray? = null; private var dragKind = ""
    private fun k() = width / 640.0
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x / k(); val y = e.y / k()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { parent?.requestDisallowInterceptTouchEvent(true)
                if (tab == "room") { dragKind = "mir"; drag = doubleArrayOf(x, y, mx, my) }
                else { val G = geom(); if (hypot(x - G.W[0], y - G.W[1]) < 60) { dragKind = "wheel"; drag = doubleArrayOf(x, y, wx, wy) } else { dragKind = "seat"; drag = doubleArrayOf(x, y, seat, 0.0) } }
                return true }
            MotionEvent.ACTION_MOVE -> { val d = drag ?: return false
                when (dragKind) { "mir" -> { mx = (d[2] + (x - d[0]) * 0.8).coerceIn(-40.0, 40.0); my = (d[3] + (y - d[1]) * 0.8).coerceIn(-30.0, 30.0) }
                    "wheel" -> { wx = (d[2] + (x - d[0]) / 70).coerceIn(0.0, 1.0); wy = (d[3] + (y - d[1]) / 70).coerceIn(0.0, 1.0) }
                    else -> seat = (d[2] + (x - d[0]) / 120).coerceIn(0.0, 1.0) }
                invalidate(); onChange?.invoke(); return true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { drag = null; performClick(); return true }
        }
        return false
    }
    override fun performClick(): Boolean { super.performClick(); return true }

    // ---- 그리기 ----
    private val pF = Paint(Paint.ANTI_ALIAS_FLAG); private val pS = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val pT = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private val path = Path(); private val rect = RectF()
    private fun rr(c: Canvas, x: Double, y: Double, w: Double, h: Double, r: Double, col: Int) { rect.set(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat()); pF.color = col; c.drawRoundRect(rect, r.toFloat(), r.toFloat(), pF) }
    private fun stroke(c: Canvas, pts: List<DoubleArray>, w: Double, col: Int) { pS.color = col; pS.strokeWidth = w.toFloat(); pS.pathEffect = null; path.reset(); pts.forEachIndexed { i, q -> if (i == 0) path.moveTo(q[0].toFloat(), q[1].toFloat()) else path.lineTo(q[0].toFloat(), q[1].toFloat()) }; c.drawPath(path, pS) }
    private fun circle(c: Canvas, x: Double, y: Double, r: Double, col: Int) { pF.color = col; c.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), pF) }

    override fun onDraw(c: Canvas) {
        val Wc = width.toFloat(); if (Wc <= 0) return; val kk = Wc / 640f; c.save(); c.scale(kk, kk)
        val dark = Scene.night(context); val ink = if (dark) Color.rgb(238, 240, 244) else Color.rgb(17, 18, 22); val dim = if (dark) Color.rgb(107, 114, 128) else Color.rgb(154, 160, 170)
        val card = if (dark) Color.rgb(26, 29, 36) else Color.rgb(236, 238, 242); val seatC = if (dark) Color.rgb(42, 45, 53) else Color.rgb(35, 37, 43); val seat2 = if (dark) Color.rgb(53, 57, 69) else Color.rgb(51, 54, 62)
        val dash = if (dark) Color.rgb(48, 52, 61) else Color.rgb(74, 79, 91)
        pF.color = card; c.drawRect(0f, 0f, 640f, 360f, pF)
        if (tab == "room") { drawRoom(c, dark, ink, dim); c.restore(); return }
        val G = geom(); val u = G.u
        // 실내: 바닥 · 앞유리 · 대시보드 · 계기판 · 페달
        pF.color = if (dark) Color.rgb(38, 41, 50) else Color.rgb(217, 219, 224); c.drawRect(0f, 300f, 640f, 360f, pF)
        stroke(c, listOf(doubleArrayOf(70.0, 30.0), doubleArrayOf(126.0, 112.0)), 3.0, if (dark) Color.rgb(58, 63, 75) else Color.rgb(195, 199, 207))
        path.reset(); path.moveTo(70f, 112f); path.quadTo(170f, 100f, 232f, 122f); path.lineTo(232f, 156f); path.quadTo(180f, 150f, 132f, 182f); path.quadTo(96f, 210f, 96f, 300f); path.lineTo(70f, 300f); path.close(); pF.color = dash; c.drawPath(path, pF)
        rr(c, G.CL[0], G.CL[2], G.CL[1] - G.CL[0], G.CL[3] - G.CL[2], 6.0, Color.rgb(10, 11, 13))
        run { val cy = (G.CL[3] - 4).toFloat(); val r = 9f; pS.strokeWidth = 2f; pS.pathEffect = null                                   // 계기판 = 눈금 두 개 + 바늘(글자 없음)
            for (cx in floatArrayOf((G.CL[0] + 18).toFloat(), (G.CL[1] - 18).toFloat())) { pS.color = Color.rgb(201, 204, 211); rect.set(cx - r, cy - r, cx + r, cy + r); c.drawArc(rect, 198f, 144f, false, pS)
                pS.color = Color.rgb(232, 81, 63); c.drawLine(cx, cy, cx + r * cos(PI * 1.35).toFloat(), cy + r * sin(PI * 1.35).toFloat(), pS) } }
        stroke(c, listOf(doubleArrayOf(150.0, 298.0), doubleArrayOf(182.0, 290.0)), 8.0, seat2); stroke(c, listOf(doubleArrayOf(192.0, 300.0), doubleArrayOf(196.0, 280.0)), 6.0, seat2)
        // 의자: 등받이(기울임) · 머리받침 · 방석 · 받침 + 레일
        val bx0 = G.H[0] + 30; val by0 = 250.0; val bx1 = bx0 + 2.9 * u * sin(G.lean); val by1 = by0 - 2.9 * u * cos(G.lean)
        stroke(c, listOf(doubleArrayOf(bx0, by0), doubleArrayOf(bx1, by1)), 30.0, seatC); stroke(c, listOf(doubleArrayOf(bx1 + 6, by1 - 10), doubleArrayOf(bx1 + 12, by1 - 34)), 24.0, seatC)
        rr(c, G.sx - 62, 226.0, 112.0, 28.0, 12.0, seatC); rr(c, G.sx - 48, 250.0, 86.0, 34.0, 6.0, seat2); rr(c, G.sx - 70, 284.0, 130.0, 8.0, 0.0, seatC); rr(c, G.sx - 40, 292.0, 10.0, 8.0, 0.0, seatC); rr(c, G.sx + 20, 292.0, 10.0, 8.0, 0.0, seatC)
        pT.textSize = 11f; pT.color = dim; c.drawText("◀ 의자 ▶", (G.sx - 6).toFloat(), 320f, pT)
        // 핸들: 기둥(대시보드 몸통 안에서 나와 핸들까지) + 옆에서 본 림
        run { val A = doubleArrayOf(116.0, 192.0); val mxx = A[0] + (G.W[0] - A[0]) * 0.42; val myy = A[1] + (G.W[1] - A[1]) * 0.42
            stroke(c, listOf(A, doubleArrayOf(mxx, myy)), 22.0, dash); stroke(c, listOf(doubleArrayOf(mxx, myy), doubleArrayOf(G.W[0] - 3, G.W[1] + 3)), 10.0, seat2) }
        c.save(); c.translate(G.W[0].toFloat(), G.W[1].toFloat()); c.rotate(-0.24f * 180f / PI.toFloat())
        rect.set(-8f, -G.R.toFloat(), 8f, G.R.toFloat()); pS.color = ink; pS.strokeWidth = 9f; c.drawOval(rect, pS); rect.set(-4f, -9f, 4f, 9f); pF.color = ink; c.drawOval(rect, pF); c.restore()
        // 사람(픽토그램): 다리 → 신발 → 목 → 몸통 → 벨트 → 팔·손 → 머리
        val skin = Color.rgb(232, 180, 140); val skin2 = Color.rgb(212, 154, 112); val pants = Color.rgb(168, 153, 124); val shirt = Color.rgb(59, 111, 209); val hair = Color.rgb(43, 33, 28)
        val ankle = doubleArrayOf(G.FT[0] + (G.K[0] - G.FT[0]) * 0.08, G.FT[1] + (G.K[1] - G.FT[1]) * 0.08)
        stroke(c, listOf(G.H, G.K, ankle), 0.44 * u, pants)
        run { val fa = atan2(G.FT[1] - G.K[1], G.FT[0] - G.K[0]); val fd = if (G.reach) atan2(8.0, -32.0) else max(1.8, fa - 0.3); val O = if (G.reach) G.FT else ankle; val y0 = if (G.reach) 4.0 else -0.26 * u
            c.save(); c.translate(O[0].toFloat(), O[1].toFloat()); c.rotate(Math.toDegrees(fd).toFloat()); rr(c, -0.3 * u, y0, 1.05 * u, 0.36 * u, 0.18 * u, Color.rgb(217, 75, 63)); c.restore() }   // 신발: 가로로 긴 빨간 캡슐, 밑창이 페달 윗면
        stroke(c, listOf(doubleArrayOf(G.S0[0] + 4, G.S0[1] - 8), doubleArrayOf(G.HD[0] + 1, G.HD[1] + 10)), 0.3 * u, skin2)                 // 목(상의보다 먼저)
        stroke(c, listOf(doubleArrayOf(G.H[0] + 10, G.H[1] - 4), G.S0), 0.96 * u, shirt)                                                    // 몸통
        stroke(c, listOf(doubleArrayOf(G.S0[0] + 6, G.S0[1] - 14), doubleArrayOf(G.H[0] - 12, G.H[1] - 6)), 3.0, Scene.rgba(255, 255, 255, 0.55))   // 안전벨트
        stroke(c, listOf(G.S0, G.EL, G.HT), 0.32 * u, shirt)
        c.save(); c.translate((G.HT[0] - 4).toFloat(), (G.HT[1] - 1).toFloat()); c.rotate(-0.5f * 180f / PI.toFloat()); rect.set((-0.26 * u).toFloat(), (-0.2 * u).toFloat(), (0.26 * u).toFloat(), (0.2 * u).toFloat()); pF.color = skin; c.drawOval(rect, pF); c.restore()
        circle(c, G.HT[0] - 11, G.HT[1] + 4, 0.07 * u, skin2)                                                                                 // 손
        val hx = G.HD[0]; val hy = G.HD[1]; val r = 0.5 * u
        circle(c, hx, hy, r, skin)                                                                                                           // 머리
        path.reset(); rect.set((hx + 2 - r * 1.04).toFloat(), (hy - 2 - r * 1.04).toFloat(), (hx + 2 + r * 1.04).toFloat(), (hy - 2 + r * 1.04).toFloat()); path.addArc(rect, 183.6f, 187.2f)
        path.quadTo((hx + r * 1.1).toFloat(), (hy + r * 0.3).toFloat(), (hx + r * 0.8).toFloat(), (hy + r * 0.5).toFloat()); path.quadTo((hx + r * 0.85).toFloat(), (hy - r * 0.05).toFloat(), (hx + r * 0.45).toFloat(), (hy - r * 0.5).toFloat())
        path.quadTo(hx.toFloat(), (hy - r * 0.62).toFloat(), (hx - r * 0.86).toFloat(), (hy - r * 0.46).toFloat()); path.close(); pF.color = hair; c.drawPath(path, pF)   // 머리카락: 정수리 + 뒷머리(앞머리 없음)
        circle(c, hx + r * 0.64, hy + r * 0.1, r * 0.17, skin2); circle(c, hx - r * 0.46, hy - r * 0.1, r * 0.09, Color.rgb(26, 28, 34))      // 귀 · 눈
        pS.color = Color.rgb(181, 104, 78); pS.strokeWidth = 2f; rect.set((hx - r * 0.5 - r * 0.22).toFloat(), (hy + r * 0.42 - r * 0.22).toFloat(), (hx - r * 0.5 + r * 0.22).toFloat(), (hy + r * 0.42 + r * 0.22).toFloat()); c.drawArc(rect, 27f, 108f, false, pS)   // 입
        // 판정 고리(무릎 · 손)
        fun ring(pt: DoubleArray, rr: Double, ok: Boolean) { pS.color = if (ok) Color.rgb(31, 157, 85) else Color.rgb(229, 72, 77); pS.strokeWidth = 3f; pS.pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f); c.drawCircle(pt[0].toFloat(), pt[1].toFloat(), rr.toFloat(), pS); pS.pathEffect = null }
        if (G.reach) ring(G.K, 0.42 * u, G.okKnee); ring(doubleArrayOf(G.HT[0] - 4, G.HT[1] - 1), 0.4 * u, G.okReach)
        c.restore()
    }
    /** 룸미러: 앞유리 위쪽 + 거울, 거울 속 뒷유리는 거울 반대로 밀린다 */
    private fun drawRoom(c: Canvas, dark: Boolean, ink: Int, dim: Int) {
        pF.color = if (dark) Color.rgb(38, 41, 50) else Color.rgb(217, 219, 224); c.drawRect(0f, 0f, 640f, 46f, pF)
        pT.textSize = 13f; pT.color = dim; c.drawText("앞유리 위 · 룸미러를 끌어 보세요", 320f, 30f, pT)
        val mxx = (320 + mx * 0.4).toFloat(); val myy = (140 + my * 0.4).toFloat()
        pS.color = ink; pS.strokeWidth = 6f; pS.pathEffect = null; c.drawLine(mxx, 46f, mxx, myy - 36, pS)
        rect.set(mxx - 110, myy - 36, mxx + 110, myy + 36); pF.color = Color.rgb(27, 29, 34); c.drawRoundRect(rect, 14f, 14f, pF); pS.strokeWidth = 4f; pS.color = if (dark) Color.rgb(58, 63, 75) else Color.rgb(42, 45, 53); c.drawRoundRect(rect, 14f, 14f, pS)
        c.save(); path.reset(); rect.set(mxx - 102, myy - 28, mxx + 102, myy + 28); path.addRoundRect(rect, 10f, 10f, Path.Direction.CW); c.clipPath(path); pF.color = Color.rgb(43, 49, 64); c.drawRect(rect, pF)
        val ox = (-mx * 1.6).toFloat(); val oy = (-my * 1.4).toFloat()
        path.reset(); path.moveTo(mxx - 70 + ox, myy - 22 + oy); path.lineTo(mxx + 70 + ox, myy - 22 + oy); path.lineTo(mxx + 86 + ox, myy + 16 + oy); path.lineTo(mxx - 86 + ox, myy + 16 + oy); path.close(); pF.color = Color.rgb(143, 163, 200); c.drawPath(path, pF)
        pF.color = Color.rgb(26, 40, 56); c.drawRect(mxx - 102, myy + 16 + oy, mxx + 102, myy + 76 + oy, pF); pF.color = Scene.rgba(255, 255, 255, 0.08); c.drawRect(mxx - 102, myy - 28, mxx + 102, myy - 14, pF); c.restore()
    }
}
