package app.parkingcoach.sim

/**
 * 차종 치수(m). 가이드라인·회전반경이 여기서 나온다.
 * Pleos CarInfo 는 VIN/CRN 만 주고 외부 치수를 주지 않아(AAR 2.3.2 확인) 프리셋으로 둔다.
 * 조향비·최대 바퀴각은 그랜저 HG 로 검증된 KAIS 2015 논문 가정(±540° ↔ 35°)을 기본값으로.
 */
data class CarSpec(
    val name: String,
    val length: Double,
    val width: Double,
    val wheelbase: Double,
    val rearOverhang: Double,      // 뒷차축 → 뒷범퍼
    val maxWheelDeg: Double = 35.0,
    val maxSteeringDeg: Double = 540.0,
) {
    val steeringRatio get() = maxSteeringDeg / maxWheelDeg
    val frontOverhang get() = length - wheelbase - rearOverhang
    val minTurnRadius get() = wheelbase / Math.tan(Math.toRadians(maxWheelDeg))

    companion object {
        val GRANDEUR = CarSpec("더 뉴 그랜저", length = 5.050, width = 1.880, wheelbase = 2.895, rearOverhang = 1.150)
        val AVANTE = CarSpec("디 올 뉴 아반떼", length = 4.765, width = 1.855, wheelbase = 2.750, rearOverhang = 1.050)
        val ALL = listOf(AVANTE, GRANDEUR)
    }
}
