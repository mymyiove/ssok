package app.parkingcoach.vehicle

/** PC·일반 에뮬레이터용. 화면 패널(슬라이더·버튼)이 값을 밀어 넣는다. */
class SimulatedVehicleSource : VehicleInputSource {
    override var state = VehicleState(gear = GearState.PARK, connected = true, sourceName = "sim")
        private set
    private var onUpdate: ((VehicleState) -> Unit)? = null
    override fun start(onUpdate: (VehicleState) -> Unit) { this.onUpdate = onUpdate; onUpdate(state) }
    override fun stop() { onUpdate = null }
    fun set(mutate: VehicleState.() -> VehicleState) { state = state.mutate(); onUpdate?.invoke(state) }
}
