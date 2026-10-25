package app.parkingcoach.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context

/**
 * AAOS 표준 속성으로 기어 상태를 읽는다 (android.car.VehicleGear: PARK = 4).
 * Pleos Vehicle SDK 2.3.2 의 Gear 도메인이 deprecated("use the Google GEAR_SELECTION property")라 이쪽이 정본.
 * 권한: android.car.permission.CAR_POWERTRAIN (normal).
 */
class AaosGearWatcher(private val context: Context, private val onGear: (GearState) -> Unit, private val onError: (String) -> Unit) {
    private var car: Car? = null
    private var pm: CarPropertyManager? = null
    private val cb = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(v: CarPropertyValue<*>) { (v.value as? Int)?.let { onGear(if (it == GEAR_PARK) GearState.PARK else GearState.OTHER) } }
        override fun onErrorEvent(propId: Int, zone: Int) { onError("aaos gear error prop=$propId") }
    }

    fun start() {
        try {
            val c = Car.createCar(context) ?: run { onError("Car.createCar null"); return }
            car = c
            val m = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            pm = m
            runCatching { m.getIntProperty(VehiclePropertyIds.GEAR_SELECTION, 0) }.onSuccess { onGear(if (it == GEAR_PARK) GearState.PARK else GearState.OTHER) }
            m.registerCallback(cb, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        } catch (e: Throwable) { onError("aaos gear: ${e.message}") }
    }

    fun stop() { runCatching { pm?.unregisterCallback(cb) }; runCatching { car?.disconnect() }; car = null; pm = null }

    companion object { const val GEAR_PARK = 4 }  // android.car.VehicleGear.GEAR_PARK
}
