package app.parkingcoach.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** 주차 거리 경고(현대 PDW 꼴, 시안 .pdw 2026-10-27): 작은 차 위에 호 세 개. level 0~3 = 가까울수록 많이·빨갛게(종전 막대 세 개는 ≡ 메뉴처럼 보였다) */
class PdwView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private var level = 0
    fun set(lv: Int) { if (lv != level) { level = lv; invalidate() } }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val pCar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Scene.rgba(255, 255, 255, 0.55) }
    private val rect = RectF()
    override fun onDraw(c: Canvas) {
        val W = width.toFloat(); val H = height.toFloat(); if (W <= 0 || H <= 0) return; val d = resources.displayMetrics.density
        rect.set(W / 2 - 5 * d, H - 6 * d, W / 2 + 5 * d, H); c.drawRoundRect(rect, 2 * d, 2 * d, pCar)
        p.strokeWidth = 3 * d
        for (k in 1..3) { val r = (4 + k * 5.5f) * d; val cy = H - 8 * d - (k - 1) * 1.5f * d
            p.color = if (k <= level) Scene.PDW_COL[level] else Scene.rgba(255, 255, 255, 0.14)
            rect.set(W / 2 - r, cy - r, W / 2 + r, cy + r); c.drawArc(rect, 200f, 140f, false, p) }
    }
}
