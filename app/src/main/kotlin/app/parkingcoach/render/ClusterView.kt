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
import android.view.View
import kotlin.math.roundToInt

/** 클러스터에 보일 값 한 벌(프레임마다 MainActivity 가 채운다). gear 는 가상 기어(P R N D), realP = 실차 기어가 P 인가 */
data class ClusterData(val kmh: Double = 0.0, val gear: String = "P", val realP: Boolean = true, val sigL: Boolean = false, val sigR: Boolean = false,
                       val brakeD: Double = 1.0, val latch: Boolean = true, val steer: Double = 0.0, val elapsed: Double = 0.0, val hits: Int = 0, val src: String = "sim", val msg: String = "")

/**
 * 슬림 클러스터(PM 2026-10-26 '계기판도 진짜 차처럼 — 핸들 앞 9.9인치 패널', view.js drawCluster 와 같은 그림):
 * 검정 유리 바 위에 흰 큰 숫자. 왼쪽 ◀ · 핸들 각도 · 가운데 속도 · 오른쪽 시간 · ▶ (PM 2026-10-28, P R N D 는 기어 레버에 있어 뺐다). 낮·밤 상관없이 검정(물리 디스플레이).
 */
class ClusterView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var data = ClusterData(); private set
    fun set(d: ClusterData) { if (d == data) return; data = d; invalidate() }
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = Scene.rgba(255, 255, 255, 0.10) }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG)
    private val med: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val bold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    private val rect = RectF(); private val path = Path()
    private val DIM = Color.rgb(141, 147, 160); private val DIM2 = Color.rgb(74, 79, 89); private val WH = Color.rgb(244, 245, 247); private val RED = Color.rgb(255, 90, 90); private val GREEN = Color.rgb(53, 208, 122)

    private val f12 get() = 12f * resources.displayMetrics.density   // 글자 하한 12dp(PM 2026-10-27)
    private fun text(s: String, x: Float, y: Float, size: Float, col: Int, align: Paint.Align, tf: Typeface = med): Float { pText.textSize = maxOf(size, f12); pText.color = col; pText.textAlign = align; pText.typeface = tf; c!!.drawText(s, x, y, pText); return pText.measureText(s) }
    private var c: Canvas? = null
    override fun onDraw(cv: Canvas) {
        val W = width.toFloat(); val H = height.toFloat(); if (W <= 0 || H <= 0) return; c = cv; val d = data
        val r = H * 0.16f; rect.set(0f, 0f, W, H); pFill.shader = null; pFill.color = Color.rgb(10, 11, 13); cv.drawRoundRect(rect, r, r, pFill)
        pFill.shader = LinearGradient(0f, 0f, 0f, H, intArrayOf(Scene.rgba(255, 255, 255, 0.09), Scene.rgba(255, 255, 255, 0.015), Color.TRANSPARENT), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP); cv.drawRoundRect(rect, r, r, pFill); pFill.shader = null
        rect.set(0.5f, 0.5f, W - 0.5f, H - 0.5f); cv.drawRoundRect(rect, r, r, pLine)
        // 배치(PM 2026-10-28, view.js drawCluster 와 같다): 깜빡이는 맨 위 양 끝 모서리, 왼쪽 = ◀ · 핸들 각도(숫자만), 가운데 = 속도, 오른쪽 = 시간(+실수) · ▶, 숫자 셋은 아래 끝 맞춤(기준선 0.8)
        val pad = W * 0.045f; val lx = pad; val rr = W - pad; val ty = H * 0.3f; val ah = H * 0.17f; val base = H * 0.8f
        fun tri(x: Float, dir: Int, on: Boolean) { val y = ty - H * 0.055f; pFill.color = if (on) GREEN else Color.rgb(38, 41, 48); path.reset(); path.moveTo(x + dir * ah, y); path.lineTo(x, y - ah * 0.62f); path.lineTo(x, y + ah * 0.62f); path.close(); cv.drawPath(path, pFill) }
        tri(lx + ah, -1, d.sigL); tri(rr - ah, 1, d.sigR)
        // 왼쪽: 핸들 각도
        text("핸들", lx + ah * 1.6f, ty, H * 0.15f, DIM, Paint.Align.LEFT)
        val sa = d.steer.coerceIn(-540.0, 540.0)
        text((if (sa > 0) "+" else if (sa < 0) "−" else "") + "${kotlin.math.abs(sa.roundToInt())}°", lx, base, H * 0.34f, WH, Paint.Align.LEFT)
        // 가운데: 속도
        val cx = W / 2; val sp = d.kmh.roundToInt().toString(); val sw = text(sp, cx, base, H * 0.56f, WH, Paint.Align.CENTER, bold)
        text("km/h", cx + sw / 2 + H * 0.1f, base, H * 0.15f, DIM, Paint.Align.LEFT)
        // 오른쪽: 시간(+실수 초)
        text("TIME", rr - ah * 1.6f, ty, H * 0.15f, DIM, Paint.Align.RIGHT)
        val ts = "%.1f s".format(d.elapsed); val tw = text(ts, rr, base, H * 0.34f, WH, Paint.Align.RIGHT)
        if (d.hits > 0) text("+${d.hits * 5}", rr - tw - H * 0.1f, base, H * 0.15f, RED, Paint.Align.RIGHT)
        // 계기판 안내 팝업(실차 클러스터 팝업 자리): 예) 브레이크를 밟고 변속하세요
        if (d.msg.isNotEmpty()) { pText.textSize = H * 0.17f; pText.typeface = med; val tw = pText.measureText(d.msg) + H * 0.5f; val th = H * 0.36f
            rect.set(cx - tw / 2, H / 2 - th / 2, cx + tw / 2, H / 2 + th / 2); pFill.color = WH; cv.drawRoundRect(rect, th / 2, th / 2, pFill); text(d.msg, cx, H / 2 + pText.textSize * 0.35f, H * 0.17f, Color.rgb(10, 11, 13), Paint.Align.CENTER) }
        c = null
    }
}
