package app.parkingcoach.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context

/**
 * 주행 제한(AAOS CarUxRestrictions): 차가 움직여 '주의 분산 최적화 필요' 가 되면 앱은 화면·소리를 멈추고 가드만 보인다(Pleos 운전 안전 가이드 · Google DD-2, 리서치 2026-10-27).
 * 기어 P 가드(PleosVehicleSource.gear)와 별개로 한 겹 더. 지원 안 하는 환경이면 오류 한 줄 남기고 넘어간다.
 */
class UxRestrictionsWatcher(private val context: Context, private val onRestricted: (Boolean) -> Unit, private val onError: (String) -> Unit) {
    private var car: Car? = null
    private var mgr: CarUxRestrictionsManager? = null
    private val listener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r -> onRestricted(r.isRequiresDistractionOptimization) }

    fun start() {
        try {
            val c = Car.createCar(context) ?: run { onError("ux: Car.createCar null"); return }
            car = c
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: run { onError("ux: manager null"); return }
            mgr = m; m.registerListener(listener)
            onRestricted(m.currentCarUxRestrictions?.isRequiresDistractionOptimization == true)
        } catch (e: Exception) { onError("ux: " + (e.message ?: e.javaClass.simpleName)) }
    }
    fun stop() { try { mgr?.unregisterListener() } catch (e: Exception) {}; try { car?.disconnect() } catch (e: Exception) {}; mgr = null; car = null }
}
