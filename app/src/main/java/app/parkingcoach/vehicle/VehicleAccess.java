package app.parkingcoach.vehicle;

import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.Brake;
import ai.pleos.playground.vehicle.api.Gear;
import ai.pleos.playground.vehicle.api.SteeringWheel;
import ai.pleos.playground.vehicle.api.TurnSignal;

/**
 * Vehicle 의 도메인 getter 는 JVM 상 public 이지만 Kotlin 메타데이터에서는 private 프로퍼티로 보인다(SDK 2.3.2).
 * Java 는 메타데이터를 무시하므로 여기서 꺼내 준다.
 */
public final class VehicleAccess {
    private VehicleAccess() {}
    public static SteeringWheel steering(Vehicle v) { return v.getSteeringWheel(); }
    public static TurnSignal turnSignal(Vehicle v) { return v.getTurnSignal(); }
    public static Brake brake(Vehicle v) { return v.getBrake(); }
    public static Gear gear(Vehicle v) { return v.getGear(); }
}
