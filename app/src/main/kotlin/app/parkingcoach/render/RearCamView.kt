package app.parkingcoach.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import app.parkingcoach.game.GameEngine
import app.parkingcoach.game.GameSnapshot
import app.parkingcoach.sim.OBB
import app.parkingcoach.sim.Vec
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 후방카메라 화면 재현. 카메라 = 뒷범퍼 중앙, 높이 0.9 m, 아래로 25° 기울임, 광각.
 * 바닥 점(차 뒤쪽 거리 d, 옆 s) → 핀홀 투영. 가이드라인 색은 현대 설명서: 노란(조향 연동)·파란(중립)·빨간(경고) + 0.5/1/2.3 m 표시.
 */
class RearCamView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var engine: GameEngine? = null
    var snap: GameSnapshot? = null

    private val camH = 0.95; private val pitch = Math.toRadians(25.0); private val hfov = Math.toRadians(130.0)
    private val pSky = Paint().apply { color = Color.rgb(28, 30, 36) }
    private val pGround = Paint().apply { color = Color.rgb(52, 54, 60) }
    private val pLine = Paint().apply { color = Color.rgb(235, 235, 235); style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true }
    private val pObs = Paint().apply { color = Color.rgb(110, 110, 120); isAntiAlias = true }
    private val pYellow = Paint().apply { color = Color.rgb(255, 210, 40); style = Paint.Style.STROKE; strokeWidth = 6f; isAntiAlias = true }
    private val pBlue = Paint().apply { color = Color.rgb(80, 170, 255); style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true; pathEffect = android.graphics.DashPathEffect(floatArrayOf(16f, 12f), 0f) }
    private val pRed = Paint().apply { color = Color.rgb(240, 80, 80); style = Paint.Style.STROKE; strokeWidth = 6f; isAntiAlias = true }
    private val pMark = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
    private val pText = Paint().apply { color = Color.WHITE; textSize = 26f; isAntiAlias = true }
    private val pBumper = Paint().apply { color = Color.rgb(20, 20, 24) }

    /** 월드 점 → 카메라 좌표(뒤쪽 거리 z, 오른쪽 x, 높이 y) → 화면. 뒤를 보므로 좌우가 거울처럼 뒤집힌다(실제 후방카메라와 같음). */
    private fun project(p: Vec, pose: app.parkingcoach.sim.Pose, rearOverhang: Double, z0: Double = 0.0): Pair<Float, Float>? {
        val camX = pose.x - rearOverhang * cos(pose.heading); val camY = pose.y - rearOverhang * sin(pose.heading)
        val dx = p.x - camX; val dy = p.y - camY
        val back = -(dx * cos(pose.heading) + dy * sin(pose.heading))           // 차 뒤쪽 +
        val right = -(-dx * sin(pose.heading) + dy * cos(pose.heading))        // 차 기준 오른쪽 + (뒤를 보니 좌우 반전)
        val yc = z0 - camH
        // pitch 회전(아래로)
        val zc = back * cos(pitch) - yc * sin(pitch)
        val yr = back * sin(pitch) + yc * cos(pitch)
        if (zc < 0.15) return null
        val f = (width / 2f) / tan(hfov / 2).toFloat()
        val u = width / 2f + (f * right / zc).toFloat()
        val v = height / 2f - (f * yr / zc).toFloat()
        return u to v
    }

    private fun drawWorldPath(c: Canvas, pts: List<Vec>, pose: app.parkingcoach.sim.Pose, ro: Double, p: Paint) {
        val path = Path(); var started = false
        for (q in pts) { val s = project(q, pose, ro) ?: continue; if (!started) { path.moveTo(s.first, s.second); started = true } else path.lineTo(s.first, s.second) }
        c.drawPath(path, p)
    }
    private fun drawWorldPoly(c: Canvas, b: OBB, pose: app.parkingcoach.sim.Pose, ro: Double, p: Paint, h: Double = 0.0) {
        val cs = b.corners().mapNotNull { project(it, pose, ro, h) }
        if (cs.size < 3) return
        val path = Path(); path.moveTo(cs[0].first, cs[0].second); cs.drop(1).forEach { path.lineTo(it.first, it.second) }; path.close(); c.drawPath(path, p)
    }

    override fun onDraw(c: Canvas) {
        val e = engine ?: return; val s = snap ?: return
        val ro = e.model.spec.rearOverhang
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pSky)
        // 지평선 아래 바닥
        val horizon = height / 2f - ((width / 2f) / tan(hfov / 2).toFloat()) * tan(pitch).toFloat()
        c.drawRect(0f, horizon, width.toFloat(), height.toFloat(), pGround)
        // 주차선·목표 칸
        drawWorldPoly(c, e.scenario.slot, s.pose, ro, pLine)
        // 장애물: 바닥 발자국 + 높이 1.4 m 상단(차)·2.2 m(기둥) 까지 면 하나
        e.scenario.obstacles.forEach { o ->
            drawWorldPoly(c, o.box, s.pose, ro, pObs)
            drawWorldPoly(c, o.box, s.pose, ro, pObs, if (o.isCar) 1.4 else 2.2)
        }
        // 가이드라인(후진 기준)
        val (nl, nr) = e.model.predictedPath(s.pose, 0.0, reverse = true, range = 3.0, n = 2)
        drawWorldPath(c, nl, s.pose, ro, pBlue); drawWorldPath(c, nr, s.pose, ro, pBlue)
        val (yl, yr) = e.model.predictedPath(s.pose, s.steeringDeg, reverse = true, range = 4.0, n = 40)
        drawWorldPath(c, yl, s.pose, ro, pYellow); drawWorldPath(c, yr, s.pose, ro, pYellow)
        val (rl, rr) = e.model.predictedPath(s.pose, s.steeringDeg, reverse = true, range = 0.5, n = 6)
        drawWorldPath(c, rl, s.pose, ro, pRed); drawWorldPath(c, rr, s.pose, ro, pRed)
        // 거리 표시 0.5 / 1.0 / 2.3 m (중립선 사이 가로줄)
        for (d in doubleArrayOf(0.5, 1.0, 2.3)) {
            val (a, b) = e.model.rearCorners(s.pose)
            val back = Vec(-cos(s.pose.heading), -sin(s.pose.heading)) * d
            val pa = project(a + back, s.pose, ro) ?: continue; val pb = project(b + back, s.pose, ro) ?: continue
            c.drawLine(pa.first, pa.second, pb.first, pb.second, pMark)
            c.drawText("${d}m", pb.first + 8f, pb.second, pText)
        }
        // 범퍼(화면 아래 검은 띠) — 자차가 보이면 거리 추정 편향이 사라진다(Bernhard & Hecht 2021)
        c.drawRect(0f, height * 0.9f, width.toFloat(), height.toFloat(), pBumper)
        c.drawText("쏙 · 후방 카메라 (연습 화면)", 16f, 36f, pText)
    }
}
