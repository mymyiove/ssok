package app.parkingcoach.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import app.parkingcoach.game.GameEngine
import app.parkingcoach.game.GameSnapshot
import app.parkingcoach.sim.Vec
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 사이드미러(view.js drawMirror). side = -1 왼쪽 · +1 오른쪽. A필러 옆에서 뒤를 보며 바깥쪽 18°, 볼록 거울, 거울상, R 이면 15° 하향(R 연동 미러).
 * 플레오스 심플 꼴(시안 .view.mir, PM 2026-10-26 '미러·핸들·계기판 상하 높이 맞게'): 거울 유리가 뷰를 꽉 채운다(둥근 카드 18dp) — 위는 클러스터 패널, 아래는 페달과 한 선. 라벨은 레이아웃의 알약(카메라와 같은 꼴). 후측방 경고(BCW): 그쪽 옆·뒤에 차·사람이 있으면 주황 아이콘 점멸.
 * pitchDeg/yawDeg 를 주면 그 각으로(거울 맞추기 연습).
 */
class MirrorView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var engine: GameEngine? = null
    var snap: GameSnapshot? = null
    var side = 1
    var blink = false
    var pitchDeg: Double? = null
    var yawDeg: Double? = null
    var noBcw = false
    var housing = true          // false 면 뷰 전체가 유리(거울 맞추기 카드)
    var label = ""          // (레이아웃 알약이 그린다 — 호환용)
    /** 그 자리에서 끌어 각도 조정(PM 2026-10-27): 가로 = 바깥·안쪽, 세로 = 위·아래. 두 번 누르면 원위치(pitch·yaw null = 기본 + R 연동 하향) */
    var adjustable = false
    var onAdjust: (() -> Unit)? = null
    private var drag: FloatArray? = null; private var lastTap = 0L
    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        if (!adjustable) return false
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { val now = System.currentTimeMillis(); if (now - lastTap < 300) { pitchDeg = null; yawDeg = null; drag = null; lastTap = 0; onAdjust?.invoke(); return true }; lastTap = now
                drag = floatArrayOf(e.x, e.y, (pitchDeg ?: 8.0).toFloat(), (yawDeg ?: 18.0).toFloat()); parent?.requestDisallowInterceptTouchEvent(true); return true }
            android.view.MotionEvent.ACTION_MOVE -> { val dd = drag ?: return false; yawDeg = (dd[3] + (e.x - dd[0]) * 0.12 * side).toDouble().coerceIn(0.0, 45.0); pitchDeg = (dd[2] + (e.y - dd[1]) * 0.12).toDouble().coerceIn(-5.0, 30.0); return true }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> { if (drag == null) return false; drag = null; performClick(); return true }
        }
        return false
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    private val pCard = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ctx.getColor(app.parkingcoach.R.color.card) }
    private val pCardLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ctx.getColor(app.parkingcoach.R.color.line); style = Paint.Style.STROKE; strokeWidth = 2f }
    private val pIcon = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pGlass = Paint()
    private val pRim = Paint().apply { color = Scene.rgba(0, 0, 0, 0.55); style = Paint.Style.STROKE }
    private val clip = Path()
    val glass = RectF()

    override fun onDraw(c: Canvas) {
        val W = width.toFloat(); val H = height.toFloat(); if (W <= 0 || H <= 0) return
        if (housing) {
            val d = resources.displayMetrics.density; glass.set(0f, 0f, W, H)
            val rr = 18f * d; c.drawRoundRect(glass, rr, rr, pCard)
            clip.reset(); clip.addRoundRect(glass, rr, rr, Path.Direction.CW); c.save(); c.clipPath(clip)
            drawGlass(c, W, H); c.restore(); c.drawRoundRect(glass, rr, rr, pCardLine)
        } else { glass.set(0f, 0f, W, H); drawGlass(c, W, H) }
    }

    private fun drawGlass(c: Canvas, W: Float, H: Float) {
        val e = engine ?: return; val s = snap ?: return; val spec = e.model.spec; val sc = e.scenario
        val pose = s.pose; val h = pose.heading
        val ahead = spec.wheelbase * 0.62; val lat = (spec.width / 2 + 0.12) * side
        val rx = sin(h); val ry = -cos(h)
        val pos = Vec(pose.x + cos(h) * ahead + rx * lat, pose.y + sin(h) * ahead + ry * lat)
        val yawOut = yawDeg ?: 18.0; val yaw = h + Math.PI + side * Math.toRadians(yawOut)
        val P = pitchDeg?.let { Scene.SIDE.copy(pitch = Math.toRadians(it)) } ?: (if (s.reverse) Scene.SIDE_DOWN else Scene.SIDE)
        val cam = Scene.Cam(pos, yaw, P, W, H)
        Scene.drawScene(cam, c, W, H, spec, s, sc, ownBody = true, blink = blink, ownSide = side)
        if (sc.env == "rain") Scene.drawRain(c, W, H); if (sc.env == "night") Scene.drawNight(c, W, H)
        if (!noBcw) { val fw = doubleArrayOf(cos(h), sin(h)); val rt = doubleArrayOf(sin(h), -cos(h))
            fun inZone(q: Vec): Boolean { val dx = q.x - pose.x; val dy = q.y - pose.y; val lon = dx * fw[0] + dy * fw[1]; val la = (dx * rt[0] + dy * rt[1]) * side; return lon > -6 && lon < 1 && la > 0.5 && la < 3.5 }
            val warn = sc.obstacles.any { it.isCar && inZone(it.box.c) } || (s.hazard?.let { inZone(it.pos) } ?: false)
            if (warn && (System.currentTimeMillis() / 350) % 2 == 0L) { val r = min(W, H) * 0.09f; val cx = if (side > 0) W * 0.14f else W * 0.86f; val cy = H * 0.22f
                pIcon.color = Color.rgb(255, 154, 31); c.drawCircle(cx, cy, r, pIcon)
                pIcon.color = Color.rgb(26, 18, 0); clip.reset(); clip.moveTo(cx, cy - r * 0.55f); clip.lineTo(cx + r * 0.5f, cy + r * 0.4f); clip.lineTo(cx - r * 0.5f, cy + r * 0.4f); clip.close(); c.drawPath(clip, pIcon)
                pIcon.color = Color.rgb(255, 154, 31); c.drawRect(cx - r * 0.07f, cy - r * 0.2f, cx + r * 0.07f, cy + r * 0.18f, pIcon); c.drawRect(cx - r * 0.07f, cy + r * 0.24f, cx + r * 0.07f, cy + r * 0.34f, pIcon) } }
        // 거울 유리 느낌(view.js drawMirror): 푸르스름한 틴트 + 왼쪽 위 비스듬한 반사 띠 + 가장자리 어둡게 + 검은 테
        pGlass.shader = null; pGlass.color = Scene.rgba(30, 45, 70, 0.07); c.drawRect(0f, 0f, W, H, pGlass)
        pGlass.shader = LinearGradient(0f, 0f, W * 0.55f, H, intArrayOf(Scene.rgba(255, 255, 255, 0.16), Scene.rgba(255, 255, 255, 0.05), Color.TRANSPARENT), floatArrayOf(0f, 0.28f, 0.42f), Shader.TileMode.CLAMP); c.drawRect(0f, 0f, W, H, pGlass); pGlass.shader = null
        Scene.vignette(c, W, H, H * 0.45f, H * 0.95f, 0.45)
        Scene.noiseOver(c, W, H, 0.04)
        pRim.strokeWidth = max(3f, W / 60); c.drawRect(0f, 0f, W, H, pRim)
    }
}
