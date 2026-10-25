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
 * 권한: android.car.permission.CAR_EXTERIOR_LIGHTS (읽기). 차종이 지원하지 않으면 onError 로 끝나고 앱은 시뮬 토글로 돈다.
 */
class AaosHazardWatcher(private val context: Context, private val onHazard: (Boolean) -> Unit, private val onError: (String) -> Unit) {
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
            runCatching { m.getIntProperty(VehiclePropertyIds.HAZARD_LIGHTS_STATE, 0) }.onSuccess { onHazard(it == LIGHT_ON) }
            m.registerCallback(cb, VehiclePropertyIds.HAZARD_LIGHTS_STATE, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        } catch (e: Throwable) { onError("aaos hazard: ${e.message}") }
    }

    fun stop() { runCatching { pm?.unregisterCallback(cb) }; runCatching { car?.disconnect() }; car = null; pm = null }

    companion object { const val LIGHT_ON = 1 }  // android.car.VehicleLightState.STATE_ON
}
