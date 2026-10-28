package app.parkingcoach.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import app.parkingcoach.sim.Kind
import app.parkingcoach.sim.Neigh
import app.parkingcoach.sim.Side
import app.parkingcoach.sim.StageOpt

/** 스테이지 아이콘(PM 2026-10-28 "글뿐이라 싫다 — 아이콘으로"): 위에서 본 작은 그림 = 방식 + 옆 차 + 기둥. 시안 index.html stgIcon 과 같은 좌표(44×30). */
class StageIconView(ctx: Context, private val st: StageOpt, private val ink: Int) : View(ctx) {
    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val r = RectF()
    private val accent = 0xFF00AAD2.toInt()

    override fun onMeasure(w: Int, h: Int) { val d = resources.displayMetrics.density; setMeasuredDimension((40 * d).toInt(), (28 * d).toInt()) }

    private fun car(c: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, alpha: Int, front: Char, rot: Float) {
        c.save(); if (rot != 0f) c.rotate(rot, x + w / 2, y + h / 2)
        pFill.color = color; pFill.alpha = alpha; r.set(x, y, x + w, y + h); c.drawRoundRect(r, 2f, 2f, pFill)
        pFill.color = 0xFFFFFFFF.toInt(); pFill.alpha = 255
        when (front) { 't' -> r.set(x + 1.5f, y + 1.5f, x + w - 1.5f, y + 3.7f); 'b' -> r.set(x + 1.5f, y + h - 3.7f, x + w - 1.5f, y + h - 1.5f); 'r' -> r.set(x + w - 3.7f, y + 1.5f, x + w - 1.5f, y + h - 1.5f); else -> r.setEmpty() }
        if (!r.isEmpty) c.drawRoundRect(r, 1f, 1f, pFill)
        c.restore()
    }

    private fun line(c: Canvas, path: String, w: Float, a: Float) { pLine.color = ink; pLine.alpha = (a * 255).toInt(); pLine.strokeWidth = w
        val p = Path(); val t = path.trim().split(Regex("\\s+")); var i = 0
        while (i < t.size) { val m = t[i]; val x = t[i + 1].toFloat(); val y = t[i + 2].toFloat(); if (m == "M") p.moveTo(x, y) else p.lineTo(x, y); i += 3 }
        c.drawPath(p, pLine) }

    override fun onDraw(c: Canvas) {
        val s = width / 44f; c.save(); c.scale(s, height / 30f)
        if (st.side == Side.L) { c.scale(-1f, 1f, 22f, 15f) }
        val cars = st.neighbors != Neigh.NONE; val pil = st.neighbors == Neigh.PILLAR; val gA = (0.35f * 255).toInt()
        when (st.kind) {
            Kind.PARALLEL -> {
                line(c, "M 1 27 L 43 27", 1.8f, .7f); line(c, "M 15 12 L 15 27 M 29 12 L 29 27", 1.4f, .6f)
                if (cars) { car(c, 1f, 14f, 13f, 11f, ink, gA, ' ', 0f); car(c, 30f, 14f, 13f, 11f, ink, gA, ' ', 0f) }
                car(c, 15.5f, 14f, 13f, 11f, accent, 255, 'r', 0f)
            }
            Kind.ANGLED -> {
                line(c, "M 1 27 L 11 3 M 12 27 L 22 3 M 23 27 L 33 3 M 34 27 L 44 3", 1.4f, .7f)
                if (cars) { car(c, 3.5f, 7f, 6f, 15f, ink, gA, ' ', 22.6f); car(c, 25.5f, 7f, 6f, 15f, ink, gA, ' ', 22.6f) }
                car(c, 14.5f, 7f, 6f, 15f, accent, 255, 't', 22.6f)
            }
            else -> {
                line(c, "M 4 27 L 4 3 L 40 3 L 40 27 M 16 3 L 16 27 M 28 3 L 28 27", 1.4f, .7f)
                if (cars) { car(c, 6f, 7f, 8f, 15f, ink, gA, ' ', 0f); car(c, 30f, 7f, 8f, 15f, ink, gA, ' ', 0f) }
                if (pil) { pFill.color = ink; pFill.alpha = 255; r.set(13.5f, 1f, 18.5f, 6f); c.drawRoundRect(r, 1f, 1f, pFill); r.set(25.5f, 1f, 30.5f, 6f); c.drawRoundRect(r, 1f, 1f, pFill) }
                car(c, 18f, 7f, 8f, 15f, accent, 255, if (st.kind == Kind.PERP_REV) 'b' else 't', 0f)
            }
        }
        c.restore()
    }
}
