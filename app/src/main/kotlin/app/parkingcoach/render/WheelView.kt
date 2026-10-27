package app.parkingcoach.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 핸들(플레오스 심플 + 질감, PM 2026-10-27 "그림판 같다" — view.js drawWheelFlat 과 같은 그림): 가죽 림(라디얼 음영) · 박음질 · 베벨 스포크 셋 · 허브(점 네 개 유지, 엠블럼 아님) · 12시 파란 띠 · 더블 D컷.
 * 조명은 돌리지 않고 마지막에 SRC_ATOP 으로 덧칠해 540° 돌려도 위가 밝고 아래가 어둡다.
 * interactive 면 끌어서 돌린다(시뮬 모드, 실차에서는 내 차 핸들 값만 보인다). 두 번 누르면 가운데.
 */
class WheelView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var steer = 0.0; set(v) { field = v.coerceIn(-540.0, 540.0); invalidate() }
    var interactive = true
    var onChange: ((Double) -> Unit)? = null
    var onLock: (() -> Unit)? = null   // 끝(±540°)에 닿는 순간
    private var atLock = false
    var onTouchStart: (() -> Unit)? = null
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Path(); private val rect = RectF()
    private var dragA = 0.0; private var dragging = false; private var lastTap = 0L

    private fun angAt(x: Float, y: Float) = Math.toDegrees(atan2((y - height / 2.0), (x - width / 2.0)))
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!interactive) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { onTouchStart?.invoke(); dragging = true; dragA = angAt(e.x, e.y); parent?.requestDisallowInterceptTouchEvent(true)
                val now = System.currentTimeMillis(); if (now - lastTap < 300) { steer = 0.0; onChange?.invoke(steer) }; lastTap = now; return true }
            MotionEvent.ACTION_MOVE -> { if (!dragging) return false; var d = angAt(e.x, e.y) - dragA; while (d > 180) d -= 360; while (d < -180) d += 360; dragA = angAt(e.x, e.y); steer += d; val l = kotlin.math.abs(steer) >= 540.0; if (l && !atLock) onLock?.invoke(); atLock = l; onChange?.invoke(steer); return true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { dragging = false; return true }
        }
        return false
    }

    private fun rr(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, rad: Float) { rect.set(x0, y0, x1, y1); c.drawRoundRect(rect, rad, rad, pFill) }
    private fun seg(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, w: Float, col: Int) { pStroke.strokeWidth = w; pStroke.color = col; pStroke.shader = null; c.drawLine(x0, y0, x1, y1, pStroke) }

    override fun onDraw(c: Canvas) {
        val size = min(width, height).toFloat(); if (size <= 0) return
        val cx = width / 2f; val cy = height / 2f; val r = size * 0.45f; val rw = size * 0.105f
        val fy = r * 0.87f; val fx = sqrt(1 - 0.87f * 0.87f) * r; val a = Math.toDegrees(atan2(fy.toDouble(), fx.toDouble())).toFloat()
        val layer = c.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        c.translate(cx, cy); c.save(); c.rotate(steer.toFloat())
        rect.set(-r, -r, r, r); rim.reset(); rim.moveTo(-fx, -fy); rim.lineTo(fx, -fy); rim.arcTo(rect, -a, 2 * a); rim.lineTo(-fx, fy); rim.arcTo(rect, 180 - a, 2 * a); rim.close()
        // 림: 안쪽 어둡게 → 가운데 밝게 → 바깥 어둡게 = 둥근 가죽
        val t0 = (r - rw / 2) / (r + rw / 2)
        pStroke.shader = RadialGradient(0f, 0f, r + rw / 2, intArrayOf(Color.rgb(21, 23, 27), Color.rgb(58, 62, 70), Color.rgb(44, 47, 54), Color.rgb(17, 18, 22)), floatArrayOf(t0, t0 + 0.42f * (1 - t0), t0 + 0.62f * (1 - t0), 1f), Shader.TileMode.CLAMP)
        pStroke.color = Color.WHITE; pStroke.strokeWidth = rw; pStroke.pathEffect = null; c.drawPath(rim, pStroke); pStroke.shader = null   // color 의 알파가 셰이더에 곱해진다(앞 프레임 박음질 색 0.22 가 남으면 림이 희게 바랜다)
        // 박음질
        pStroke.pathEffect = DashPathEffect(floatArrayOf(size * 0.012f, size * 0.014f), 0f); pStroke.color = Scene.rgba(255, 255, 255, 0.22); pStroke.strokeWidth = maxOf(1f, size * 0.004f); c.drawPath(rim, pStroke); pStroke.pathEffect = null
        // 12시 띠
        pFill.color = Color.rgb(0, 170, 210); rr(c, -size * 0.05f, -fy - rw * 0.3f, size * 0.05f, -fy + rw * 0.3f, size * 0.012f)
        // 스포크 셋(9시 · 3시 · 6시): 그림자 → 몸통 → 윗면 하이라이트 = 베벨
        fun spoke(x0: Float, y0: Float, x1: Float, y1: Float) { seg(c, x0, y0 + size * 0.012f, x1, y1 + size * 0.012f, size * 0.08f, Color.rgb(14, 15, 18)); seg(c, x0, y0, x1, y1, size * 0.07f, Color.rgb(38, 41, 48)); seg(c, x0, y0 - size * 0.016f, x1, y1 - size * 0.016f, size * 0.028f, Color.rgb(61, 65, 74)) }
        spoke(-r + rw * 0.35f, 0f, -size * 0.2f, size * 0.012f); spoke(r - rw * 0.35f, 0f, size * 0.2f, size * 0.012f)
        seg(c, size * 0.012f, size * 0.12f, size * 0.012f, fy - size * 0.03f, size * 0.07f, Color.rgb(14, 15, 18)); seg(c, 0f, size * 0.12f, 0f, fy - size * 0.03f, size * 0.06f, Color.rgb(38, 41, 48)); seg(c, -size * 0.014f, size * 0.13f, -size * 0.014f, fy - size * 0.04f, size * 0.022f, Color.rgb(61, 65, 74))
        // 허브: 바깥 판(그라데이션) + 안쪽 판 + 점 네 개
        val hw = size * 0.2f; val hh = size * 0.105f
        pFill.shader = null; pFill.color = Color.rgb(14, 15, 18); rr(c, -hw, -hh + size * 0.012f, hw, hh + size * 0.012f, size * 0.045f)
        pFill.color = Color.WHITE; pFill.shader = LinearGradient(0f, -hh, 0f, hh, Color.rgb(51, 55, 63), Color.rgb(26, 28, 33), Shader.TileMode.CLAMP); rr(c, -hw, -hh, hw, hh, size * 0.045f); pFill.shader = null
        pStroke.strokeWidth = 1f; pStroke.color = Scene.rgba(255, 255, 255, 0.1); rect.set(-hw + 1, -hh + 1, hw - 1, hh - 1); c.drawRoundRect(rect, size * 0.04f, size * 0.04f, pStroke)
        pFill.color = Color.rgb(21, 23, 27); rr(c, -hw * 0.78f, -hh * 0.5f, hw * 0.78f, hh * 0.5f, size * 0.03f)
        for (k in floatArrayOf(-1.5f, -0.5f, 0.5f, 1.5f)) { val x = k * size * 0.06f; pFill.color = Color.rgb(7, 8, 10); c.drawCircle(x, size * 0.004f, size * 0.021f, pFill); pFill.color = Color.rgb(74, 79, 89); c.drawCircle(x, 0f, size * 0.018f, pFill); pFill.color = Color.rgb(42, 45, 52); c.drawCircle(x, size * 0.004f, size * 0.011f, pFill) }
        c.restore()
        // 조명(돌지 않음): 위는 밝게 아래는 어둡게, 이미 그린 픽셀 위에만
        pFill.color = Color.WHITE; pFill.shader = LinearGradient(0f, -r - rw, 0f, r + rw, intArrayOf(Scene.rgba(255, 255, 255, 0.2), Color.TRANSPARENT, Scene.rgba(0, 0, 0, 0.28)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        pFill.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP); c.drawRect(-cx, -cy, cx, cy, pFill); pFill.xfermode = null; pFill.shader = null
        c.restoreToCount(layer)
    }
}
