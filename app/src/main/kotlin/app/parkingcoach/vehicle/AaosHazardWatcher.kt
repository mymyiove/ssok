package app.parkingcoach.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context

/**
 * 비상등 상태를 AAOS 표준 속성 HAZARD_LIGHTS_STATE 로 읽는다 (android.car.VehicleLightState: ON = 1).
 * Pleos Vehicle SDK 2.3.2 의 TurnSignal 은 OFF/LEFT/RIGHT 만 주고 Light 도메인엔 비상등이 없지만,
 * vehicle-data 1.1.11 의 PleosPropertyIds 에 HAZARD_LIGHTS_STATE(193)·HAZARD_LIGHTS_SWITCH 가 있어 AAOS 통로로 읽는다(2026-10-25 AAR 확인).
 * 권한: android.car.permission.CAR_EXTERIOR_LIGHTS (읽기). 못 읽으면 onUnavailable → 실차 모드에서도 화면 비상등 버튼으로 돈다.
 */
class AaosHazardWatcher(private val context: Context, private val onHazard: (Boolean) -> Unit, private val onError: (String) -> Unit,
                        private val onReady: () -> Unit = {}, private val onUnavailable: () -> Unit = {}) {
    private var car: Car? = null
    private var pm: CarPropertyManager? = null
    private val cb = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(v: CarPropertyValue<*>) { (v.value as? Int)?.let { onHazard(it == LIGHT_ON) } }
        override fun onErrorEvent(propId: Int, zone: Int) { onError("aaos hazard error prop=$propId") }
    }

    fun start() {
        try {
            val c = Car.createCar(context) ?: run { onError("Car.createCar null"); return }
            car = c
            val m = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            pm = m
            // 에뮬레이터 2.1.0 실측(2026-10-26): 속성 목록에 없고 registerCallback 은 예외 없이 false 만 돌려준다 → 못 읽는 것으로 친다
            if (m.getCarPropertyConfig(VehiclePropertyIds.HAZARD_LIGHTS_STATE) == null) { onUnavailable(); return }
            if (!m.registerCallback(cb, VehiclePropertyIds.HAZARD_LIGHTS_STATE, CarPropertyManager.SENSOR_RATE_ONCHANGE)) { onUnavailable(); return }
            onReady()
            runCatching { m.getIntProperty(VehiclePropertyIds.HAZARD_LIGHTS_STATE, 0) }.onSuccess { onHazard(it == LIGHT_ON) }
        } catch (e: Throwable) { onError("aaos hazard: ${e.message}"); onUnavailable() }
    }

    fun stop() { runCatching { pm?.unregisterCallback(cb) }; runCatching { car?.disconnect() }; car = null; pm = null }

    companion object { const val LIGHT_ON = 1 }  // android.car.VehicleLightState.STATE_ON
}
