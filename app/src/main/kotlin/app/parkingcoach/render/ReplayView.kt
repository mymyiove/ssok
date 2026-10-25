package app.parkingcoach.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import app.parkingcoach.game.TracePoint
import kotlin.math.abs

/** 주차 완료 뒤: 시간축 × 핸들 각도. 후진 구간은 주황, 전진은 파랑 배경. */
class ReplayView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var trace: List<TracePoint> = emptyList(); set(v) { field = v; invalidate() }
    private val pBg = Paint().apply { color = Color.rgb(21, 24, 31) }
    private val pGrid = Paint().apply { color = Color.rgb(42, 47, 58); strokeWidth = 2f }
    private val pRev = Paint().apply { color = Color.argb(28, 255, 179, 64) }
    private val pFwd = Paint().apply { color = Color.argb(30, 74, 158, 255) }
    private val pLine = Paint().apply { color = Color.rgb(255, 179, 64); style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true }
    private val pText = Paint().apply { color = Color.rgb(154, 163, 178); textSize = 26f; isAntiAlias = true }

    override fun onDraw(c: Canvas) {
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pBg)
        val t = trace; if (t.isEmpty()) return
        val tmax = t.last().t.coerceAtLeast(0.1)
        val x = { s: Double -> (24 + (width - 48) * s / tmax).toFloat() }
        val y = { d: Double -> (height / 2 - (height / 2 - 18) * d / 540.0).toFloat() }
        for (i in 1 until t.size) c.drawRect(x(t[i - 1].t), 18f, maxOf(x(t[i].t), x(t[i - 1].t) + 1), height - 18f, if (t[i].v < 0) pRev else pFwd)
        for (v in doubleArrayOf(-540.0, 0.0, 540.0)) c.drawLine(24f, y(v), width - 24f, y(v), pGrid)
        c.drawText("+540°", 28f, y(540.0) + 26f, pText); c.drawText("0°", 28f, y(0.0) - 8f, pText); c.drawText("−540°", 28f, y(-540.0) - 8f, pText)
        val path = Path(); t.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.t), y(p.steeringDeg)) else path.lineTo(x(p.t), y(p.steeringDeg)) }
        c.drawPath(path, pLine)
        val full = t.count { abs(it.steeringDeg) > 500 }
        c.drawText("핸들 끝까지 잡은 시간 %.1fs · 후진 %d%%".format(full * tmax / t.size, t.count { it.v < 0 } * 100 / t.size), width - 420f, 40f, pText)
    }
}
