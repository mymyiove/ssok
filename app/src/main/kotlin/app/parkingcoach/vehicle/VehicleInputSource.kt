package app.parkingcoach.vehicle

/** 차량에서 읽는 입력. Pleos SDK 또는 시뮬레이터가 같은 모양으로 채운다. */
enum class Signal { OFF, LEFT, RIGHT }
enum class GearState { PARK, OTHER, UNKNOWN }

data class VehicleState(
    val steeringDeg: Float = 0f,     // 핸들 각도, + = 시계방향(우회전). Pleos SDK 값은 부호가 반대라 PleosVehicleSource.STEER_SIGN 에서 뒤집는다(2026-10-26 실측)
    val signal: Signal = Signal.OFF,  // 깜빡이 레버: RIGHT(레버 위)=D 전진, LEFT(레버 아래)=R 후진, OFF=N (GameEngine.tick 과 같다)
    val brake: Boolean = false,
    val driving: Boolean = false,    // 주행 제한(CarUxRestrictions requiresDistractionOptimization) — 참이면 가드만 보인다
    val brakeDepth: Float? = null,   // 밟은 깊이 0~1(살살 밟기). null 이면 brake 로(실차 SDK 는 밟음/뗌만 준다 → 0/1)
    val hazardLights: Boolean = false, // 비상등. AAOS HAZARD_LIGHTS_STATE(AaosHazardWatcher)로 읽는다 — Pleos TurnSignal 은 OFF/LEFT/RIGHT 뿐. 시뮬 패널 토글
    val hazardFromCar: Boolean = false, // 비상등을 차에서 읽고 있나. false 면(에뮬레이터 2.1.0: CAR_EXTERIOR_LIGHTS 미승인·속성 없음) 실차 모드에서도 화면 비상등 버튼을 쓴다
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
