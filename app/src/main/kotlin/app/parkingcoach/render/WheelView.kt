package app.parkingcoach.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 핸들(요즘 현대 핸들, view.js drawWheel): 더블 D컷 림(디 올 뉴 아반떼) · 9시·3시·6시 3스포크(더 뉴 투싼) · 가로 허브에 모스 부호 H 점 네 개 · 12시 파란 점.
 * interactive 면 끌어서 돌린다(시뮬 모드, 실차에서는 내 차 핸들 값만 보인다). 두 번 누르면 가운데.
 */
class WheelView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var steer = 0.0; set(v) { field = v.coerceIn(-540.0, 540.0); invalidate() }
    var interactive = true
    var onChange: ((Double) -> Unit)? = null
    var onTouchStart: (() -> Unit)? = null
    private val pRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val pSpoke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.rgb(183, 188, 198) }
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(154, 163, 178); textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.MONOSPACE }
    private val rim = Path(); private val rect = RectF()
    private var dragA = 0.0; private var dragging = false; private var lastTap = 0L

    private fun angAt(x: Float, y: Float) = Math.toDegrees(atan2((y - height / 2.0), (x - width / 2.0)))
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!interactive) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { onTouchStart?.invoke(); dragging = true; dragA = angAt(e.x, e.y); parent?.requestDisallowInterceptTouchEvent(true)
                val now = System.currentTimeMillis(); if (now - lastTap < 300) { steer = 0.0; onChange?.invoke(steer) }; lastTap = now; return true }
            MotionEvent.ACTION_MOVE -> { if (!dragging) return false; var d = angAt(e.x, e.y) - dragA; while (d > 180) d -= 360; while (d < -180) d += 360; dragA = angAt(e.x, e.y); steer += d; onChange?.invoke(steer); return true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { dragging = false; return true }
        }
        return false
    }

    override fun onDraw(c: Canvas) {
        val size = min(width, height).toFloat(); if (size <= 0) return
        val cx = width / 2f; val cy = height / 2f; val r = size * 0.42f
        c.save(); c.translate(cx, cy); c.rotate(steer.toFloat())
        val fy = r * 0.87f; val fx = sqrt(1 - 0.87f * 0.87f) * r; val a = Math.toDegrees(atan2(fy.toDouble(), fx.toDouble())).toFloat()
        rect.set(-r, -r, r, r)
        rim.reset(); rim.moveTo(-fx, -fy); rim.lineTo(fx, -fy); rim.arcTo(rect, -a, 2 * a); rim.lineTo(-fx, fy); rim.arcTo(rect, 180 - a, 2 * a); rim.close()
        pRim.strokeWidth = size * 0.12f; pRim.color = Color.rgb(42, 46, 55); c.drawPath(rim, pRim)
        pRim.strokeWidth = size * 0.08f; pRim.color = Color.rgb(58, 63, 75); c.drawPath(rim, pRim)
        pRim.strokeWidth = size * 0.02f; pRim.color = Scene.rgba(255, 255, 255, 0.12); val r2 = r - size * 0.03f; rect.set(-r2, -r2, r2, r2); c.drawArc(rect, 180 + a + 8.6f, 180 - 2 * a - 17.2f, false, pRim)
        pSpoke.strokeWidth = size * 0.085f
        c.drawLine(-r + size * 0.06f, -size * 0.01f, -size * 0.15f, size * 0.025f, pSpoke); c.drawLine(r - size * 0.06f, -size * 0.01f, size * 0.15f, size * 0.025f, pSpoke); c.drawLine(0f, size * 0.13f, 0f, fy - size * 0.03f, pSpoke)
        val hw = size * 0.2f; val hh = size * 0.105f; rect.set(-hw, -hh, hw, hh); pFill.color = Color.rgb(29, 32, 39); c.drawRoundRect(rect, size * 0.04f, size * 0.04f, pFill)
        pFill.color = Color.rgb(214, 218, 226); for (k in floatArrayOf(-1.5f, -0.5f, 0.5f, 1.5f)) c.drawCircle(k * size * 0.06f, 0f, size * 0.022f, pFill)
        pFill.color = Color.rgb(0, 170, 210); c.drawCircle(0f, -fy, size * 0.035f, pFill)
        c.restore()
        pText.textSize = size * 0.07f; c.drawText("%s%d°".format(if (steer >= 0) "+" else "", Math.round(steer)), cx, cy + r + size * 0.11f, pText)
    }
}
