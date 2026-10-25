package app.parkingcoach.vehicle

/** 차량에서 읽는 입력. Pleos SDK 또는 시뮬레이터가 같은 모양으로 채운다. */
enum class Signal { OFF, LEFT, RIGHT }
enum class GearState { PARK, OTHER, UNKNOWN }

data class VehicleState(
    val steeringDeg: Float = 0f,     // 핸들 각도, + = 시계방향(우회전). 부호는 1주차 실측 뒤 steeringSign 으로 보정
    val signal: Signal = Signal.OFF,  // 깜빡이 레버: LEFT=전진, RIGHT=후진 (PM 2026-10-24)
    val brake: Boolean = false,
    val brakeDepth: Float? = null,   // 밟은 깊이 0~1(살살 밟기). null 이면 brake 로(실차 SDK 는 밟음/뗌만 준다 → 0/1)
    val hazardLights: Boolean = false, // 비상등. AAOS HAZARD_LIGHTS_STATE(AaosHazardWatcher)로 읽는다 — Pleos TurnSignal 은 OFF/LEFT/RIGHT 뿐. 시뮬 패널 토글
    val gear: GearState = GearState.UNKNOWN,
    val connected: Boolean = false,
    val sourceName: String = "none",
    val lastError: String? = null,
)

interface VehicleInputSource {
    val state: VehicleState
    fun start(onUpdate: (VehicleState) -> Unit)
    fun stop()
}
