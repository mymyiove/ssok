package app.parkingcoach.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min

/**
 * 컬럼 레버(현대 컬럼식, 투싼 2025 매뉴얼: 위로 돌리면 D · 아래로 R · 끝 버튼 P. view.js drawStalk 와 같은 그림).
 * 운전석에서 본 옆모습 — 왼쪽 컬럼 기둥 → 오른쪽으로 뻗은 레버 → 널링 손잡이(돌아가면 줄무늬·글자가 위아래로 흐름) → 끝 P 버튼.
 * 시뮬 모드: 손잡이를 위로 끌면 D 쪽, 아래로 R 쪽으로 한 단씩(디텐트 26dp). 탭은 위 = D · 아래 = R · 가운데 = N. 실차 모드(locked)는 P 고정이라 돌아가지 않는다.
 */
class StalkView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var gear = "N"; private set
    var realP = true
    var locked = false
    /** 손잡이 각(연속값, D=+1 · N=0 · R=-1) — 프레임마다 gear 쪽으로 부드럽게 */
    var a = 0.0; private set
    var onGear: ((String) -> Unit)? = null
    var onLockedTouch: (() -> Unit)? = null
    private val d = resources.displayMetrics.density
    private var dragY = 0f; private var dragging = false; private var moved = 0
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG); private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val bold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    private val rect = RectF(); private val clip = Path()
    private val GV = mapOf("D" to 1, "N" to 0, "R" to -1); private val VG = listOf("R", "N", "D")

    /** 프레임마다: 가상 기어와 dt 로 손잡이를 돌린다 */
    fun tick(g: String, dt: Double) { gear = g; val tgt = if (locked) 0.0 else (GV[g] ?: 0).toDouble(); val na = a + (tgt - a) * min(1.0, dt * 14); if (abs(na - a) > 1e-4 || g != gear) { a = na; invalidate() } else a = na }
    fun select(g: String) { if (locked) { onLockedTouch?.invoke(); return }; onGear?.invoke(g) }
    private fun step(dir: Int) { val cur = GV[gear] ?: 0; val nxt = (cur + dir).coerceIn(-1, 1); if (nxt != cur) select(VG[nxt + 1]) }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { if (locked) { onLockedTouch?.invoke(); return true }; dragging = true; dragY = e.y; moved = 0; parent?.requestDisallowInterceptTouchEvent(true); return true }
            MotionEvent.ACTION_MOVE -> { if (!dragging) return false; val dy = e.y - dragY; if (abs(dy) >= 26 * d) { step(if (dy < 0) 1 else -1); dragY = e.y; moved++ }; return true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { if (!dragging) return false; dragging = false
                if (e.actionMasked == MotionEvent.ACTION_UP && moved == 0) { val y = e.y / height; select(if (y < 0.4f) "D" else if (y > 0.6f) "R" else "N"); performClick() }; return true }
        }
        return false
    }
    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onDraw(c: Canvas) {
        val W = width.toFloat(); val H = height.toFloat(); if (W <= 0 || H <= 0) return
        val cy = H / 2; val colW = W * 0.14f; val armH = H * 0.13f; val kx0 = W * 0.36f; val kx1 = W * 0.76f; val kh = H * 0.62f; val step = H * 0.2f
        pFill.shader = null; pFill.color = Color.rgb(30, 32, 38); rect.set(0f, cy - H * 0.3f, colW, cy + H * 0.3f); c.drawRoundRect(rect, 10f, 10f, pFill)
        pFill.shader = LinearGradient(0f, cy - armH / 2, 0f, cy + armH / 2, intArrayOf(Color.rgb(90, 94, 104), Color.rgb(58, 61, 69), Color.rgb(31, 33, 38)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        rect.set(colW - 4, cy - armH / 2, kx0 + 4, cy + armH / 2); c.drawRoundRect(rect, armH / 2, armH / 2, pFill)
        pFill.shader = LinearGradient(0f, cy - kh / 2, 0f, cy + kh / 2, intArrayOf(if (locked) Color.rgb(58, 61, 68) else Color.rgb(85, 89, 99), if (locked) Color.rgb(42, 45, 51) else Color.rgb(51, 54, 61), Color.rgb(21, 22, 26)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        rect.set(kx0, cy - kh / 2, kx1, cy + kh / 2); c.drawRoundRect(rect, 9f, 9f, pFill); pFill.shader = null
        clip.reset(); clip.addRoundRect(rect, 9f, 9f, Path.Direction.CW); c.save(); c.clipPath(clip)
        val sp = H * 0.06f; val ph = ((-a * step) % sp).toFloat(); pFill.color = Scene.rgba(255, 255, 255, 0.10)
        var y = cy - kh / 2 - sp + ph; while (y < cy + kh / 2 + sp) { c.drawRect(kx0, y, kx0 + (kx1 - kx0) * 0.22f, y + 2f, pFill); y += sp }
        pText.textSize = H * 0.17f; pText.typeface = bold
        for ((k, t) in listOf(1 to "D", 0 to "N", -1 to "R")) { val yy = cy - (k - a).toFloat() * step; val on = t == gear
            pText.color = if (on) Color.WHITE else Scene.rgba(255, 255, 255, 0.28); if (on) pText.setShadowLayer(8f, 0f, 0f, Scene.rgba(255, 255, 255, 0.6)) else pText.clearShadowLayer()
            c.drawText(t, (kx0 + kx1) / 2 + (kx1 - kx0) * 0.12f, yy + pText.textSize * 0.35f, pText) }
        pText.clearShadowLayer(); c.restore()
        val px = kx1 - 2; val pw = W - px; rect.set(px, cy - kh * 0.3f, W, cy + kh * 0.3f); pFill.color = if (realP) Color.rgb(244, 245, 247) else Color.rgb(42, 45, 51); c.drawRoundRect(rect, 7f, 7f, pFill)
        pText.textSize = min(H * 0.13f, pw * 0.6f); pText.color = if (realP) Color.rgb(10, 11, 13) else Scene.rgba(255, 255, 255, 0.35); c.drawText("P", px + pw / 2, cy + pText.textSize * 0.35f, pText)
    }
}
