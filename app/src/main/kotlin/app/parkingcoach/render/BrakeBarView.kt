package app.parkingcoach.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * 브레이크 = 가로 페달 슬라이더(PM 2026-10-25): 누른 자리가 곧 강도(왼쪽 약하게 · 오른쪽 세게). 60% 정지선 아래는 누르는 동안만, 정지선을 넘겨 밟고 떼면 멈춘 채 고정(오토홀드), 왼쪽을 톡 치면 풀림.
 * 시뮬 모드 전용. 실차에서는 브레이크 페달 신호를 받아 밟음/뗌만 표시한다(interactive = false).
 */
class BrakeBarView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var depth = 1.0; private set
    var latch = true; private set
    var interactive = true
    var onChange: ((Double) -> Unit)? = null
    var onTouchStart: (() -> Unit)? = null
    private var hold = false
    private val pBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(26, 17, 20) }
    private val pBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f }
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pStop = Paint().apply { color = Scene.rgba(255, 255, 255, 0.35) }
    private val pKnob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val pSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Scene.rgba(255, 255, 255, 0.5) }
    private val rect = RectF()

    fun set(d: Double, latched: Boolean) { depth = d.coerceIn(0.0, 1.0); latch = latched && depth >= 0.6; invalidate(); onChange?.invoke(depth) }
    private fun at(x: Float) = ((x - 10f) / (width - 20f)).toDouble().coerceIn(0.0, 1.0)
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!interactive) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { onTouchStart?.invoke(); hold = true; parent?.requestDisallowInterceptTouchEvent(true); set(at(e.x), false); return true }
            MotionEvent.ACTION_MOVE -> { if (hold) set(at(e.x), false); return true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { if (!hold) return false; hold = false; if (depth >= 0.6) set(1.0, true) else set(0.0, false); return true }
        }
        return false
    }

    override fun onDraw(c: Canvas) {
        val W = width.toFloat(); val H = height.toFloat(); if (W <= 0) return
        val d = resources.displayMetrics.density; val on = depth >= 0.6
        rect.set(2f, 2f, W - 2, H - 2); c.drawRoundRect(rect, 14 * d, 14 * d, pBg)
        val fw = (W * depth).toFloat(); if (fw > 0) { pFill.shader = LinearGradient(0f, 0f, W, 0f, Color.rgb(122, 42, 42), Color.rgb(255, 77, 77), Shader.TileMode.CLAMP); rect.set(2f, 2f, fw, H - 2); c.drawRoundRect(rect, 12 * d, 12 * d, pFill); pFill.shader = null }
        c.drawRect(W * 0.6f, 0f, W * 0.6f + 2, H, pStop)
        pSmall.textSize = 9 * d; c.drawText("정지", W * 0.6f + 4, 12 * d, pSmall); c.drawText("← 약하게", 8 * d, 12 * d, pSmall); pSmall.textAlign = Paint.Align.RIGHT; c.drawText("세게 →", W - 8 * d, 12 * d, pSmall); pSmall.textAlign = Paint.Align.LEFT
        val kx = 10f + (W - 20f) * depth.toFloat(); rect.set(kx - 7 * d, 6 * d, kx + 7 * d, H - 6 * d); c.drawRoundRect(rect, 7 * d, 7 * d, pKnob)
        pBorder.color = if (on) Color.rgb(255, 138, 138) else Color.rgb(58, 32, 32); rect.set(2f, 2f, W - 2, H - 2); c.drawRoundRect(rect, 14 * d, 14 * d, pBorder)
        pText.textSize = 10 * d; pText.color = Color.rgb(255, 208, 208)
        val pct = Math.round(depth * 100)
        val cap = if (!interactive) (if (on) "BRAKE 밟음" else "BRAKE 뗌") else if (latch) "BRAKE $pct% · 정지 고정 · 왼쪽 톡 = 풀림" else if (on) "BRAKE $pct% · 정지 (떼면 고정)" else if (depth > 0) "BRAKE $pct% · 살살" else "BRAKE · 누른 자리가 강도"
        c.drawText(cap, W / 2, H - 6 * d, pText)
    }
}
