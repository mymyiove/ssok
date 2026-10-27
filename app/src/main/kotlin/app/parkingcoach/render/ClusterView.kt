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
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** 클러스터에 보일 값 한 벌(프레임마다 MainActivity 가 채운다). gear 는 가상 기어(P R N D), realP = 실차 기어가 P 인가 */
data class ClusterData(val kmh: Double = 0.0, val gear: String = "P", val realP: Boolean = true, val sigL: Boolean = false, val sigR: Boolean = false,
                       val brakeD: Double = 1.0, val latch: Boolean = true, val steer: Double = 0.0, val elapsed: Double = 0.0, val hits: Int = 0, val src: String = "sim", val msg: String = "")

/**
 * 슬림 클러스터(PM 2026-10-26 '계기판도 진짜 차처럼 — 핸들 앞 9.9인치 패널', view.js drawCluster 와 같은 그림):
 * 검정 유리 바 위에 흰 큰 숫자·얇은 막대 게이지. 왼쪽 BRAKE 게이지 · 가운데 깜빡이 ◀ ▶ + 속도 + 위 P R N D · 오른쪽 TIME + 핸들 각도 막대. 낮·밤 상관없이 검정(물리 디스플레이).
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

    private fun text(s: String, x: Float, y: Float, size: Float, col: Int, align: Paint.Align, tf: Typeface = med): Float { pText.textSize = size; pText.color = col; pText.textAlign = align; pText.typeface = tf; c!!.drawText(s, x, y, pText); return pText.measureText(s) }
    private var c: Canvas? = null
    override fun onDraw(cv: Canvas) {
        val W = width.toFloat(); val H = height.toFloat(); if (W <= 0 || H <= 0) return; c = cv; val d = data
        val r = H * 0.16f; rect.set(0f, 0f, W, H); pFill.shader = null; pFill.color = Color.rgb(10, 11, 13); cv.drawRoundRect(rect, r, r, pFill)
        pFill.shader = LinearGradient(0f, 0f, 0f, H, intArrayOf(Scene.rgba(255, 255, 255, 0.09), Scene.rgba(255, 255, 255, 0.015), Color.TRANSPARENT), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP); cv.drawRoundRect(rect, r, r, pFill); pFill.shader = null
        rect.set(0.5f, 0.5f, W - 0.5f, H - 0.5f); cv.drawRoundRect(rect, r, r, pLine)
        val pad = W * 0.045f; val bw = min(W * 0.2f, H * 1.9f); val by = H * 0.8f; val bh = maxOf(2f, H * 0.05f)
        // 왼쪽: BRAKE
        text("BRAKE", pad, H * 0.33f, H * 0.15f, DIM, Paint.Align.LEFT)
        text(if (d.src == "pleos") (if (d.brakeD >= 0.6) "밟음" else "뗌") else "${(d.brakeD * 100).roundToInt()}%", pad, H * 0.66f, H * 0.34f, WH, Paint.Align.LEFT)
        pFill.color = Scene.rgba(255, 255, 255, 0.16); cv.drawRect(pad, by, pad + bw, by + bh, pFill); pFill.color = if (d.brakeD >= 0.6) WH else RED; cv.drawRect(pad, by, pad + bw * d.brakeD.toFloat(), by + bh, pFill)
        pFill.color = Scene.rgba(255, 255, 255, 0.55); cv.drawRect(pad + bw * 0.6f - 1, by - bh * 1.2f, pad + bw * 0.6f + 1, by + bh * 2.2f, pFill)
        text("정지", pad + bw * 0.6f, by + bh * 1.2f + H * 0.1f, H * 0.11f, DIM2, Paint.Align.CENTER)
        // 가운데: 속도 · km/h · 깜빡이 · P R N D
        val cx = W / 2; val sp = d.kmh.roundToInt().toString(); val sw = text(sp, cx, H * 0.72f, H * 0.58f, WH, Paint.Align.CENTER, bold)
        text("km/h", cx + sw / 2 + H * 0.1f, H * 0.72f, H * 0.15f, DIM, Paint.Align.LEFT)
        fun tri(x: Float, dir: Int, on: Boolean) { val h = H * 0.17f; pFill.color = if (on) GREEN else Color.rgb(35, 38, 44); path.reset(); path.moveTo(x + dir * h, H * 0.5f); path.lineTo(x, H * 0.5f - h * 0.7f); path.lineTo(x, H * 0.5f + h * 0.7f); path.close(); cv.drawPath(path, pFill) }
        tri(cx - sw / 2 - H * 0.55f, -1, d.sigL); tri(cx + sw / 2 + H * 0.95f, 1, d.sigR)
        val gw = H * 0.28f; var gx = cx - gw * 1.5f
        for (k in listOf("P", "R", "N", "D")) { val on = k == d.gear
            if (on) { pFill.color = if (d.realP || k != "P") WH else RED; rect.set(gx - gw * 0.38f, H * 0.08f, gx + gw * 0.38f, H * 0.28f); cv.drawRoundRect(rect, 3f, 3f, pFill); text(k, gx, H * 0.24f, H * 0.15f, Color.rgb(10, 11, 13), Paint.Align.CENTER, bold) }
            else text(k, gx, H * 0.24f, H * 0.15f, if (!d.realP && k == "D") RED else DIM2, Paint.Align.CENTER, bold)
            gx += gw }
        // 오른쪽: TIME · 핸들 각도
        val rx = W - pad - bw
        text("TIME", rx, H * 0.33f, H * 0.15f, DIM, Paint.Align.LEFT); text(if (d.src == "pleos") "실차 신호" else "화면 조작", W - pad, H * 0.33f, H * 0.15f, DIM2, Paint.Align.RIGHT)
        val ts = "%.1f s".format(d.elapsed); val tw = text(ts, rx, H * 0.66f, H * 0.34f, WH, Paint.Align.LEFT)
        if (d.hits > 0) text("+${d.hits * 5}", rx + tw + H * 0.1f, H * 0.66f, H * 0.15f, RED, Paint.Align.LEFT)
        pFill.color = Scene.rgba(255, 255, 255, 0.16); cv.drawRect(rx, by, rx + bw, by + bh, pFill); pFill.color = Scene.rgba(255, 255, 255, 0.55); cv.drawRect(rx + bw / 2 - 1, by - bh * 1.2f, rx + bw / 2 + 1, by + bh * 2.2f, pFill)
        val mx = rx + bw / 2 + (d.steer.coerceIn(-540.0, 540.0) / 540.0).toFloat() * bw / 2; pFill.color = WH
        cv.drawRect(min(rx + bw / 2, mx), by, maxOf(rx + bw / 2, mx), by + bh, pFill); cv.drawRect(mx - 1.5f, by - bh, mx + 1.5f, by + bh * 2, pFill)
        text("${if (d.steer >= 0) "+" else ""}${d.steer.roundToInt()}°", W - pad, by + bh * 1.2f + H * 0.1f, H * 0.11f, DIM2, Paint.Align.RIGHT)
        // 계기판 안내 팝업(실차 클러스터 팝업 자리): 예) 브레이크를 밟고 변속하세요
        if (d.msg.isNotEmpty()) { pText.textSize = H * 0.17f; pText.typeface = med; val tw = pText.measureText(d.msg) + H * 0.5f; val th = H * 0.36f
            rect.set(cx - tw / 2, H / 2 - th / 2, cx + tw / 2, H / 2 + th / 2); pFill.color = WH; cv.drawRoundRect(rect, th / 2, th / 2, pFill); text(d.msg, cx, H / 2 + pText.textSize * 0.35f, H * 0.17f, Color.rgb(10, 11, 13), Paint.Align.CENTER) }
        c = null
    }
}
