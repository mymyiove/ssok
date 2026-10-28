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
        // 고무 페달 패드 + 금속 테(시안 .pedal 2026-10-27 조작부 질감): 가로 홈이 패드 전체에, 테 3dp
        rect.set(1.5f * d, 1.5f * d, W - 1.5f * d, H - 1.5f * d); pPad.color = Color.rgb(58, 62, 71); c.drawRoundRect(rect, r, r, pPad)
        rect.set(4.5f * d, 4.5f * d, W - 4.5f * d, H - 4.5f * d); pPad.color = Color.rgb(29, 32, 37); c.drawRoundRect(rect, r - 3 * d, r - 3 * d, pPad)
        c.save(); c.clipRect(rect); var y = rect.top + 4 * d
        while (y < rect.bottom - 2 * d) { pRidge.color = Scene.rgba(255, 255, 255, 0.07); c.drawRect(rect.left, y, rect.right, y + 2 * d, pRidge); pRidge.color = Scene.rgba(0, 0, 0, 0.18); c.drawRect(rect.left, y + 2 * d, rect.right, y + 3 * d, pRidge); y += 9 * d }
        c.restore()
        pEdge.shader = LinearGradient(0f, 0f, 0f, H, intArrayOf(Scene.rgba(255, 255, 255, 0.1), Color.TRANSPARENT, Color.TRANSPARENT, Scene.rgba(0, 0, 0, 0.6)), floatArrayOf(0f, 0.1f, 0.88f, 1f), Shader.TileMode.CLAMP); c.drawRoundRect(rect, r - 3 * d, r - 3 * d, pEdge); pEdge.shader = null
        val fw = (W * depth).toFloat(); if (fw > 0) { c.save(); c.clipRect(0f, 0f, fw, H); pFill.shader = LinearGradient(0f, 0f, W, 0f, Scene.rgba(255, 255, 255, 0.1), Scene.rgba(255, 255, 255, 0.26), Shader.TileMode.CLAMP); c.drawRoundRect(rect, r - 3 * d, r - 3 * d, pFill); pFill.shader = null; c.restore() }
        pStop.color = Scene.rgba(255, 255, 255, 0.5); c.drawRect(W * 0.6f, rect.top, W * 0.6f + 2 * d, rect.bottom, pStop)
        if (on) { rect.set(2f, 2f, W - 2, H - 2); pOn.color = Scene.rgba(255, 90, 90, 0.6); c.drawRoundRect(rect, r, r, pOn) }
        pText.textSize = 18 * d   // 계기판 BRAKE 게이지를 빼고 페달 글자 하나로, 잘 보이게(PM 2026-10-27)
        val pct = Math.round(depth * 100)
        val cap = if (!interactive) (if (on) "BRAKE 밟음" else "BRAKE 뗌") else if (latch) "BRAKE $pct% · 고정" else if (depth > 0) "BRAKE $pct%" else "BRAKE"
        c.drawText(cap, W / 2, H / 2 + pText.textSize * 0.35f, pText)
    }
}
