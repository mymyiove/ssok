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

/** 어라운드뷰(SVM) 탑뷰 재현: 내 차를 위에서, 노란선(조향 연동 예상 경로)·파란선(중립)·빨간선(경고). 돌발은 빨간 점 + 이모지. */
class TopView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var engine: GameEngine? = null
    var snap: GameSnapshot? = null

    private val pLot = Paint().apply { color = Color.rgb(38, 40, 46) }
    private val pLine = Paint().apply { color = Color.rgb(230, 230, 230); style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
    private val pMarks = Paint().apply { color = Color.argb(90, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true }
    private val pSlot = Paint().apply { color = Color.rgb(90, 200, 120); style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true }
    private val pSlotOk = Paint().apply { color = Color.argb(90, 90, 200, 120) }
    private val pCar = Paint().apply { color = Color.rgb(70, 130, 240); isAntiAlias = true }
    private val pObs = Paint().apply { color = Color.rgb(120, 120, 130); isAntiAlias = true }
    private val pPillar = Paint().apply { color = Color.rgb(200, 160, 80) }
    private val pHaz = Paint().apply { color = Color.argb(230, 255, 90, 90); isAntiAlias = true }
    private val pYellow = Paint().apply { color = Color.rgb(255, 210, 40); style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true }
    private val pBlue = Paint().apply { color = Color.rgb(80, 170, 255); style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true; pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 10f), 0f) }
    private val pRed = Paint().apply { color = Color.rgb(240, 80, 80); style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true }
    private val pText = Paint().apply { color = Color.WHITE; textSize = 28f; isAntiAlias = true }
    private val pEmoji = Paint().apply { color = Color.WHITE; textSize = 36f; isAntiAlias = true; textAlign = Paint.Align.CENTER }

    private var scale = 60f; private var ox = 0f; private var oy = 0f

    private fun sx(x: Double) = ox + (x * scale).toFloat()
    private fun sy(y: Double) = oy - (y * scale).toFloat()

    private fun drawOBB(c: Canvas, b: OBB, p: Paint) {
        val cs = b.corners(); val path = Path()
        path.moveTo(sx(cs[0].x), sy(cs[0].y)); for (i in 1 until 4) path.lineTo(sx(cs[i].x), sy(cs[i].y)); path.close()
        c.drawPath(path, p)
    }
    private fun drawPoly(c: Canvas, pts: List<Vec>, p: Paint) {
        if (pts.size < 2) return
        val path = Path(); path.moveTo(sx(pts[0].x), sy(pts[0].y)); for (q in pts.drop(1)) path.lineTo(sx(q.x), sy(q.y)); c.drawPath(path, p)
    }

    override fun onDraw(c: Canvas) {
        val e = engine ?: return; val s = snap ?: return
        val sc = e.scenario
        // 화면 맞춤: bounds 가 뷰에 들어가게
        scale = minOf(width / (sc.bounds.hx * 2).toFloat(), height / (sc.bounds.hy * 2).toFloat()) * 0.95f
        ox = width / 2f - (sc.bounds.c.x * scale).toFloat(); oy = height / 2f + (sc.bounds.c.y * scale).toFloat()
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pLot)
        // 주차선(옆 칸) · 목표 칸
        sc.marks.forEach { drawOBB(c, it, pMarks) }
        drawOBB(c, sc.slot, if (s.phase == app.parkingcoach.game.Phase.PARKED) pSlotOk else pSlot)
        sc.obstacles.forEach { o -> if (o.isCar) { pObs.color = o.color; drawOBB(c, o.box, pObs) } else drawOBB(c, o.box, pPillar) }
        // 가이드라인: 중립(파랑) 과 현재 조향(노랑), 끝 0.5m 는 빨강 — 진행 방향 기준(후진 = 뒷범퍼, 전진 = 앞범퍼)
        val (nl, nr) = e.model.predictedPath(s.pose, 0.0, reverse = s.reverse, range = 3.0, n = 2)
        drawPoly(c, nl, pBlue); drawPoly(c, nr, pBlue)
        val (yl, yr) = e.model.predictedPath(s.pose, s.steeringDeg, reverse = s.reverse, range = 4.0, n = 40)
        drawPoly(c, yl, pYellow); drawPoly(c, yr, pYellow)
        val (rl, rr) = e.model.predictedPath(s.pose, s.steeringDeg, reverse = s.reverse, range = 0.5, n = 6)
        drawPoly(c, rl, pRed); drawPoly(c, rr, pRed)
        // 내 차
        drawOBB(c, s.body, pCar)
        val f = s.body.c + Vec(Math.cos(s.pose.heading), Math.sin(s.pose.heading)) * (e.model.spec.length / 2 - 0.3)
        c.drawCircle(sx(f.x), sy(f.y), 7f, pLine)
        // 돌발
        s.hazard?.let { hz ->
            c.drawCircle(sx(hz.pos.x), sy(hz.pos.y), maxOf(8f, (hz.type.r * scale).toFloat()), pHaz)
            c.drawText(hz.type.emoji, sx(hz.pos.x), sy(hz.pos.y) + 12f, pEmoji)
        }
        c.drawText("탑뷰 · ${sc.title}", 16f, 36f, pText)
    }
}
