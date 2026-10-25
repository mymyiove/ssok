package app.parkingcoach.game

import app.parkingcoach.sim.CarModel
import app.parkingcoach.sim.CarSpec
import app.parkingcoach.sim.HazType
import app.parkingcoach.sim.OBB
import app.parkingcoach.sim.Pose
import app.parkingcoach.sim.Scenario
import app.parkingcoach.sim.Vec
import app.parkingcoach.vehicle.GearState
import app.parkingcoach.vehicle.Signal
import app.parkingcoach.vehicle.VehicleState
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class Phase { READY, DRIVING, PARKED, HIT }

data class TracePoint(val t: Double, val steeringDeg: Double, val pose: Pose, val v: Double)

/** 돌발(사람·아이·카트): 옆에서 나와 경로 한가운데서 잠깐 멈춰 선 뒤 지나간다. */
class Hazard(val type: HazType, val origin: Vec, val nrm: Vec, side: Double) {
    var pos: Vec = origin + nrm * (type.lat * side)
    val vel: Vec = nrm * (-type.speed * side)
    var crossed = false
    var pauseLeft = type.pause
    var life = (2 * type.lat + 1.0) / type.speed + type.pause
    var wasStopped = false
    fun advance(dt: Double) {
        life -= dt
        if (!crossed) {
            val next = pos + vel * dt
            val s0 = (pos - origin).dot(nrm); val s1 = (next - origin).dot(nrm)
            if (Math.signum(s0) != Math.signum(s1)) { crossed = true; pos = origin } else pos = next
        } else if (pauseLeft > 0) pauseLeft -= dt
        else pos += vel * dt
    }
}

data class HazStats(var total: Int = 0, var hit: Int = 0, var avoided: Int = 0)

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
    val coachCount: Int,
    val reverse: Boolean,
    val hazard: Hazard?,
    val hazStats: HazStats,
    val flash: String?,
)

/**
 * 규칙(PM 2026-10-24): 깜빡이 LEFT = 전진, RIGHT = 후진, OFF = 정지, 브레이크 = 정지. 속도 고정.
 * 기어가 P 가 아니면 멈춘다(정차 전용 앱 정책). site/preview/sim.js Engine 과 같은 규칙.
 */
class GameEngine(spec: CarSpec, steeringSign: Double = 1.0, private val seed: Long = 7L) {
    val model = CarModel(spec, steeringSign)
    var scenario: Scenario = Scenario.build(spec, app.parkingcoach.sim.Stages.ALL[0]); private set
    var pose: Pose = scenario.start; private set
    var phase = Phase.READY; private set
    var hits = 0; private set
    var elapsed = 0.0; private set
    var travelled = 0.0; private set
    val trace = ArrayList<TracePoint>()
    var hazard: Hazard? = null; private set
    val hazStats = HazStats()
    private var hazPlan = ArrayList<Pair<Double, HazType>>()
    private var rng = Random(seed)
    private var coachStep = 0
    private var stepAt = 0.0
    private var hint = ""
    private var lastV = 0.0
    private var lastReverse = true
    private var flash: String? = null

    val speed = 0.6  // m/s ≈ 2.2 km/h (주차 속도 고정)
    val sessionLimitSec = 300.0  // EPS 과부하·드라이 스티어링 고려: 5분

    fun load(s: Scenario) { scenario = s; reset() }
    fun reset() {
        pose = scenario.start; phase = Phase.READY; hits = 0; elapsed = 0.0; travelled = 0.0; trace.clear()
        coachStep = 0; stepAt = 0.0; hint = scenario.coach.first().text; lastV = 0.0; lastReverse = !scenario.forward
        hazard = null; hazStats.total = 0; hazStats.hit = 0; hazStats.avoided = 0; rng = Random(seed); hazPlan.clear()
        val types = HazType.values()
        for (i in 0 until scenario.hazards) hazPlan += (1.2 + rng.nextDouble() * 1.3 + i * 2.2) to (scenario.hazType ?: types[rng.nextInt(types.size)])
    }

    private fun spawnHazard(type: HazType, v: Double) {
        val rev = v < 0; val dir = if (rev) -1.0 else 1.0
        val fwd = Vec(cos(pose.heading) * dir, sin(pose.heading) * dir); val nrm = Vec(-sin(pose.heading), cos(pose.heading))
        val side = if (rng.nextDouble() < 0.5) 1.0 else -1.0
        val origin = model.bumper(pose, rev) + fwd * type.ahead
        hazard = Hazard(type, origin, nrm, side)
        hazStats.total++; hint = "${type.emoji} ${type.label}! 멈춰요."; flash = type.label
    }

