package app.parkingcoach.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/** 첫 진입 화면 안내(PM 2026-10-27, 시안 .tourMarks): 각 부분의 테두리 + 번호 동그라미. anchors 의 뷰 위치를 창 기준으로 읽어 이 뷰 좌표로 그린다 */
class TourMarksView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var anchors: List<View> = emptyList(); set(v) { field = v; invalidate() }
    private val d = resources.displayMetrics.density
    private val pBox = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * d; color = Color.rgb(0, 170, 210) }
    private val pDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 170, 210) }
    private val pTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
    private val rect = RectF(); private val me = IntArray(2); private val it = IntArray(2)
    override fun onDraw(c: Canvas) {
        getLocationInWindow(me); pTxt.textSize = 13 * d
        anchors.forEachIndexed { i, v -> if (v.width == 0) return@forEachIndexed; v.getLocationInWindow(it); val x = (it[0] - me[0]).toFloat(); val y = (it[1] - me[1]).toFloat()
            rect.set(x, y, x + v.width, y + v.height); c.drawRoundRect(rect, 16 * d, 16 * d, pBox)
            c.drawCircle(x + 14 * d, y + 14 * d, 13 * d, pDot); c.drawText("${i + 1}", x + 14 * d, y + 14 * d + pTxt.textSize * 0.35f, pTxt) }
    }
}
