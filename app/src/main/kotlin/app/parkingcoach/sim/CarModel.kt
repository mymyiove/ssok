package app.parkingcoach.sim

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** 차 자세: 뒷차축 중점 (x,y) m, heading rad (0 = +x). */
data class Pose(val x: Double, val y: Double, val heading: Double)

/**
 * 저속 운동학 자전거 모델(슬립 0). 주차 속도에서 유효.
 * 핸들각 φ → 바퀴각 δ = φ / 조향비(최대 ±maxWheelDeg) → 회전반경 R = L / tan δ.
 */
class CarModel(val spec: CarSpec, val steeringSign: Double = 1.0) {

    fun wheelAngleRad(steeringDeg: Double): Double {
        // 핸들 + = 시계방향(우회전) → 자전거 모델의 δ 는 좌회전이 + 이므로 부호를 뒤집는다
        val d = (-steeringDeg * steeringSign / spec.steeringRatio).coerceIn(-spec.maxWheelDeg, spec.maxWheelDeg)
        return Math.toRadians(d)
    }

    /** v: m/s (+전진, −후진), dt: s */
    fun step(p: Pose, steeringDeg: Double, v: Double, dt: Double): Pose {
        val delta = wheelAngleRad(steeringDeg)
        val ds = v * dt
        return if (abs(delta) < 1e-6) {
            Pose(p.x + ds * cos(p.heading), p.y + ds * sin(p.heading), p.heading)
        } else {
            val r = spec.wheelbase / tan(delta)           // 뒷차축 기준 회전반경 (부호 포함, 좌회전 +)
            val dth = ds / r
            val cx = p.x - r * sin(p.heading); val cy = p.y + r * cos(p.heading)
            val th = p.heading + dth
            Pose(cx + r * sin(th), cy - r * cos(th), th)
        }
    }

    /** 차체 OBB. 뒷차축 중점에서 앞으로 (wheelbase + frontOverhang − rearOverhang)/2 만큼이 기하 중심. */
    fun body(p: Pose): OBB {
        val centerAhead = (spec.wheelbase + spec.frontOverhang - spec.rearOverhang) / 2.0
        val c = Vec(p.x + centerAhead * cos(p.heading), p.y + centerAhead * sin(p.heading))
        return OBB(c, p.heading, spec.length / 2.0, spec.width / 2.0)
    }

    /**
     * 예상 경로(가이드라인). 현재 핸들각으로 후진(또는 전진)할 때 뒷범퍼 좌/우 모서리가 그릴 궤적을
     * 경로 길이 range(m)까지 n개 점으로. 현대 후방카메라의 '노란선'과 같은 기하.
     */
    fun predictedPath(p: Pose, steeringDeg: Double, reverse: Boolean, range: Double = 5.0, n: Int = 40): Pair<List<Vec>, List<Vec>> {
        val left = ArrayList<Vec>(n); val right = ArrayList<Vec>(n)
        var q = p
        val v = if (reverse) -1.0 else 1.0
        val dt = range / n
        for (i in 0..n) {
            val (l, r) = rearCorners(q)
            left += l; right += r
            q = step(q, steeringDeg, v, dt)
        }
        return left to right
    }

    /** 뒷범퍼 좌/우 모서리 좌표 */
    fun rearCorners(p: Pose): Pair<Vec, Vec> {
        val back = Vec(p.x - spec.rearOverhang * cos(p.heading), p.y - spec.rearOverhang * sin(p.heading))
        val side = Vec(-sin(p.heading), cos(p.heading)) * (spec.width / 2.0)
        return (back + side) to (back - side)
    }
}
