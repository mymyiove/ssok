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
 * 브레이크 = 핸들 아래 가로로 넓은 고무 페달 패드(PM 2026-10-26 '가로로 긴 모양', 시안 .pedal). 누른 자리가 강도(왼쪽 살살 · 오른쪽 세게, PM 2026-10-25) —
 * 60% 정지선 아래는 누르는 동안만, 정지선을 넘겨 밟고 떼면 멈춘 채 고정(오토홀드), 왼쪽을 톡 치면 풀림. 채움이 왼쪽부터 밝아지고 글자는 가운데.
 * 시뮬 모드 전용. 실차에서는 브레이크 페달 신호를 받아 밟음/뗌만 표시한다(interactive = false).
 */
class BrakeBarView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var depth = 1.0; private set
    var latch = true; private set
    var interactive = true
    var onChange: ((Double) -> Unit)? = null
    var onTouchStart: (() -> Unit)? = null
    private var hold = false
    private val pPad = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(27, 29, 34) }
    private val pEdge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Scene.rgba(255, 255, 255, 0.22) }
    private val pStop = Paint().apply { color = Scene.rgba(255, 255, 255, 0.35) }
    private val pRidge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Scene.rgba(255, 255, 255, 0.14) }
    private val pOn = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = Scene.rgba(255, 90, 90, 0.55) }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true; color = Color.WHITE; setShadowLayer(2f, 0f, 1f, Scene.rgba(0, 0, 0, 0.6)) }
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
        val W = width.toFloat(); val H = height.toFloat(); if (W <= 0 || H <= 0) return
        val d = resources.displayMetrics.density; val on = depth >= 0.6; val r = 12 * d
        rect.set(2f, 2f, W - 2, H - 2); c.drawRoundRect(rect, r, r, pPad)
        pEdge.shader = LinearGradient(0f, 0f, 0f, H, intArrayOf(Scene.rgba(255, 255, 255, 0.14), Color.TRANSPARENT, Color.TRANSPARENT, Scene.rgba(0, 0, 0, 0.6)), floatArrayOf(0f, 0.08f, 0.9f, 1f), Shader.TileMode.CLAMP); c.drawRoundRect(rect, r, r, pEdge); pEdge.shader = null
        val fw = (W * depth).toFloat(); if (fw > 0) { c.save(); c.clipRect(0f, 0f, fw, H); c.drawRoundRect(rect, r, r, pFill); c.restore() }
        c.drawRect(W * 0.6f, 2f, W * 0.6f + 2 * d, H - 2, pStop)
        for (k in 0..2) { val y = 11 * d + k * 10 * d; if (y < H - 6 * d) c.drawRect(12 * d, y, W - 12 * d, y + 2 * d, pRidge) }
        if (on) c.drawRoundRect(rect, r, r, pOn)
        pText.textSize = 11 * d
        val pct = Math.round(depth * 100)
        val cap = if (!interactive) (if (on) "BRAKE 밟음" else "BRAKE 뗌") else if (latch) "BRAKE $pct% · 고정" else if (depth > 0) "BRAKE $pct%" else "BRAKE"
        c.drawText(cap, W / 2, H / 2 + pText.textSize * 0.35f, pText)
    }
}
