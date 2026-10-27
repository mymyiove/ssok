package app.parkingcoach.render

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * 첫 진입 화면 안내(PM 2026-10-27 '한 단계씩', 시안 .spot): 어두운 막에 둥근 구멍 하나. show(view) 로 그 뷰 자리에 구멍이 미끄러져 간다.
 * 구멍 좌표는 창 기준으로 읽어 이 뷰 좌표로 바꾼다. 구멍 둘레에 강조 테두리.
 */
class SpotlightView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    val hole = RectF(); private val from = RectF(); private val to = RectF()
    private val d = resources.displayMetrics.density
    private val pScrim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(0x8C, 0, 0, 0) }
    private val pRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * d; color = Color.rgb(0, 170, 210) }
    private val path = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private var anim: ValueAnimator? = null; private var first = true

    /** 창 기준 위치를 읽어 이 뷰 좌표의 구멍으로. 처음은 바로, 그 뒤는 260ms 미끄러짐. */
    fun show(target: View, pad: Float = 6 * d) {
        val r = rectIn(this, target)
        to.set(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad)
        anim?.cancel()
        if (first) { first = false; hole.set(to); invalidate(); return }
        from.set(hole)
        anim = ValueAnimator.ofFloat(0f, 1f).apply { duration = 260; interpolator = DecelerateInterpolator()
            addUpdateListener { a -> val t = a.animatedValue as Float
                hole.set(from.left + (to.left - from.left) * t, from.top + (to.top - from.top) * t, from.right + (to.right - from.right) * t, from.bottom + (to.bottom - from.bottom) * t); invalidate() }
            start() }
    }
    fun reset() { first = true; anim?.cancel() }

    companion object {
        /** target 의 자리를 container 좌표로. 창에 붙지 않은 상태(스냅샷 테스트)에서도 되도록 공통 조상에서 내려 계산한다. */
        fun rectIn(container: View, target: View): RectF {
            val root = container.rootView as? android.view.ViewGroup ?: return RectF(0f, 0f, target.width.toFloat(), target.height.toFloat())
            val a = android.graphics.Rect(0, 0, target.width, target.height); root.offsetDescendantRectToMyCoords(target, a)
            val b = android.graphics.Rect(0, 0, container.width, container.height); root.offsetDescendantRectToMyCoords(container, b)
            return RectF((a.left - b.left).toFloat(), (a.top - b.top).toFloat(), (a.right - b.left).toFloat(), (a.bottom - b.top).toFloat())
        }
    }

    override fun onDraw(c: Canvas) {
        val r = 16 * d
        path.reset(); path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        if (!hole.isEmpty) path.addRoundRect(hole, r, r, Path.Direction.CW)
        c.drawPath(path, pScrim)
        if (!hole.isEmpty) c.drawRoundRect(hole, r, r, pRing)
    }
}
