package app.parkingcoach.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class CarModelTest {
    private val m = CarModel(CarSpec.AVANTE)

    @Test fun straightLine() {
        val p = m.step(Pose(0.0, 0.0, 0.0), 0.0, 1.0, 2.0)
        assertEquals(2.0, p.x, 1e-9); assertEquals(0.0, p.y, 1e-9)
    }

    @Test fun fullLockTurnsOnMinRadius() {
        // 핸들 끝까지(540°) → 바퀴 35° → R = 2.75/tan35° ≈ 3.93 m. 반원 돌면 heading 180°.
        val r = CarSpec.AVANTE.minTurnRadius
        var p = Pose(0.0, 0.0, 0.0)
        val arc = Math.PI * r
        repeat(1000) { p = m.step(p, 540.0, 1.0, arc / 1000) }
        assertEquals(Math.PI, abs(p.heading), 1e-3)  // 우회전이라 heading 은 −π
        assertEquals(2 * r, hypot(p.x, p.y), 1e-2)
    }

    @Test fun reverseWithRightSteerCurvesRight() {
        // 후진 + 우조향(핸들 +): 차 뒤가 오른쩝(차 기준)으로 돈다 → heading 이 왼쪽(+)으로 커진다
        var p = Pose(0.0, 0.0, 0.0)
        repeat(100) { p = m.step(p, 540.0, -1.0, 0.02) }
        assertTrue(p.x < 0); assertTrue(p.heading > 0)
    }

    @Test fun predictedPathStartsAtRearCorners() {
        val p = Pose(1.0, 2.0, 0.3)
        val (l, r) = m.predictedPath(p, 200.0, reverse = true)
        val (cl, cr) = m.rearCorners(p)
        assertEquals(cl.x, l.first().x, 1e-9); assertEquals(cr.y, r.first().y, 1e-9)
        assertEquals(41, l.size)
    }

    @Test fun scenarioStartIsCollisionFreeAndNotParked() {
        for (s in Scenario.all(CarSpec.AVANTE) + Scenario.all(CarSpec.GRANDEUR)) {
            val body = CarModel(CarSpec.AVANTE).body(s.start)
            assertTrue("${s.title} start collides", s.collides(body) == null)
            assertTrue("${s.title} start already parked", !s.parked(body, s.start.heading))
        }
    }

    @Test fun parkedDetectsCarInsideSlot() {
        val s = Scenario.build(CarSpec.AVANTE, Stages.ALL[5])   // 중급 후면 2.5×5.0
        // 칸 중심에 세로로 선 차(heading 90°), 뒷차축은 칸 중심에서 뒤쪽으로
        val pose = Pose(0.0, -2.5 - (CarSpec.AVANTE.wheelbase + CarSpec.AVANTE.frontOverhang - CarSpec.AVANTE.rearOverhang) / 2, Math.PI / 2)
        assertTrue(s.parked(m.body(pose), pose.heading))
        assertTrue("옆 칸으로 0.5 m 치우치면 주차 아님", !s.parked(m.body(pose.copy(x = 0.5)), pose.heading))
    }

    @Test fun obbIntersectsAndInside() {
        val a = OBB(Vec(0.0, 0.0), 0.0, 1.0, 1.0)
        assertTrue(a.intersects(OBB(Vec(1.5, 0.0), 0.7, 1.0, 0.5)))
        assertTrue(!a.intersects(OBB(Vec(5.0, 0.0), 0.0, 1.0, 1.0)))
        assertTrue(OBB(Vec(0.0, 0.0), 0.0, 0.5, 0.5).inside(a))
        assertTrue(abs(CarSpec.GRANDEUR.minTurnRadius - 4.13) < 0.05)
    }
}
