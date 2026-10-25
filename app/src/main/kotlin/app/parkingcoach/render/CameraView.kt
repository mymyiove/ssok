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
    private val pBump = Paint()

    override fun onDraw(c: Canvas) {
        val e = engine ?: return; val s = snap ?: return
        val W = width.toFloat(); val H = height.toFloat(); if (W <= 0 || H <= 0) return
        val rev = s.reverse; val pose = s.pose; val spec = e.model.spec; val sc = e.scenario
        val cam = Scene.Cam(e.model.bumper(pose, rev), if (rev) pose.heading + Math.PI else pose.heading, if (rev) Scene.REAR else Scene.FRONT, W, H)
        Scene.drawScene(cam, c, W, H, spec, s, sc, ownBody = false, blink = false)
        if (guide) {
            fun pr(st: Double, range: Double, n: Int, col: Int, lw: Float, dash: FloatArray? = null) { val (L, R) = e.model.predictedPath(pose, st, rev, range, n)
                Scene.strokeSegs(c, cam.path(L, 0.01), col, lw, dash); Scene.strokeSegs(c, cam.path(R, 0.01), col, lw, dash) }
            pr(0.0, 3.0, 2, Scene.rgba(80, 170, 255, 0.95), W / 320, floatArrayOf(W / 90, W / 140)); pr(steer, 4.0, 40, Color.rgb(255, 210, 40), W / 190); pr(steer, 0.5, 6, Color.rgb(255, 77, 77), W / 190)
            val (a, b) = if (rev) e.model.rearCorners(pose) else e.model.frontCorners(pose); val dir = if (rev) -1.0 else 1.0
            pText.textSize = W / 46
            for (d in doubleArrayOf(0.5, 1.0, 2.3)) { val bk = Vec(cos(pose.heading) * d * dir, sin(pose.heading) * d * dir)
                val segs = cam.path(listOf(a + bk, b + bk), 0.01); Scene.strokeSegs(c, segs, Scene.rgba(255, 255, 255, 0.9), W / 480)
                if (segs.isNotEmpty()) { val s0 = segs[0].first; val s1 = segs[0].second; c.drawText("${d}m", max(s0[0], s1[0]) + 8, s1[1] + 6, pText) } }
        }
        if (sc.env == "rain") Scene.drawRain(c, W, H); if (sc.env == "night") Scene.drawNight(c, W, H)
        pBump.shader = LinearGradient(0f, H * 0.86f, 0f, H, intArrayOf(Color.TRANSPARENT, Color.rgb(16, 17, 20), Color.rgb(7, 7, 8)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, H * 0.86f, W, H, pBump)
        Scene.vignette(c, W, H, H * 0.45f, H * 0.95f, 0.55)
        Scene.noiseOver(c, W, H, 0.07)
    }
}
