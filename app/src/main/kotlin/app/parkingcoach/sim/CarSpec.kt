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
    val key: String = "AVANTE",
    val shape: String = "sedan",   // sedan / suv — 그리는 꼴
    val roofH: Double = 1.42,
    val hoodH: Double = 0.82,
) {
    val steeringRatio get() = maxSteeringDeg / maxWheelDeg
    val frontOverhang get() = length - wheelbase - rearOverhang
    val minTurnRadius get() = wheelbase / Math.tan(Math.toRadians(maxWheelDeg))

    companion object {
        val GRANDEUR = CarSpec("더 뉴 그랜저", length = 5.050, width = 1.880, wheelbase = 2.895, rearOverhang = 1.150, key = "GRANDEUR", shape = "sedan", roofH = 1.46, hoodH = 0.85)
        val AVANTE = CarSpec("디 올 뉴 아반떼", length = 4.765, width = 1.855, wheelbase = 2.750, rearOverhang = 1.050, key = "AVANTE", shape = "sedan", roofH = 1.42, hoodH = 0.82)
        val TUCSON = CarSpec("디 올 뉴 투싼", length = 4.640, width = 1.865, wheelbase = 2.755, rearOverhang = 0.955, key = "TUCSON", shape = "suv", roofH = 1.665, hoodH = 1.02)
        val ALL = listOf(AVANTE, GRANDEUR, TUCSON)
        /** 옆 차도 이 셋 중 하나(seed 로 고른다, 정본은 가운데 = 그랜저). sim.js MODELS 와 같은 순서 */
        val MODELS = listOf(AVANTE, GRANDEUR, TUCSON)
        fun byKey(k: String?) = ALL.firstOrNull { it.key == k } ?: AVANTE
    }
}
