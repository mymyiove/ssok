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
    private val pObs = Paint().apply { color = Color.rgb(96, 98, 108); isAntiAlias = true }
    private val pObsTop = Paint().apply { color = Color.rgb(130, 132, 142); isAntiAlias = true }
    private val pYellow = Paint().apply { color = Color.rgb(255, 210, 40); style = Paint.Style.STROKE; strokeWidth = 6f; isAntiAlias = true }
    private val pBlue = Paint().apply { color = Color.rgb(80, 170, 255); style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true; pathEffect = android.graphics.DashPathEffect(floatArrayOf(16f, 12f), 0f) }
    private val pRed = Paint().apply { color = Color.rgb(240, 80, 80); style = Paint.Style.STROKE; strokeWidth = 6f; isAntiAlias = true }
    private val pMark = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
    private val pText = Paint().apply { color = Color.WHITE; textSize = 26f; isAntiAlias = true }
    private val pBumper = Paint().apply { color = Color.rgb(20, 20, 24) }

    private val nearZ = 0.15

    /** 월드 점 → 카메라 좌표 (right, yr, zc). 뒤를 보므로 좌우가 거울처럼 뒤집힌다(실제 후방카메라와 같음). */
    private fun toCam(p: Vec, pose: app.parkingcoach.sim.Pose, rearOverhang: Double, z0: Double): DoubleArray {
        val camX = pose.x - rearOverhang * cos(pose.heading); val camY = pose.y - rearOverhang * sin(pose.heading)
        val dx = p.x - camX; val dy = p.y - camY
        val back = -(dx * cos(pose.heading) + dy * sin(pose.heading))
        val right = -(-dx * sin(pose.heading) + dy * cos(pose.heading))
        val yc = z0 - camH
        val zc = back * cos(pitch) - yc * sin(pitch)
        val yr = back * sin(pitch) + yc * cos(pitch)
        return doubleArrayOf(right, yr, zc)
    }
    private fun toScreen(c: DoubleArray): Pair<Float, Float> {
        val f = (width / 2f) / tan(hfov / 2).toFloat()
        return (width / 2f + (f * c[0] / c[2]).toFloat()) to (height / 2f - (f * c[1] / c[2]).toFloat())
    }
    private fun project(p: Vec, pose: app.parkingcoach.sim.Pose, rearOverhang: Double, z0: Double = 0.0): Pair<Float, Float>? {
        val c = toCam(p, pose, rearOverhang, z0); return if (c[2] < nearZ) null else toScreen(c)
    }
    /** 근평면(z = nearZ) 기준 다각형 클리핑. 카메라 뒤로 넘어간 꼭짓점을 버리지 않고 교점으로 자른다. */
    private fun clipNear(poly: List<DoubleArray>): List<DoubleArray> {
        if (poly.isEmpty()) return poly
        val out = ArrayList<DoubleArray>()
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[(i + 1) % poly.size]
            val ina = a[2] >= nearZ; val inb = b[2] >= nearZ
            if (ina) out += a
            if (ina != inb) {
                val t = (nearZ - a[2]) / (b[2] - a[2])
                out += doubleArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, nearZ)
            }
        }
        return out
    }
    private fun clipSegment(a: DoubleArray, b: DoubleArray): Pair<DoubleArray, DoubleArray>? {
        val ina = a[2] >= nearZ; val inb = b[2] >= nearZ
        if (!ina && !inb) return null
        if (ina && inb) return a to b
        val t = (nearZ - a[2]) / (b[2] - a[2]); val m = doubleArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, nearZ)
        return if (ina) a to m else m to b
    }

    private fun drawWorldPath(c: Canvas, pts: List<Vec>, pose: app.parkingcoach.sim.Pose, ro: Double, p: Paint) {
        val cam = pts.map { toCam(it, pose, ro, 0.0) }
        val path = Path()
        for (i in 1 until cam.size) {
            val seg = clipSegment(cam[i - 1], cam[i]) ?: continue
            val s0 = toScreen(seg.first); val s1 = toScreen(seg.second)
            path.moveTo(s0.first, s0.second); path.lineTo(s1.first, s1.second)
        }
        c.drawPath(path, p)
    }
    private fun drawWorldPoly(c: Canvas, b: OBB, pose: app.parkingcoach.sim.Pose, ro: Double, p: Paint, h: Double = 0.0) {
        val cs = clipNear(b.corners().map { toCam(it, pose, ro, h) }).map { toScreen(it) }
        if (cs.size < 3) return
        val path = Path(); path.moveTo(cs[0].first, cs[0].second); cs.drop(1).forEach { path.lineTo(it.first, it.second) }; path.close(); c.drawPath(path, p)
    }
    /** 장애물을 상자(바닥 + 4 측면 + 윗면)로. 카메라에서 먼 면부터 그린다(화가 알고리즘 간이판). */
    private fun drawBox(c: Canvas, b: OBB, pose: app.parkingcoach.sim.Pose, ro: Double, h: Double, side: Paint, top: Paint) {
        val cs = b.corners()
        val faces = (0 until 4).map { i -> listOf(cs[i], cs[(i + 1) % 4]) }
        val sorted = faces.sortedByDescending { f -> f.sumOf { toCam(it, pose, ro, 0.0)[2] } }
        for (f in sorted) {
            val quad = listOf(toCam(f[0], pose, ro, 0.0), toCam(f[1], pose, ro, 0.0), toCam(f[1], pose, ro, h), toCam(f[0], pose, ro, h))
            val q = clipNear(quad).map { toScreen(it) }; if (q.size < 3) continue
            val path = Path(); path.moveTo(q[0].first, q[0].second); q.drop(1).forEach { path.lineTo(it.first, it.second) }; path.close(); c.drawPath(path, side)
        }
        drawWorldPoly(c, b, pose, ro, top, h)
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
        e.scenario.obstacles.sortedByDescending { toCam(it.box.c, s.pose, ro, 0.0)[2] }.forEach { o ->
            drawBox(c, o.box, s.pose, ro, if (o.isCar) 1.4 else 2.2, pObs, pObsTop)
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
