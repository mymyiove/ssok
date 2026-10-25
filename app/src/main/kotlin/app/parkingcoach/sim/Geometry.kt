package app.parkingcoach.sim

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class Vec(val x: Double, val y: Double) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y)
    operator fun minus(o: Vec) = Vec(x - o.x, y - o.y)
    operator fun times(k: Double) = Vec(x * k, y * k)
    fun dot(o: Vec) = x * o.x + y * o.y
    fun rot(theta: Double) = Vec(x * cos(theta) - y * sin(theta), x * sin(theta) + y * cos(theta))
}

/** 축에 정렬되지 않은 직사각형(차·장애물). center, heading(rad), half extents. */
data class OBB(val c: Vec, val heading: Double, val hx: Double, val hy: Double) {
    fun corners(): List<Vec> {
        val ax = Vec(cos(heading), sin(heading)); val ay = Vec(-sin(heading), cos(heading))
        return listOf(
            c + ax * hx + ay * hy, c + ax * hx - ay * hy, c - ax * hx - ay * hy, c - ax * hx + ay * hy)
    }
    private fun axes() = listOf(Vec(cos(heading), sin(heading)), Vec(-sin(heading), cos(heading)))
    /** 분리축 정리(SAT). */
    fun intersects(o: OBB): Boolean {
        val axes = axes() + o.axes()
        val a = corners(); val b = o.corners()
        for (ax in axes) {
            val pa = a.map { it.dot(ax) }; val pb = b.map { it.dot(ax) }
            if (pa.max() < pb.min() || pb.max() < pa.min()) return false
        }
        return true
    }
    /** 점과 이 상자 사이 거리(안이면 0). 돌발(사람·카트) 충돌 판정용. */
    fun distTo(p: Vec): Double {
        val ax = axes(); val d = p - c
        val dx = (abs(d.dot(ax[0])) - hx).coerceAtLeast(0.0); val dy = (abs(d.dot(ax[1])) - hy).coerceAtLeast(0.0)
        return kotlin.math.hypot(dx, dy)
    }
    /** 이 상자가 other(축 정렬 가정 아님) 안에 완전히 들어가나. */
    fun inside(o: OBB): Boolean {
        val ax = o.axes()
        return corners().all { p ->
            val d = p - o.c
            abs(d.dot(ax[0])) <= o.hx + 1e-9 && abs(d.dot(ax[1])) <= o.hy + 1e-9
        }
    }
}
