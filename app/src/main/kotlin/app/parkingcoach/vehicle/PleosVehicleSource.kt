package app.parkingcoach.vehicle

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener

/**
 * Pleos Connect Vehicle SDK 2.3.2 읽기 전용 바인딩.
 * 권한: CAR_DRIVE(조향각·브레이크·기어), CAR_LIGHTS(방향지시등), AAOS CAR_POWERTRAIN(기어)·CAR_EXTERIOR_LIGHTS(비상등). 제어 API 는 쓰지 않는다.
 * 콜백은 IPC 비동기 → 메인 스레드로 올려서 전달.
 */
class PleosVehicleSource(private val context: Context) : VehicleInputSource {
    override var state = VehicleState(sourceName = "pleos")
        private set
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var onUpdate: ((VehicleState) -> Unit)? = null
    private var aaosGear: AaosGearWatcher? = null
    private var aaosHazard: AaosHazardWatcher? = null

    private fun push(mutate: VehicleState.() -> VehicleState) {
        main.post { state = state.mutate(); onUpdate?.invoke(state) }
    }

    private val steerL = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(deg: Float?) { push { copy(steeringDeg = deg?.let { it * STEER_SIGN } ?: steeringDeg, connected = true) } }
        override fun onFailed(e: Exception) { push { copy(lastError = "steering: ${e.message}") } }
    }
    private val signalL = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(v: TurnSignalValue?) {
            push { copy(signal = when (v) { TurnSignalValue.LEFT -> Signal.LEFT; TurnSignalValue.RIGHT -> Signal.RIGHT; else -> Signal.OFF }, connected = true) }
        }
        override fun onFailed(e: Exception) { push { copy(lastError = "turnSignal: ${e.message}") } }
    }
    private val brakeL = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(on: Boolean?) { push { copy(brake = on == true, connected = true) } }
        override fun onFailed(e: Exception) { push { copy(lastError = "brake: ${e.message}") } }
    }
    private val gearL = object : GearSelectionListener {
        override fun onSelected(g: VehicleGear?) { push { copy(gear = if (g == VehicleGear.GEAR_PARK) GearState.PARK else GearState.OTHER, connected = true) } }
        override fun onFailed(e: Exception) { push { copy(lastError = "gear: ${e.message}") } }
    }

    override fun start(onUpdate: (VehicleState) -> Unit) {
        this.onUpdate = onUpdate
        try {
            val v = Vehicle(context).also { it.initialize() }
            vehicle = v
            // 초기값 한 번 읽고 구독. 서비스 바인딩이 비동기라 실패하면 onFailed 로 들어온다.
            VehicleAccess.steering(v).getCurrentSteeringWheelAngle({ d -> push { copy(steeringDeg = d?.let { it * STEER_SIGN } ?: steeringDeg, connected = true) } }, { e -> push { copy(lastError = e.message) } })
            VehicleAccess.gear(v).getCurrentGearSelectionState({ g -> gearL.onSelected(g) }, { e -> push { copy(lastError = e.message) } })
            VehicleAccess.steering(v).registerSteeringWheelAngle(steerL)
            VehicleAccess.turnSignal(v).registerTurnSignalState(signalL)
            VehicleAccess.brake(v).registerBrakePedalStatus(brakeL)
            VehicleAccess.gear(v).registerGearSelection(gearL)
        } catch (e: Exception) {
            push { copy(connected = false, lastError = "init: ${e.message}") }
        }
        // 기어는 AAOS 표준 속성이 정본(Pleos Gear 는 deprecated). 둘 다 구독해 먼저 오는 값을 쓴다.
        aaosGear = AaosGearWatcher(context, { g -> push { copy(gear = g, connected = true) } }, { msg -> push { copy(lastError = msg) } }).also { it.start() }
        // 비상등은 AAOS HAZARD_LIGHTS_STATE 로(SDK TurnSignal 은 OFF/LEFT/RIGHT 뿐). 지원 안 하는 차는 오류 한 줄 남기고 넘어간다.
        aaosHazard = AaosHazardWatcher(context, { on -> push { copy(hazardLights = on, connected = true) } }, { msg -> push { copy(lastError = msg) } },
            onReady = { push { copy(hazardFromCar = true) } }, onUnavailable = { push { copy(hazardFromCar = false) } }).also { it.start() }
        // 주행 제한: 차가 움직이면 화면·소리를 멈춘다(리서치 2026-10-27)
        ux = UxRestrictionsWatcher(context, { on -> push { copy(driving = on) } }, { msg -> push { copy(lastError = msg) } }).also { it.start() }
    }
    private var ux: UxRestrictionsWatcher? = null

    override fun stop() {
        try { ux?.stop() } catch (e: Exception) {}; ux = null
        aaosGear?.stop(); aaosGear = null
        aaosHazard?.stop(); aaosHazard = null
        vehicle?.let {
            runCatching { VehicleAccess.steering(it).unregisterSteeringWheelAngle(steerL) }
            runCatching { VehicleAccess.turnSignal(it).unregisterTurnSignalState(signalL) }
            runCatching { VehicleAccess.brake(it).unregisterBrakePedalStatus(brakeL) }
            runCatching { VehicleAccess.gear(it).unregisterGearSelection(gearL) }
            runCatching { it.release() }
        }
        vehicle = null
    }

    companion object {
        /** SDK 조향각 부호 → 앱 부호(+ = 시계방향·우회전). 에뮬레이터 2.1.0 실측(2026-10-26): Virtual Console 핸들을 오른쪽 +200° 로 돌리면 SDK 는 -200.0 을 준다. 범위 ±540°, 도 단위. 화면 핸들·가이드라인·모델이 다 이 값을 쓰므로 여기서 한 번만 뒤집는다. */
        const val STEER_SIGN = -1f
        /** Pleos 차량 서비스가 있는 기기인가 (에뮬레이터 포함). 없으면 시뮬레이터로. */
        fun isAvailable(context: Context): Boolean =
            context.packageManager.queryIntentServices(Intent("ai.pleos.playground.vehicle.VEHICLE_START"), 0).isNotEmpty()
    }
}