    fun tick(vs: VehicleState, dt: Double): GameSnapshot {
        flash = null
        if (phase == Phase.PARKED) return snapshot(vs)
        if (vs.gear != GearState.PARK && vs.sourceName == "pleos") { hint = "기어를 P에 두면 계속할 수 있어요"; lastV = 0.0; return snapshot(vs) }
        val v = when {
            vs.brake -> 0.0
            vs.signal == Signal.LEFT -> speed
            vs.signal == Signal.RIGHT -> -speed
            else -> 0.0
        }
        // 돌발: 움직이는 중 계획된 거리에서 등장, 옆에서 가로질러 와 경로 위에 잠깐 선다
        val hz = hazard
        if (hz != null) {
            hz.advance(dt)
            val d = model.body(pose).distTo(hz.pos)
            if (d <= hz.type.r + 0.1) {
                if (v != 0.0) { hits++; hazStats.hit++; phase = Phase.HIT; hint = "${hz.type.emoji} ${hz.type.label}과 닿았어요! 사람이 보이면 먼저 멈춰요."; hazard = null; lastV = 0.0; return snapshot(vs) }
            } else if (v == 0.0 && d < 3.0) hz.wasStopped = true
            if (hazard != null && hz.life <= 0) { hazard = null; hazStats.avoided++; hint = if (hz.wasStopped) "잘 멈췄어요. 이어서 가세요." else "지나갔어요. 다음엔 보이는 순간 멈춰요."; stepAt = elapsed }
        } else if (v != 0.0 && hazPlan.isNotEmpty() && travelled >= hazPlan[0].first) spawnHazard(hazPlan.removeAt(0).second, v)

        if (v != 0.0) {
            if (phase == Phase.READY) phase = Phase.DRIVING
            val next = model.step(pose, vs.steeringDeg.toDouble(), v, dt)
            val body = model.body(next)
            val hit = scenario.collides(body)
            if (hit != null) {
                if (phase != Phase.HIT) hits++
                phase = Phase.HIT; hint = "${hit.label}에 닿았어요. 반대로 빼서 다시."
            } else {
                pose = next; phase = Phase.DRIVING
                elapsed += dt; travelled += abs(v) * dt
                trace += TracePoint(elapsed, vs.steeringDeg.toDouble(), pose, v)
                lastReverse = v < 0
                if (hazard == null) advanceCoach(vs)
                if (scenario.parked(body, pose.heading)) { phase = Phase.PARKED; hint = doneText() }
            }
        } else if (phase == Phase.HIT) {
            phase = Phase.DRIVING   // 멈춘 상태에서 반대 방향으로 움직이면 다시 DRIVING 으로
        }
        lastV = v
        if (elapsed > sessionLimitSec) hint = "5분이 지났어요. 핸들과 타이어를 위해 오늘은 여기까지."
        return snapshot(vs)
    }

    fun doneText(): String {
        val base = if (hits == 0) "쏙! 한 번도 부딪히지 않았어요." else "쏙! ${hits}번 부딪혔어요. 리플레이에서 어디서 각을 잘못 잡았나 보세요."
        val h = hazStats
        return if (h.total > 0) "$base 돌발 ${h.total}회 중 ${h.avoided}회 멈춤${if (h.hit > 0) " · ${h.hit}회 사고" else ""}." else base
    }

    /** 코칭 진행: 단계마다 '떠나는 조건'(Scenario.CoachStep.cond)이 맞으면 다음 문장으로. */
    private fun advanceCoach(vs: VehicleState) {
        val c = scenario.coach; val cond = c.getOrNull(coachStep)?.cond
        val st = vs.steeringDeg.toDouble(); val hd = scenario.headingDiff(pose.heading)
        val go = when (cond) {
            'R' -> vs.signal == Signal.RIGHT; 'F' -> vs.signal == Signal.LEFT
            '>' -> st > 400; '<' -> st < -400
            '0' -> abs(st) < 200 && elapsed - stepAt > 1.0
            'A' -> hd < Math.toRadians(12.0); 'D' -> hd > Math.toRadians(35.0) && hd < Math.toRadians(55.0)
            else -> false
        }
        if (go && coachStep < c.size - 1) { coachStep++; stepAt = elapsed }
        hint = c[minOf(coachStep, c.size - 1)].text
    }

    private fun snapshot(vs: VehicleState) = GameSnapshot(
        phase, pose, model.body(pose), vs.steeringDeg.toDouble(), lastV, hint, hits, elapsed, coachStep, scenario.coach.size,
        reverse = if (lastV == 0.0) lastReverse else lastV < 0, hazard = hazard, hazStats = hazStats, flash = flash)
}
