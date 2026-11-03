package app.parkingcoach.game

import app.parkingcoach.sim.Kind
import app.parkingcoach.sim.Neigh
import app.parkingcoach.sim.Scenario
import app.parkingcoach.sim.Side
import app.parkingcoach.sim.StageOpt
import app.parkingcoach.vehicle.GearState
import app.parkingcoach.vehicle.Signal
import app.parkingcoach.vehicle.VehicleState
import kotlin.math.abs

/**
 * 시연 운전자(튜토리얼): 공식대로 스스로 주차한다. site/preview/sim.js 의 Demo 와 같은 규칙.
 * 핸들은 900°/s 로 돌리되, 연수 공식대로 '멈춰서 돌리고 움직인다'(끝까지 돌릴 때는 서서). 시작 자세는 공식 자리여야 한다(Scenario.build noStartJitter).
 */
class DemoDriver(private val sc: Scenario, private val opt: StageOpt, val mistake: String? = null) {   // mistake = "early": 실수 보기(sim.js Demo opt.mistake 와 같다)
    /** 조사 이/가: 받침 있으면 이, 없으면 가 (sim.js ga 와 같다) */
    private fun ga(w: String) = w + if ((w.last().code - 0xAC00) % 28 != 0) "이" else "가"
    private val T = opt.tier; private val side = if (opt.side == Side.L) -1.0 else 1.0
    private val R = if (opt.side == Side.L) "왼쪽" else "오른쪽"; private val L = if (opt.side == Side.L) "오른쪽" else "왼쪽"
    private var phase = 0; private var t0 = 0.0; private var hold = 0.6; private var arrived = false; private var stopHold = 0.0; private var mT: Double? = null; private var centered = false; private var centering = false
    var steer = 0.0; private set
    var caption = ""; private set
    var done = false; private set

