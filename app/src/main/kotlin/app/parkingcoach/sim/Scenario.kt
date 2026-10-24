package app.parkingcoach.sim

import kotlin.math.PI
import kotlin.math.abs

data class Obstacle(val box: OBB, val label: String, val isCar: Boolean = true)

/**
 * 주차 과제. 좌표계: 미터, +x 오른쪽, +y 위(탑뷰), heading 0 = +x 방향.
 * 한국 주차 구획: 2.5 × 5.0 m (2019 확폭 기준, 신축). 통로 폭 6.0 m.
 */
data class Scenario(
    val id: String,
    val title: String,
    val coach: List<String>,          // 단계별 기준점 코칭(연수 공식)
    val slot: OBB,                    // 목표 칸
    val obstacles: List<Obstacle>,
    val start: Pose,
    val headingTolDeg: Double = 6.0,
    val bounds: OBB,                  // 화면 범위
) {
    companion object {
        private const val SLOT_W = 2.5; private const val SLOT_L = 5.0

        /** 후면 직각주차: 통로를 따라 서 있다가 오른쪽 뒤 칸으로 후진 */
        fun perpendicularRight(spec: CarSpec, pillar: Boolean = false): Scenario {
            val slotC = Vec(0.0, -SLOT_L / 2.0)
            val slot = OBB(slotC, PI / 2, SLOT_L / 2, SLOT_W / 2)      // 세로 칸(heading 90°: 길이축이 y)
            val carBox = { cx: Double -> Obstacle(OBB(Vec(cx, -SLOT_L / 2.0), PI / 2, 2.3, 0.9), "주차 차량") }
            val obs = mutableListOf(carBox(-SLOT_W), carBox(SLOT_W))
            if (pillar) obs += Obstacle(OBB(Vec(SLOT_W / 2 + 0.25, -0.6), 0.0, 0.25, 0.25), "기둥", isCar = false)
            // 통로 반대편 벽
            obs += Obstacle(OBB(Vec(0.0, 6.6), 0.0, 12.0, 0.3), "벽", isCar = false)
            // 시작: 칸 앞 통로, 왼쪽을 향해(= 칸을 오른잽 뒤에 두고) 차체 중심이 칸 옆을 지나친 위치
            val start = Pose(x = spec.rearOverhang + 1.2, y = 3.4, heading = PI) // 뒷차축이 칸 오른쪽 위
            return Scenario(
                id = if (pillar) "perp_pillar" else "perp",
                title = if (pillar) "기둥 옆 후면주차" else "후면 직각주차",
                coach = listOf(
                    "오른쪽 깜빡이(후진)를 올리고, 핸들은 아직 그대로.",
                    "뒷범퍼가 칸 입구 선에 걸치면 핸들을 오른쪽으로 끝까지.",
                    "노란선이 주차선 안으로 들어오면 핸들을 풀기 시작.",
                    "차체가 칸과 나란해지면 핸들을 가운데로, 빨간선이 뒤 벽에 닿기 전에 브레이크."),
                slot = slot, obstacles = obs, start = start,
                bounds = OBB(Vec(0.0, 1.0), 0.0, 7.5, 6.5))
        }

        /** 평행주차: 길가 두 차 사이 6.5 m 공간 */
        fun parallelRight(spec: CarSpec): Scenario {
            val gap = spec.length + 1.6
            val slot = OBB(Vec(0.0, -1.3), 0.0, gap / 2, 1.3)
            val obs = listOf(
                Obstacle(OBB(Vec(-(gap / 2 + 2.4), -1.3), 0.0, 2.4, 0.95), "앞차"),
                Obstacle(OBB(Vec((gap / 2 + 2.4), -1.3), 0.0, 2.4, 0.95), "뒷차"),
                Obstacle(OBB(Vec(0.0, -2.75), 0.0, 10.0, 0.15), "연석", isCar = false))
            val start = Pose(x = gap / 2 + 1.0 + spec.rearOverhang + 0.6, y = 1.6, heading = PI) // 뒷차 옆에 나란히, 왼쪽(= -x) 방향으로 서 있음 → 후진은 +x
            return Scenario("parallel", "평행주차",
                listOf("뒷차 범퍼와 내 뒷범퍼를 맞추고 후진 깜빡이.",
                    "핸들 오른쪽 끝까지, 차체가 45°가 되면 멈춤.",
                    "핸들 왼쒽 끝까지, 앞 범퍼가 뒷차를 빠져나가면서 들어가기.",
                    "연석과 나란해지면 핸들 가운데, 브레이크."),
                slot, obs, start, headingTolDeg = 5.0, bounds = OBB(Vec(0.0, 0.0), 0.0, 9.0, 5.0))
        }

        fun all(spec: CarSpec) = listOf(perpendicularRight(spec), perpendicularRight(spec, pillar = true), parallelRight(spec))
    }

    fun collides(body: OBB) = obstacles.firstOrNull { it.box.intersects(body) }
    fun parked(body: OBB, heading: Double): Boolean {
        val dh = ((heading - slot.heading) % PI + PI) % PI
        val aligned = abs(dh) < Math.toRadians(headingTolDeg) || abs(dh - PI) < Math.toRadians(headingTolDeg)
        return aligned && body.inside(slot)
    }
}
