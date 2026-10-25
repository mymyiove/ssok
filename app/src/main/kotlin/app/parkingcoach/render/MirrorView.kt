package app.parkingcoach.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import app.parkingcoach.game.GameEngine
import app.parkingcoach.game.GameSnapshot
import app.parkingcoach.sim.Vec
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 사이드미러(view.js drawMirror). side = -1 왼쪽 · +1 오른쪽. A필러 옆에서 뒤를 보며 바깥쪽 18°, 볼록 거울, 거울상, R 이면 15° 하향(R 연동 미러).
 * 기둥 가운데에 실제 거울 유리 비율 17:10 의 하우징을 그리고 그 안에 장면을 그린다. 후측방 경고(BCW): 그쪽 옆·뒤에 차·사람이 있으면 주황 아이콘 점멸.
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
    var label = ""
    private val pBg = Paint().apply { color = Color.rgb(10, 11, 13) }
    private val pBezel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(42, 45, 53); style = Paint.Style.STROKE; strokeWidth = 6f }
    private val pBezel2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(14, 16, 19); style = Paint.Style.STROKE; strokeWidth = 10f }
    private val pLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Scene.rgba(255, 255, 255, 0.75); textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.MONOSPACE }
    private val pIcon = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    val glass = RectF()

    override fun onDraw(c: Canvas) {
        val W = width.toFloat(); val H = height.toFloat(); if (W <= 0 || H <= 0) return
        if (housing) {
            c.drawRect(0f, 0f, W, H, pBg)
            val gw = min(W - 16f, (H - 40f) * 1.7f); val gh = gw / 1.7f; val gx = (W - gw) / 2; val gy = (H - gh) / 2 - 6f
            glass.set(gx, gy, gx + gw, gy + gh)
            pLabel.textSize = min(11f * resources.displayMetrics.density, gh * 0.12f); c.drawText(label, W / 2, glass.bottom + pLabel.textSize + 6f, pLabel)
            c.drawRoundRect(glass, 14f, 14f, pBezel2); c.drawRoundRect(glass, 14f, 14f, pBezel)
            clip.reset(); clip.addRoundRect(glass, 12f, 12f, Path.Direction.CW); c.save(); c.clipPath(clip); c.translate(glass.left, glass.top)
            drawGlass(c, gw, gh); c.restore()
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
        Scene.drawScene(cam, c, W, H, spec, s, sc, ownBody = true, blink = blink)
        if (sc.env == "rain") Scene.drawRain(c, W, H); if (sc.env == "night") Scene.drawNight(c, W, H)
        if (!noBcw) { val fw = doubleArrayOf(cos(h), sin(h)); val rt = doubleArrayOf(sin(h), -cos(h))
            fun inZone(q: Vec): Boolean { val dx = q.x - pose.x; val dy = q.y - pose.y; val lon = dx * fw[0] + dy * fw[1]; val la = (dx * rt[0] + dy * rt[1]) * side; return lon > -6 && lon < 1 && la > 0.5 && la < 3.5 }
            val warn = sc.obstacles.any { it.isCar && inZone(it.box.c) } || (s.hazard?.let { inZone(it.pos) } ?: false)
            if (warn && (System.currentTimeMillis() / 350) % 2 == 0L) { val r = min(W, H) * 0.09f; val cx = if (side > 0) W * 0.14f else W * 0.86f; val cy = H * 0.22f
                pIcon.color = Color.rgb(255, 154, 31); c.drawCircle(cx, cy, r, pIcon)
                pIcon.color = Color.rgb(26, 18, 0); clip.reset(); clip.moveTo(cx, cy - r * 0.55f); clip.lineTo(cx + r * 0.5f, cy + r * 0.4f); clip.lineTo(cx - r * 0.5f, cy + r * 0.4f); clip.close(); c.drawPath(clip, pIcon)
                pIcon.color = Color.rgb(255, 154, 31); c.drawRect(cx - r * 0.07f, cy - r * 0.2f, cx + r * 0.07f, cy + r * 0.18f, pIcon); c.drawRect(cx - r * 0.07f, cy + r * 0.24f, cx + r * 0.07f, cy + r * 0.34f, pIcon) } }
        Scene.vignette(c, W, H, H * 0.4f, H * 0.9f, 0.5)
        Scene.noiseOver(c, W, H, 0.05)
    }
}