    fun step(eng: GameEngine, dt: Double): VehicleState {
        val hd = sc.headingDiff(eng.pose.heading); val trav = eng.travelled
        var target = 0.0; var signal = Signal.OFF; var cap = caption
        if (eng.phase == Phase.PARKED) { done = true; caption = "쏙! 이제 직접 해 보세요"; return VehicleState(steeringDeg = 0f, signal = Signal.OFF, brake = true, gear = GearState.PARK, sourceName = "demo") }
        // 실수 보기(오류 훈련, 리서치 2026-10-27): 핸들을 25° 남기고 일찍 풀어 비뚤게 들어가는 모습을 한 번
        // 실수 보기는 닿기 15 cm 전에 멈춘다(어라운드뷰에서 차가 겹쳐 박혀 보이던 것, sim.js nearHit)
        val nearHit = mistake == "early" && mT != null && run { val b = eng.model.body(eng.pose); val cs = b.corners(); sc.obstacles.any { o -> minOf(cs.minOf { o.box.distTo(it) }, o.box.corners().minOf { b.distTo(it) }) < 0.15 } }
        if (mistake == "early" && (eng.phase == Phase.HIT || nearHit || (mT != null && trav - mT!! > 2.2))) { done = true; caption = "핸들을 일찍 풀면 이렇게 비뚤어져요. 이제 바르게 해 보세요"; return VehicleState(steeringDeg = 0f, signal = Signal.OFF, brake = true, hazardLights = true, gear = GearState.PARK, sourceName = "demo") }
        // 평행(앞뒤 차 있음): 곧게 편 뒤(phase 3) 뒤차에 붙었으면 D 로 살짝 앞으로 가 앞뒤 틈을 맞춘다(sim.js 와 같다)
        if (centering && !eng.inSlot) centered = true
        if (eng.inSlot && opt.kind == Kind.PARALLEL && opt.neighbors != Neigh.NONE && phase == 3 && !centered) {
            val cs = eng.model.body(eng.pose).corners(); val cars = sc.obstacles.filter { it.isCar }
            fun gapOf(ps: List<app.parkingcoach.sim.Vec>) = minOf(9.0, cars.minOfOrNull { o -> ps.minOf { o.box.distTo(it) } } ?: 9.0)
            val gf = gapOf(listOf(cs[0], cs[1])); val gr = gapOf(listOf(cs[2], cs[3]))
            if (gr < 0.45 && gf - gr > 0.06) { centering = true; caption = "앞뒤 틈을 맞춰요. D 로 살짝 앞으로"; steer = 0.0; val sg = GameEngine.sigOfGear("D")
                return VehicleState(steeringDeg = 0f, signal = sg, brake = GameEngine.sigOfGear(eng.gear) != sg, hazardLights = true, gear = GearState.PARK, sourceName = "demo") }
            centered = true }
        if (eng.inSlot && !(opt.kind == Kind.PARALLEL && opt.neighbors != Neigh.NONE && phase < 3)) { caption = "들어왔어요. 브레이크 밟고 N"; return VehicleState(steeringDeg = steer.toFloat(), signal = Signal.OFF, brake = true, hazardLights = true, gear = GearState.PARK, sourceName = "demo") }
        if (hold > 0) { hold -= dt; caption = "브레이크 밟고 비상등, D 넣고 살살 떼요"
            return VehicleState(steeringDeg = 0f, signal = GameEngine.sigOfGear("D"), brake = true, hazardLights = true, gear = GearState.PARK, sourceName = "demo") }
        eng.hazard?.let { caption = "${it.type.emoji} ${ga(it.type.label)} 먼저 브레이크, 지나가면 이어서"
            return VehicleState(steeringDeg = steer.toFloat(), signal = if (!arrived || sc.forward) GameEngine.sigOfGear("D") else GameEngine.sigOfGear("R"), brake = true, hazardLights = true, gear = GearState.PARK, sourceName = "demo") }
        if (phase == 0 && !arrived) {
            if (eng.pose.x < sc.formulaX) { caption = when { opt.kind == Kind.PERP_REV -> "D 로 천천히, 뒷차축이 옆 칸 지날 때까지"; opt.kind == Kind.PARALLEL -> "D 로 앞차 옆에, 뒷바퀴가 뒷범퍼 선까지"; opt.kind == Kind.ANGLED -> "D 로 천천히, 칸 입구가 미러 옆까지"; opt.neighbors == Neigh.NONE -> "D 로 천천히, 칸 입구 선이 미러 옆까지"; else -> "D 로 바깥쪽에 붙여, 운전석이 칸 중앙까지" }
                steer = 0.0; return VehicleState(steeringDeg = 0f, signal = GameEngine.sigOfGear("D"), brake = false, hazardLights = true, gear = GearState.PARK, sourceName = "demo") }
            arrived = true; stopHold = 0.5
        }
        if (stopHold > 0) { stopHold -= dt; caption = if (sc.forward) "여기! 핸들을 끝까지 돌려요" else "여기서 멈추고 R, 서서 핸들 끝까지"
            return VehicleState(steeringDeg = steer.toFloat(), signal = if (sc.forward) GameEngine.sigOfGear("D") else GameEngine.sigOfGear("R"), brake = true, hazardLights = true, gear = GearState.PARK, sourceName = "demo") }
        when (opt.kind) {
            Kind.PERP_REV -> { signal = GameEngine.sigOfGear("R")
                if (phase == 0) phase = 1
                if (phase == 1) { target = 540 * side; cap = "뒷차축이 지났어요. 핸들 ${R} 끝까지"; if (hd < Math.toRadians(if (mistake == "early") 25.0 else 0.7)) { phase = 2; if (mistake == "early") mT = trav } }
                if (phase == 2) { target = 0.0; cap = if (mistake == "early") "25° 남았는데 핸들을 풀어요. 일찍 푼 실수" else "나란해졌어요. 핸들 가운데, 직진 후진" }
            }
            Kind.ANGLED -> { signal = GameEngine.sigOfGear("D")
                if (phase == 0) phase = 1
                if (phase == 1) { target = 540 * side; cap = "칸 입구가 미러 옆! 핸들 ${R} 끝까지"; if (hd < Math.toRadians(0.7)) phase = 2 }
                if (phase == 2) { target = 0.0; cap = "나란해졌어요(45°). 핸들 가운데, 직진" }
            }
            Kind.PERP_FWD -> if (opt.neighbors == Neigh.NONE) { signal = GameEngine.sigOfGear("D")
                if (phase == 0) phase = 1
                if (phase == 1) { target = 540 * side; cap = "칸 입구가 미러 옆! 핸들 ${R} 끝까지"; if (hd < Math.toRadians(0.7)) phase = 2 }
                if (phase == 2) { target = 0.0; cap = "나란해졌어요. 핸들 가운데, 앞 벽 전까지" }
            } else { signal = GameEngine.sigOfGear("D"); val h = -eng.pose.heading * side
                if (phase == 0) phase = 1
                if (phase == 1) { target = 540 * side; cap = "운전석이 칸 중앙을 지났어요. 핸들 ${R}으로 끝까지, 35°까지만."; if (h >= Math.toRadians(35.0)) { phase = 2; t0 = trav } }
                if (phase == 2) { target = -540 * side; signal = GameEngine.sigOfGear("R"); cap = "35°! 핸들 ${L} 끝까지, R 로 2 m 빼요"; if (trav - t0 >= 2.0) phase = 3 }
                if (phase == 3) { target = 540 * side; cap = "다시 D, 핸들 ${R} 끝까지"; if (hd < Math.toRadians(0.7)) phase = 4 }
                if (phase == 4) { target = 0.0; cap = "나란해졌어요. 핸들 가운데, 앞 벽 전까지" }
            }
            Kind.PARALLEL -> { signal = GameEngine.sigOfGear("R")
                if (phase == 0) phase = 1
                if (phase == 1) { target = 540 * side; cap = "핸들 ${R} 끝까지, 45°까지 후진"; if (hd > Math.toRadians(44.5)) phase = 2 }
                if (phase == 2) { target = -540 * side; cap = "45°! 핸들 ${L} 끝까지, 계속 후진"; if (hd < Math.toRadians(0.7)) phase = 3 }
                if (phase == 3) { target = 0.0; cap = "연석과 나란해요. 핸들 가운데, 브레이크" }
            }
        }
        var brake = false
        eng.hazard?.let { brake = true; cap = "${it.type.emoji} ${ga(it.type.label)} 먼저 브레이크, 지나가면 이어서" }
        val maxStep = 900 * dt; val gap = target - steer; steer += gap.coerceIn(-maxStep, maxStep)
        if (abs(gap) > 8) brake = true   // 핸들이 목표에 거의 닿을 때까지는 서서 돌린다(옆 차가 큰 차일 때 여유가 8 cm 뿐)
        if (GameEngine.sigOfGear(eng.gear) != signal) brake = true   // 기어를 바꿀 땐 밟고 바꾼다(실차 시프트 록)
        caption = cap
        return VehicleState(steeringDeg = steer.toFloat(), signal = signal, brake = brake, hazardLights = true, gear = GearState.PARK, sourceName = "demo")
    }
}
