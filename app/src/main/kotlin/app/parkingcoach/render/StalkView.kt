package app.parkingcoach.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * 기어 블록(PM 2026-10-27 그림, 시안 .gblock): 세로 블록에 D(위) · N · R(아래), 현재 단만 흰 글자, 널링 질감. 위·아래 버튼(레이아웃 gUp/gDn)이 step(±1),
 * 블록을 끌어도 한 단씩(디텐트 26dp), 글자를 누르면 그 단. 방향은 현대 컬럼식(투싼 2025 매뉴얼: 위로 D · 아래로 R)과 같다.
 * 실차 모드(locked)는 P 고정이라 바뀌지 않는다(건드리면 onLockedTouch).
 */
class StalkView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var gear = "N"; private set
    var locked = false
    var onGear: ((String) -> Unit)? = null
    var onLockedTouch: (() -> Unit)? = null
    private val d = resources.displayMetrics.density
    private var dragY = 0f; private var dragging = false; private var moved = 0
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG); private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
    private val rect = RectF()
    private val GV = mapOf("D" to 1, "N" to 0, "R" to -1); private val VG = listOf("R", "N", "D")

    fun set(g: String) { if (g != gear) { gear = g; invalidate() } }
    fun select(g: String) { if (locked) { onLockedTouch?.invoke(); return }; onGear?.invoke(g) }
    /** +1 = D 쪽 한 단, -1 = R 쪽 한 단 */
    fun step(dir: Int) { if (locked) { onLockedTouch?.invoke(); return }; val cur = GV[gear] ?: 0; val nxt = (cur + dir).coerceIn(-1, 1); if (nxt != cur) select(VG[nxt + 1]) }
    private fun gearAt(y: Float) = if (y < height * 0.38f) "D" else if (y > height * 0.62f) "R" else "N"

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { if (locked) { onLockedTouch?.invoke(); return true }; dragging = true; dragY = e.y; moved = 0; parent?.requestDisallowInterceptTouchEvent(true); return true }
            MotionEvent.ACTION_MOVE -> { if (!dragging) return false; val dy = e.y - dragY; if (abs(dy) >= 26 * d) { step(if (dy < 0) 1 else -1); dragY = e.y; moved++ }; return true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { if (!dragging) return false; dragging = false
                if (e.actionMasked == MotionEvent.ACTION_UP && moved == 0) { select(gearAt(e.y)); performClick() }; return true }
        }
        return false
    }
    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onDraw(c: Canvas) {
        val W = width.toFloat(); val H = height.toFloat(); if (W <= 0 || H <= 0) return
        val r = 14 * d; rect.set(0f, 0f, W, H)
        pFill.shader = null; pFill.color = Color.rgb(27, 29, 34); c.drawRoundRect(rect, r, r, pFill)
        pFill.shader = LinearGradient(0f, 0f, 0f, H, intArrayOf(Scene.rgba(255, 255, 255, 0.12), Color.TRANSPARENT, Color.TRANSPARENT, Scene.rgba(0, 0, 0, 0.6)), floatArrayOf(0f, 0.06f, 0.94f, 1f), Shader.TileMode.CLAMP); c.drawRoundRect(rect, r, r, pFill); pFill.shader = null
        pFill.color = Scene.rgba(255, 255, 255, 0.06); var y = 6 * d; while (y < H - 4 * d) { c.drawRect(4 * d, y, W - 4 * d, y + 2 * d, pFill); y += 9 * d }
        pText.textSize = minOf(22 * d, H * 0.16f)
        for ((k, t) in listOf("D" to 0.22f, "N" to 0.5f, "R" to 0.78f)) { val on = k == gear && !locked
            pText.color = if (on) Color.WHITE else Scene.rgba(255, 255, 255, 0.28); if (on) pText.setShadowLayer(10f, 0f, 0f, Scene.rgba(255, 255, 255, 0.6)) else pText.clearShadowLayer()
            c.drawText(k, W / 2, H * t + pText.textSize * 0.35f, pText) }
        pText.clearShadowLayer()
        if (locked) { pText.color = Color.WHITE; pText.setShadowLayer(10f, 0f, 0f, Scene.rgba(255, 255, 255, 0.6)); c.drawText("P", W / 2, H * 0.5f + pText.textSize * 0.35f, pText); pText.clearShadowLayer() }
    }
}
