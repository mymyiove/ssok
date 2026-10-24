package app.parkingcoach.game

import app.parkingcoach.sim.CarModel
import app.parkingcoach.sim.CarSpec
import app.parkingcoach.sim.OBB
import app.parkingcoach.sim.Pose
import app.parkingcoach.sim.Scenario
import app.parkingcoach.vehicle.GearState
import app.parkingcoach.vehicle.Signal
import app.parkingcoach.vehicle.VehicleState
import kotlin.math.abs

enum class Phase { READY, DRIVING, PARKED, HIT }

data class TracePoint(val t: Double, val steeringDeg: Double, val pose: Pose, val v: Double)

data class GameSnapshot(
    val phase: Phase,
    val pose: Pose,
    val body: OBB,
    val steeringDeg: Double,
    val v: Double,
    val hint: String,
    val hits: Int,
    val elapsed: Double,
    val coachStep: Int,
    val reverse: Boolean,
)

/**
 * 규칙(PM 2026-10-24): 깜빡이 LEFT = 전진, RIGHT = 후진, OFF = 정지, 브레이크 = 정지. 속도 고정.
 * 기어가 P 가 아니면 멈춘다(정차 전용 앱 정책).
 */
class GameEngine(spec: CarSpec, steeringSign: Double = 1.0) {
    val model = CarModel(spec, steeringSign)
    var scenario: Scenario = Scenario.perpendicularRight(spec); private set
    var pose: Pose = scenario.start; private set
    var phase = Phase.READY; private set
    var hits = 0; private set
    var elapsed = 0.0; private set
    val trace = ArrayList<TracePoint>()
    private var coachStep = 0
    private var hint = ""
    private var lastV = 0.0
    private var lastReverse = true

    val speed = 0.6  // m/s ≈ 2.2 km/h (주차 속도 고정)
    val sessionLimitSec = 300.0  // EPS 과부하·드라이 스티어링 고려: 5분

    fun load(s: Scenario) { scenario = s; reset() }
    fun reset() { pose = scenario.start; phase = Phase.READY; hits = 0; elapsed = 0.0; trace.clear(); coachStep = 0; hint = scenario.coach.first(); lastV = 0.0 }

    fun tick(vs: VehicleState, dt: Double): GameSnapshot {
        if (phase == Phase.PARKED) return snapshot(vs)
        if (vs.gear != GearState.PARK && vs.sourceName == "pleos") { hint = "기어를 P에 두면 계속할 수 있어요"; lastV = 0.0; return snapshot(vs) }
        val v = when {
            vs.brake -> 0.0
            vs.signal == Signal.LEFT -> speed
            vs.signal == Signal.RIGHT -> -speed
            else -> 0.0
        }
        if (v != 0.0) {
            if (phase == Phase.READY) phase = Phase.DRIVING
            val next = model.step(pose, vs.steeringDeg.toDouble(), v, dt)
            val body = model.body(next)
            val hit = scenario.collides(body)
            if (hit != null) {
                hits++; phase = Phase.HIT; hint = "${hit.label}에 닿았어요. 반대로 빼서 다시."
            } else {
                pose = next; phase = Phase.DRIVING
                elapsed += dt
                trace += TracePoint(elapsed, vs.steeringDeg.toDouble(), pose, v)
                lastReverse = v < 0
                advanceCoach(vs)
                if (scenario.parked(body, pose.heading)) { phase = Phase.PARKED; hint = "쏙! ${hitsText()}" }
            }
        } else if (phase == Phase.HIT) {
            // 멈춘 상태에서 반대 방향으로 움직이면 다시 DRIVING 으로
            phase = Phase.DRIVING
        }
        lastV = v
        if (elapsed > sessionLimitSec) hint = "5분이 지났어요. 핸들과 타이어를 위해 오늘은 여기까지."
        return snapshot(vs)
    }

    private fun hitsText() = if (hits == 0) "한 번도 부딪히지 않았어요." else "${hits}번 부딪혔어요. 리플레이에서 어디서 각을 잘못 잡았나 보세요."

    /** 아주 단순한 코칭 진행: 조향각·자세 조건으로 다음 문장으로. 실차 기준점은 2주차에 보정. */
    private fun advanceCoach(vs: VehicleState) {
        val steer = abs(vs.steeringDeg)
        when (coachStep) {
            0 -> if (vs.signal == Signal.RIGHT) coachStep = 1
            1 -> if (steer > 400) coachStep = 2
            2 -> if (steer < 200 && elapsed > 2) coachStep = 3
            else -> {}
        }
        hint = scenario.coach.getOrElse(coachStep) { scenario.coach.last() }
    }

    private fun snapshot(vs: VehicleState) = GameSnapshot(
        phase, pose, model.body(pose), vs.steeringDeg.toDouble(), lastV, hint, hits, elapsed, coachStep,
        reverse = if (lastV == 0.0) lastReverse else lastV < 0)
}
