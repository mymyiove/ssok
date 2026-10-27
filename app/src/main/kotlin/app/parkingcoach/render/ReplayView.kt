package app.parkingcoach.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import app.parkingcoach.game.GameSnapshot
import app.parkingcoach.game.HazStats
import app.parkingcoach.game.Phase
import app.parkingcoach.game.TracePoint
import app.parkingcoach.sim.CarModel
import app.parkingcoach.sim.Scenario
import app.parkingcoach.sim.Vec
import kotlin.math.max
import kotlin.math.min

/**
 * 결과 리플레이(view.js drawReplayTop): 탑뷰 애니메이션(2.5배속) 위에 내 궤적(파란 실선, 뒷차축)과 공식 궤적(흰 점선 = 같은 상황을 시연 운전자가 간 길)을 겹친다.
 * play() 로 처음부터 다시. 프레임 루프가 invalidate() 를 부른다.
 */
class ReplayView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var model: CarModel? = null
    var scenario: Scenario? = null
    var trace: List<TracePoint> = emptyList()
    var ghost: List<Vec> = emptyList()
    private var t0 = 0L
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.MONOSPACE }
    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    fun play() { t0 = System.currentTimeMillis(); invalidate() }
    fun set(model: CarModel, sc: Scenario, trace: List<TracePoint>, ghost: List<Vec>) { this.model = model; this.scenario = sc; this.trace = trace; this.ghost = ghost; play() }

    override fun onDraw(c: Canvas) {
        val m = model ?: return; val sc = scenario ?: return; val T = trace
        val W = width.toFloat(); val H = height.toFloat(); if (W <= 0 || H <= 0) return
        val dur = if (T.isEmpty()) 0.0 else T.last().t
        val tt = min(dur, (System.currentTimeMillis() - t0) / 1000.0 * 2.5)
        var i = 0; while (i < T.size - 1 && T[i + 1].t <= tt) i++
        val tp = T.getOrNull(i); val pose = tp?.pose ?: sc.start
        val snap = GameSnapshot(Phase.DRIVING, pose, m.body(pose), tp?.steeringDeg ?: 0.0, tp?.v ?: 0.0, "", 0, tp?.t ?: 0.0, 0, 1, (tp?.v ?: 0.0) < 0, null, HazStats(), null)
        val light = !Scene.night(context)
        val map = TopDraw.drawTopView(c, W, H, m, snap, sc, tp?.steeringDeg ?: 0.0, guide = false, blink = false, pdw = false, light = light, ghost = false)
        fun line(pts: List<Vec>, col: Int, lw: Float, dash: FloatArray?) { if (pts.size < 2) return
            path.reset(); pts.forEachIndexed { k, p -> if (k == 0) path.moveTo(map.X(p.x), map.Y(p.y)) else path.lineTo(map.X(p.x), map.Y(p.y)) }
            pLine.color = col; pLine.strokeWidth = lw; pLine.pathEffect = dash?.let { android.graphics.DashPathEffect(it, 0f) }; c.drawPath(path, pLine); pLine.pathEffect = null }
        if (ghost.isNotEmpty()) line(ghost, if (light) Scene.rgba(30, 34, 40, 0.7) else Scene.rgba(255, 255, 255, 0.75), max(2f, 0.08f * map.scale), floatArrayOf(8f, 7f))
        line(T.take(i + 1).map { Vec(it.pose.x, it.pose.y) }, Scene.rgba(80, 170, 255, 0.95), max(2.5f, 0.1f * map.scale), null)
        pText.textSize = max(11f, W / 46); pText.color = if (light) Scene.rgba(20, 22, 27, 0.85) else Scene.rgba(255, 255, 255, 0.85); pText.textAlign = Paint.Align.LEFT
        val st = tp?.steeringDeg ?: 0.0
        c.drawText("%.1f s · 핸들 %s%d°".format(tp?.t ?: 0.0, if (st >= 0) "+" else "", Math.round(st)), 10f, H - 10f, pText)
        pText.textAlign = Paint.Align.RIGHT; pText.color = if (light) Scene.rgba(20, 22, 27, 0.6) else Scene.rgba(255, 255, 255, 0.6); c.drawText("── 내 궤적   ┈┈ 공식 궤적", W - 10f, H - 10f, pText)
    }
}
