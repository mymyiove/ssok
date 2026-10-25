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
class DemoDriver(private val sc: Scenario, private val opt: StageOpt) {
    private val T = opt.tier; private val side = if (opt.side == Side.L) -1.0 else 1.0
    private val R = if (opt.side == Side.L) "왼쪽" else "오른쪽"; private val L = if (opt.side == Side.L) "오른쪽" else "왼쪽"
    private var phase = 0; private var t0 = 0.0
    var steer = 0.0; private set
    var caption = ""; private set
    var done = false; private set

    fun step(eng: GameEngine, dt: Double): VehicleState {
        val hd = sc.headingDiff(eng.pose.heading); val trav = eng.travelled
        var target = 0.0; var signal = Signal.OFF; var cap = caption
        val offsetCap = if (T.offset > 0) "시작 자리가 공식 자리에서 ${T.offset} m 어긋났어요. 먼저 ${T.offset} m 똑바로 맞춥니다." else null
        if (eng.phase == Phase.PARKED) { done = true; caption = "쏙! 이렇게 들어갑니다. 이제 직접 해 보세요."; return VehicleState(steeringDeg = 0f, signal = Signal.OFF, brake = true, gear = GearState.PARK, sourceName = "demo") }
        when (opt.kind) {
            Kind.PERP_REV -> { signal = Signal.RIGHT
                if (phase == 0) { cap = offsetCap ?: "오른쪽 깜빡이(후진). 핸들은 가운데, 뒷차축이 옆 칸을 지날 때까지."; if (trav >= T.offset) phase = 1 }
                if (phase == 1) { target = 540 * side; cap = "뒷차축이 옆 칸을 지났어요. 핸들을 ${R}으로 끝까지. 노란선이 칸 안으로 들어오는 걸 보세요."; if (hd < Math.toRadians(0.7)) phase = 2 }
                if (phase == 2) { target = 0.0; cap = "차체가 칸과 나란해졌어요. 핸들 가운데, 빨간선이 뒤 벽에 닿기 전까지 직진 후진." }
            }
            Kind.ANGLED -> { signal = Signal.LEFT
                if (phase == 0) { cap = offsetCap ?: "왼쪽 깜빡이(전진). 핸들 가운데로 칸 앞까지."; if (trav >= 1.0 + T.offset) phase = 1 }
                if (phase == 1) { target = 540 * side; cap = "칸 입구가 $R 사이드미러 옆에 왔어요. 핸들 ${R}으로 끝까지."; if (hd < Math.toRadians(0.7)) phase = 2 }
                if (phase == 2) { target = 0.0; cap = "칸과 나란해졌어요(45°). 핸들 가운데, 앞 벽 0.5 m 전까지 직진." }
            }
            Kind.PERP_FWD -> if (opt.neighbors == Neigh.NONE) { signal = Signal.LEFT
                if (phase == 0) { cap = offsetCap ?: "왼쪽 깜빡이(전진). 핸들 가운데로 칸 앞까지."; if (trav >= 1.0 + T.offset) phase = 1 }
                if (phase == 1) { target = 540 * side; cap = "칸 입구가 $R 사이드미러 옆에 왔어요. 핸들 ${R}으로 끝까지."; if (hd < Math.toRadians(0.7)) phase = 2 }
                if (phase == 2) { target = 0.0; cap = "나란해졌어요. 핸들 가운데, 앞 벽 0.5 m 전까지 직진." }
            } else { signal = Signal.LEFT; val h = -eng.pose.heading * side
                if (phase == 0) { cap = "전면 주차는 옆 차가 있으면 한 번에 안 들어가요. 바깥쪽으로 붙여서 칸을 지나칩니다."; if (trav >= 3.5 + T.offset) phase = 1 }
                if (phase == 1) { target = 540 * side; cap = "운전석이 칸 중앙을 지났어요. 핸들 ${R}으로 끝까지, 40°까지만."; if (h >= Math.toRadians(40.0)) { phase = 2; t0 = trav } }
                if (phase == 2) { target = -540 * side; signal = Signal.RIGHT; cap = "40° 꺾였어요. 핸들 ${L}으로 끝까지 바꾸고 오른쪽 깜빡이(후진)로 2 m 뺍니다."; if (trav - t0 >= 2.0) phase = 3 }
                if (phase == 3) { target = 540 * side; cap = "다시 왼쪽 깜빡이(전진), 핸들 ${R}으로 끝까지. 이번엔 앞 모서리가 옆 차를 비껴 들어가요."; if (hd < Math.toRadians(0.7)) phase = 4 }
                if (phase == 4) { target = 0.0; cap = "나란해졌어요. 핸들 가운데, 앞 벽 0.5 m 전까지 직진." }
            }
            Kind.PARALLEL -> { signal = Signal.RIGHT
                if (phase == 0) { cap = offsetCap ?: "뒷바퀴가 앞차 뒷범퍼 선에 있어요. 오른쪽 깜빡이(후진)."; if (trav >= T.offset) phase = 1 }
                if (phase == 1) { target = 540 * side; cap = "핸들 ${R}으로 끝까지. 차체가 45°가 될 때까지 후진."; if (hd > Math.toRadians(44.5)) phase = 2 }
                if (phase == 2) { target = -540 * side; cap = "45°가 됐어요. 핸들 ${L}으로 끝까지 바꿔서 계속 후진. 앞 범퍼가 앞차를 스치듯 지나갑니다."; if (hd < Math.toRadians(0.7)) phase = 3 }
                if (phase == 3) { target = 0.0; cap = "연석과 나란해졌어요. 핸들 가운데, 칸 가운데에서 브레이크." }
            }
        }
        var brake = false
        eng.hazard?.let { brake = true; cap = "${it.type.emoji} ${it.type.label}이 보이면 먼저 브레이크. 지나가면 이어서." }
        val maxStep = 900 * dt; val gap = target - steer; steer += gap.coerceIn(-maxStep, maxStep)
        if (abs(gap) > 30) brake = true
        caption = cap
        return VehicleState(steeringDeg = steer.toFloat(), signal = signal, brake = brake, gear = GearState.PARK, sourceName = "demo")
    }
}
