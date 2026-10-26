package app.parkingcoach.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import app.parkingcoach.R

/**
 * 브레이크 = 가로 페달 슬라이더(PM 2026-10-25): 누른 자리가 곧 강도(왼쪽 약하게 · 오른쪽 세게). 60% 정지선 아래는 누르는 동안만, 정지선을 넘겨 밟고 떼면 멈춘 채 고정(오토홀드), 왼쪽을 톡 치면 풀림.
 * 플레오스 심플 꼴: 알약(카드색) 안에 먹색 채움 + 바탕색 손잡이, 글자는 채움 위에서 흰색·밖에서 검정(시안 mix-blend-mode).
 * 시뮬 모드 전용. 실차에서는 브레이크 페달 신호를 받아 밟음/뗌만 표시한다(interactive = false).
 */
class BrakeBarView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var depth = 1.0; private set
    var latch = true; private set
    var interactive = true
    var onChange: ((Double) -> Unit)? = null
    var onTouchStart: (() -> Unit)? = null
    private var hold = false
    private val pBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ctx.getColor(R.color.card) }
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ctx.getColor(R.color.ink) }
    private val pStop = Paint().apply { color = Scene.rgba(0, 0, 0, 0.18) }
    private val pKnob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ctx.getColor(R.color.bg) }
    private val pKnobLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Scene.rgba(0, 0, 0, 0.35); style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val fgOnInk = ctx.getColor(R.color.inkFg); private val fg = ctx.getColor(R.color.fg)
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
        val d = resources.displayMetrics.density; val on = depth >= 0.6; val rr = H / 2
        rect.set(0f, 0f, W, H); c.drawRoundRect(rect, rr, rr, pBg)
        val fw = (W * depth).toFloat()
        if (fw > 0) { c.save(); c.clipRect(0f, 0f, fw, H); c.drawRoundRect(rect, rr, rr, pFill); c.restore() }
        c.drawRect(W * 0.6f, 0f, W * 0.6f + 2, H, pStop)
        val kx = 10f + (W - 20f) * depth.toFloat(); rect.set(kx - 6 * d, 8 * d, kx + 6 * d, H - 8 * d); c.drawRoundRect(rect, 6 * d, 6 * d, pKnob); c.drawRoundRect(rect, 6 * d, 6 * d, pKnobLine)
        pText.textSize = 11 * d
        val pct = Math.round(depth * 100)
        val cap = if (!interactive) (if (on) "BRAKE 밟음" else "BRAKE 뗌") else if (depth > 0) "BRAKE $pct%" else "BRAKE"
        val ty = H / 2 + pText.textSize * 0.35f
        c.save(); c.clipRect(0f, 0f, fw, H); pText.color = fgOnInk; c.drawText(cap, W / 2, ty, pText); c.restore()
        c.save(); c.clipRect(fw, 0f, W, H); pText.color = fg; c.drawText(cap, W / 2, ty, pText); c.restore()
    }
}
