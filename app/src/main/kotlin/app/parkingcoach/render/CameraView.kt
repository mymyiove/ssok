package app.parkingcoach.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import app.parkingcoach.game.GameEngine
import app.parkingcoach.game.GameSnapshot
import app.parkingcoach.sim.Vec
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * 후방/전방 카메라 화면(view.js drawCamera): 장면 + 가이드라인(노란 조향 연동 · 파란 중립 · 빨간 0.5 m) + 거리 표시 0.5/1/2.3 m + 범퍼 띠 + 비네트 + 노이즈.
 * 카메라는 들어간 가상 기어를 따른다(R 후방 · D 전방).
 */
class CameraView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var engine: GameEngine? = null
    var snap: GameSnapshot? = null
    var steer = 0.0
    var guide = true
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Scene.rgba(255, 255, 255, 0.92); typeface = android.graphics.Typeface.MONOSPACE }
    private val pBump = Paint(Paint.ANTI_ALIAS_FLAG); private val bumpPath = android.graphics.Path()
    private val pEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Scene.rgba(170, 200, 255, 0.55) }
    private val pOut = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; color = Scene.rgba(10, 12, 16, 0.75); typeface = android.graphics.Typeface.MONOSPACE }

    override fun onDraw(c: Canvas) {
        val e = engine ?: return; val s = snap ?: return
        val W = width.toFloat(); val H = height.toFloat(); if (W <= 0 || H <= 0) return
        val rev = s.reverse; val pose = s.pose; val spec = e.model.spec; val sc = e.scenario
        val cam = Scene.Cam(e.model.bumper(pose, rev), if (rev) pose.heading + Math.PI else pose.heading, if (rev) Scene.REAR else Scene.FRONT, W, H)
        Scene.drawScene(cam, c, W, H, spec, s, sc, ownBody = false, blink = false)
        if (guide) {
            // 가이드는 바닥 선이라 벽·차를 만나면 거기서 끊는다(낮은 연석은 가리지 않는다) — view.js drawCamera 와 같다
            fun blocked(p: Vec) = sc.obstacles.any { it.label != "연석" && it.box.distTo(p) < 0.01 }
            fun hitOn(p: Vec, q: Vec): Vec? { val n = max(1, Math.ceil(hypot(q.x - p.x, q.y - p.y) / 0.05).toInt()); for (i in 1..n) { val t = i.toDouble() / n; val r = Vec(p.x + (q.x - p.x) * t, p.y + (q.y - p.y) * t); if (blocked(r)) return r }; return null }
            fun cut(pts: List<Vec>): List<Vec> { for (k in 1 until pts.size) { val h = hitOn(pts[k - 1], pts[k]); if (h != null) return pts.subList(0, k) + h }; return pts }
            fun pr(st: Double, range: Double, n: Int, col: Int, lw: Float, dash: FloatArray? = null) { val (L, R) = e.model.predictedPath(pose, st, rev, range, n)
                Scene.strokeSegs(c, cam.path(cut(L), 0.01), col, lw, dash); Scene.strokeSegs(c, cam.path(cut(R), 0.01), col, lw, dash) }
            pr(0.0, 3.0, 2, Scene.rgba(80, 170, 255, 0.95), W / 320, floatArrayOf(W / 90, W / 140)); pr(steer, 2.5, 30, Color.rgb(255, 210, 40), W / 190); pr(steer, 0.5, 6, Color.rgb(255, 77, 77), W / 190)
            val (a, b) = if (rev) e.model.rearCorners(pose) else e.model.frontCorners(pose); val dir = if (rev) -1.0 else 1.0
            pText.textSize = W / 46
            for (d in doubleArrayOf(0.5, 1.0, 2.3)) { val bk = Vec(cos(pose.heading) * d * dir, sin(pose.heading) * d * dir)
                val m = Vec((a.x + b.x) / 2, (a.y + b.y) / 2); if (hitOn(m, m + bk) != null) continue
                val segs = cam.path(listOf(a + bk, b + bk), 0.01); Scene.strokeSegs(c, segs, Scene.rgba(255, 255, 255, 0.9), W / 480)
                if (segs.isNotEmpty()) { val s0 = segs[0].first; val s1 = segs[0].second; val tx = max(s0[0], s1[0]) + 8; val ty = s1[1] + 6
                    pOut.textSize = pText.textSize; pOut.strokeWidth = max(3f, W / 260); c.drawText("${d}m", tx, ty, pOut); c.drawText("${d}m", tx, ty, pText) } }   // 글자 테두리: 흰 주차선 위에서도 읽히게
        }
        if (sc.env == "rain") Scene.drawRain(c, W, H); if (sc.env == "night") Scene.drawNight(c, W, H)
        // 내 차 범퍼(실제 카메라처럼 화면 아래에 둥글게): 벽·차 앞에 바짝 섰을 때도 '내 차가 여기' 기준
        run { val y0 = H * 0.8f; val y1 = H * 0.85f; bumpPath.reset(); bumpPath.moveTo(0f, y0); bumpPath.quadTo(W / 2, y1 + (y1 - y0), W, y0); bumpPath.lineTo(W, H); bumpPath.lineTo(0f, H); bumpPath.close()
            pBump.shader = LinearGradient(0f, y0, 0f, H, intArrayOf(Color.rgb(42, 79, 158), Color.rgb(22, 41, 90), Color.rgb(11, 20, 48)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP); c.drawPath(bumpPath, pBump); pBump.shader = null
            bumpPath.reset(); bumpPath.moveTo(0f, y0); bumpPath.quadTo(W / 2, y1 + (y1 - y0), W, y0); pEdge.strokeWidth = max(1.5f, W / 500); c.drawPath(bumpPath, pEdge) }
        Scene.vignette(c, W, H, H * 0.45f, H * 0.95f, 0.55)
        Scene.noiseOver(c, W, H, 0.07)
    }
}
