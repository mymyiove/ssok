package app.parkingcoach.sim

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 주차 과제(스테이지). site/preview/sim.js 의 build() 와 같은 수식 — 둘을 같이 고친다.
 * 좌표계: 미터, +x 오른쪽, +y 위(탑뷰), heading 0 = +x 방향.
 * 치수 근거: 주차장법 시행규칙 — 일반형 2.5×5.0(2019~), 확장형 2.6×5.2, 2019 이전 일반형 2.3×5.0, 평행 2.0×6.0,
 * 차로 너비 직각 6.0 · 45° 대향 3.5 · 60° 대향 4.5 · 평행 3.3. 초급은 넓게, 중급은 법정, 고급은 구축 주차장(좁은 칸)·어긋난 시작.
 */
enum class Kind(val label: String) { PERP_REV("후면 직각주차"), PERP_FWD("전면 직각주차"), PARALLEL("평행주차"), ANGLED("사선주차 45°") }
enum class Neigh(val label: String) { NONE("옆 차 없음"), CARS("양옆 차"), PILLAR("기둥 + 양옆 차") }
enum class Side(val label: String) { R("오른쪽 칸"), L("왼쪽 칸") }

data class Tier(val name: String, val slotW: Double, val slotL: Double, val lane: Double, val gapExtra: Double, val aLane: Double, val tolDeg: Double, val offset: Double, val note: String, val backGap: Double = 0.4, val room: Double = 1.5) {
    companion object {
        val BEGINNER = Tier("초급", 3.0, 5.5, 9.0, 3.0, 6.5, 10.0, 0.0, "연습용 넓은 칸 3.0×5.5 · 차로 9 m · 뒤 벽 1.5 m · 평행 틈 차 길이 + 3 m", backGap = 1.5, room = 3.0)   // 초급은 법정 칸보다 넓은 연습용 칸(PM 2026-10-27 "주차칸이 너무 좁다, 움직일 공간이 더 넓어야"), sim.js TIERS 와 같다
        val MIDDLE = Tier("중급", 2.5, 5.0, 6.5, 1.9, 4.0, 6.0, 0.0, "법정 일반형 2.5×5.0 · 차로 6.5 m · 평행 틈 + 1.9 m", backGap = 0.6)
        val ADVANCED = Tier("고급", 2.3, 5.0, 6.0, 1.6, 3.5, 4.0, 0.0, "2019 이전 구축 칸 2.3×5.0 · 평행 틈 + 1.6 m(한 번에 들어가는 최소) · 정렬 허용 4° · 시작 자세 흔들림 ±0.4 m", backGap = 0.4)
        val ALL = listOf(BEGINNER, MIDDLE, ADVANCED)
    }
}

/** 돌발: ahead 범퍼에서 진행 방향으로 등장 거리, lat 옆에서 등장 거리, speed 가로지르는 속도, pause 경로 한가운데서 멈춰 서는 시간 */
enum class HazType(val label: String, val emoji: String, val ahead: Double, val lat: Double, val speed: Double, val pause: Double, val r: Double, val height: Double) {
    WALKER("보행자", "🚶", 3.0, 3.2, 1.3, 3.0, 0.25, 1.7),
    CHILD("아이", "🧒", 2.0, 1.8, 2.4, 2.5, 0.22, 1.1),
    CART("쇼핑카트", "🛒", 2.6, 2.6, 0.9, 6.0, 0.40, 1.0),
}

data class StageOpt(
    val kind: Kind, val tier: Tier, val neighbors: Neigh, val side: Side = Side.R,
    val hazards: Int = 0, val hazType: HazType? = null, val lane: Double? = null, val slotW: Double? = null,
    val no: Int = 0, val boss: Boolean = false,
) {
    val title: String get() {
        val bits = ArrayList<String>(); bits += kind.label
        if (side == Side.L) bits += "왼쪽 칸"
        bits += if (kind == Kind.PARALLEL) (if (neighbors == Neigh.NONE) "앞뒤 차 없음" else if (tier == Tier.ADVANCED) "좁은 틈" else "앞뒤 차") else neighbors.label
        if (lane != null) bits += "차로 $lane m"
        if (hazards > 0) bits += if (hazards > 1) "돌발 ${hazards}회" else "돌발"
        return (if (no > 0) "$no. " else "") + "[${tier.name}] " + bits.joinToString(" · ")
    }
}

object Stages {
    val ALL = listOf(
        StageOpt(Kind.PERP_REV, Tier.BEGINNER, Neigh.NONE, no = 1),
        StageOpt(Kind.PERP_REV, Tier.BEGINNER, Neigh.CARS, no = 2),
        StageOpt(Kind.PERP_FWD, Tier.BEGINNER, Neigh.NONE, no = 3),
        StageOpt(Kind.PARALLEL, Tier.BEGINNER, Neigh.NONE, no = 4),
        StageOpt(Kind.ANGLED, Tier.BEGINNER, Neigh.NONE, no = 5),
        StageOpt(Kind.PERP_REV, Tier.MIDDLE, Neigh.CARS, no = 6),
        StageOpt(Kind.PERP_FWD, Tier.MIDDLE, Neigh.CARS, no = 7),
        StageOpt(Kind.PERP_REV, Tier.MIDDLE, Neigh.CARS, hazards = 1, hazType = HazType.WALKER, no = 8),
        StageOpt(Kind.PARALLEL, Tier.MIDDLE, Neigh.CARS, no = 9),
        StageOpt(Kind.ANGLED, Tier.MIDDLE, Neigh.CARS, no = 10),
        StageOpt(Kind.PERP_REV, Tier.ADVANCED, Neigh.CARS, Side.L, hazards = 1, no = 11),
        StageOpt(Kind.PERP_REV, Tier.ADVANCED, Neigh.PILLAR, hazards = 1, hazType = HazType.CHILD, no = 12),
        StageOpt(Kind.PERP_FWD, Tier.ADVANCED, Neigh.PILLAR, hazards = 1, slotW = 2.5, no = 13),
        StageOpt(Kind.PARALLEL, Tier.ADVANCED, Neigh.CARS, hazards = 1, hazType = HazType.CART, no = 14),
        StageOpt(Kind.PERP_REV, Tier.ADVANCED, Neigh.PILLAR, Side.L, hazards = 2, lane = 5.5, no = 15, boss = true),
    )
}

/** 코칭 한 단계. cond = 이 단계를 떠나는 조건: X 공식 자리 도달 · R 가상 기어 R · F 가상 기어 D · > 핸들 오른쪽 400°↑ · < 왼쪽 400°↑ · 0 가운데(200°↓) · A 칸과 나란함 · D 칸과 45° */
data class CoachStep(val text: String, val cond: Char, val guide: String = "", val ok: String = "")   // guide = 연습하기 문장(~해 보세요) · ok = 완료 이름(sim.js COACH 와 같다)

/** 공식 자리까지 D 로 다가가는 거리(m): 시작 때 칸이 앞에 보인다 (sim.js APPROACH 와 같다) */
const val APPROACH = 7.0

data class Obstacle(val box: OBB, val label: String, val isCar: Boolean = true, val color: Int = 0xFF8A8D94.toInt(), val model: String = "GRANDEUR")

/** sim.js 의 lcg 와 같은 난수(uint32 LCG) — 같은 seed 면 브라우저 시안과 같은 상황이 나온다 */
class Lcg(seed: Long) {
    private var s: Long = (seed and 0xFFFFFFFFL).let { if (it == 0L) 1L else it }
    init { s = (s * 1664525L + 1013904223L) and 0xFFFFFFFFL }   // 워밍업 한 번: 작은 seed 의 첫 값 쏠림 제거 (sim.js lcg 와 같다)
    fun next(): Double { s = (s * 1664525L + 1013904223L) and 0xFFFFFFFFL; return s.toDouble() / 4294967296.0 }
}

data class Scenario(
    val title: String,
    val kind: Kind,
    val tier: Tier,
    val forward: Boolean,             // 주 진행 방향이 전진이면 전방 카메라 화면
    val slot: OBB,                    // 목표 칸
    val obstacles: List<Obstacle>,
    val marks: List<OBB>,             // 주차선만(장애물 아님)
    val start: Pose,
    val coach: List<CoachStep>,
    val tolDeg: Double,
    val hazards: Int,
    val hazType: HazType?,
    val bounds: OBB,                  // 화면 범위
    val env: String = "underground",  // underground | outdoor
    val seed: Long? = null,
    val lane: Double = 6.0,
    val formulaX: Double = 0.0,       // 공식대로라면 핸들을 끝까지 돌리는 뒷차축 x (끝난 뒤 비교용 · 코칭 'X' 조건)
    val approach: Double = APPROACH,  // 시작 → 공식 자리까지 D 로 다가가는 거리
    val room: Double = 1.5,           // 연습 구역 여유(화면 범위 밖으로 이만큼, 초급 3 m)
) {
    fun collides(body: OBB): Obstacle? = obstacles.firstOrNull { it.box.intersects(body) }
        ?: if (!body.inside(OBB(bounds.c, 0.0, bounds.hx + room, bounds.hy + room))) Obstacle(bounds, "연습 구역 끝", isCar = false) else null   // 끝없이 후진해 사라지지 않게
    /** 칸 축과 차 heading 의 차(0~90°) */
    fun headingDiff(heading: Double): Double { val dh = ((heading - slot.heading) % PI + PI) % PI; return min(dh, PI - dh) }
    fun parked(body: OBB, heading: Double): Boolean =
        headingDiff(heading) < Math.toRadians(tolDeg) && body.inside(OBB(slot.c, slot.heading, slot.hx + 0.3, slot.hy + 0.15))

    companion object {
        // 코칭 표(PM 2026-10-27 "글자 극단적으로 줄여": 코치 ≤ 22자 · 연습 ≤ 24자 · 완료 이름 ≤ 8자) = sim.js COACH
        private val COACH: Map<String, List<CoachStep>> = mapOf(
            "perp_rev" to listOf(CoachStep("비상등 켜고 D. 뒷차축이 옆 칸 지날 때까지", 'X', "뒷차축이 옆 칸을 지날 때까지 가요", "자리 잡기"), CoachStep("멈추고 R. 핸들 {R} 끝까지", '>', "멈추고 R, 핸들을 {R} 끝까지 돌려요", "핸들 끝까지"), CoachStep("노란선이 칸 안에 오면 핸들 풀기", '0', "노란선이 칸 안에 오면 핸들을 풀어요", "핸들 풀기"), CoachStep("나란하면 핸들 가운데, 뒤 벽 전 브레이크", 'A', "나란해지면 핸들 가운데, 브레이크", "나란히")),
            "perp_fwd" to listOf(CoachStep("비상등 켜고 D. 칸 입구가 {R} 미러 옆까지", 'X', "칸 입구가 {R} 미러 옆에 올 때까지 가요", "자리 잡기"), CoachStep("핸들 {R} 끝까지", '>', "핸들을 {R} 끝까지 돌려요", "핸들 끝까지"), CoachStep("노란선이 주차선과 나란하면 풀기", '0', "노란선이 주차선과 나란하면 풀어요", "핸들 풀기"), CoachStep("앞 벽 0.5 m, 빨간선 전에 브레이크", 'A', "빨간선이 닿기 전에 브레이크", "멈추기")),
            "perp_fwd_tight" to listOf(CoachStep("비상등 켜고 D, 바깥쪽으로 붙여 지나쳐요", 'X', "바깥쪽으로 붙여 운전석이 칸 중앙을 지나요", "자리 잡기"), CoachStep("핸들 {R} 끝까지, 35°에서 멈춤", '>', "핸들 {R} 끝까지, 35°쯤에서 멈춰요", "핸들 끝까지"), CoachStep("R, 핸들 {L} 끝까지, 2 m 빼기", 'R', "R 넣고 핸들 {L} 끝까지, 2 m 빼요", "한 번 빼기"), CoachStep("다시 D, 핸들 {R} 끝까지", 'A', "다시 D, 핸들 {R} 끝까지 돌려요", "다시 들어가기"), CoachStep("앞 벽 0.5 m, 빨간선 전에 브레이크", 'A', "빨간선이 닿기 전에 브레이크", "멈추기")),
            "parallel" to listOf(CoachStep("비상등 켜고 D. 뒷바퀴가 앞차 뒷범퍼까지", 'X', "뒷바퀴가 앞차 뒷범퍼 선까지 가요", "자리 잡기"), CoachStep("R. 핸들 {R} 끝까지, 45°에서 멈춤", 'D', "R 넣고 핸들 {R} 끝까지, 45°에서 멈춰요", "45°"), CoachStep("핸들 {L} 끝까지, 앞차를 빠져나가며", '<', "핸들 {L} 끝까지, 앞차를 빠져나가요", "핸들 반대로"), CoachStep("연석과 나란하면 핸들 가운데, 브레이크", 'A', "나란해지면 핸들 가운데, 브레이크", "나란히")),
            "parallel_none" to listOf(CoachStep("비상등 켜고 D. 뒷바퀴가 주차선 앞 끝까지", 'X', "뒷바퀴가 주차선 앞 끝까지 가요", "자리 잡기"), CoachStep("R. 핸들 {R} 끝까지, 45°에서 멈춤", 'D', "R 넣고 핸들 {R} 끝까지, 45°에서 멈춰요", "45°"), CoachStep("핸들 {L} 끝까지, 앞 범퍼가 칸 안으로", '<', "핸들 {L} 끝까지, 앞 범퍼가 칸 안으로", "핸들 반대로"), CoachStep("연석과 나란하면 핸들 가운데, 브레이크", 'A', "나란해지면 핸들 가운데, 브레이크", "나란히")),
            "angled" to listOf(CoachStep("비상등 켜고 D. 칸 입구가 {R} 미러 옆까지", 'X', "칸 입구가 {R} 미러 옆에 올 때까지 가요", "자리 잡기"), CoachStep("핸들 {R} 끝까지", '>', "핸들을 {R} 끝까지 돌려요", "핸들 끝까지"), CoachStep("나란하면(45°) 핸들 가운데, 직진", 'A', "나란해지면 핸들 가운데, 직진해요", "나란히"), CoachStep("앞 벽 0.5 m, 빨간선 전에 브레이크", 'A', "빨간선이 닿기 전에 브레이크", "멈추기")),
        )

        val CAR_COLORS = intArrayOf(0xFFE8E8EA.toInt(), 0xFFC9CCD2.toInt(), 0xFF2B2D33.toInt(), 0xFF6B6F78.toInt(), 0xFF3D5A99.toInt(), 0xFF8A2B2B.toInt(), 0xFFD8D2C2.toInt(), 0xFF4A5D4F.toInt())
        private data class Jit(val x: Double, val y: Double, val h: Double, val nb: Double)
        private val JITTER = mapOf("초급" to Jit(0.15, 0.1, 2.0, 0.12), "중급" to Jit(0.25, 0.15, 3.0, 0.15), "고급" to Jit(0.4, 0.2, 4.0, 0.15))
        private fun mirror(b: OBB) = OBB(Vec(b.c.x, -b.c.y), -b.heading, b.hx, b.hy)

        /** 환경: 지하 60% · 지상 40%. 고급은 지상 몫을 지상 20 · 밤 12 · 비 8 로 나눈다 (sim.js envOf 와 같은 식) */
        fun envOf(r: Double, tier: Tier): String = if (r < 0.6) "underground" else if (tier != Tier.ADVANCED) "outdoor" else if (r < 0.8) "outdoor" else if (r < 0.92) "night" else "rain"
        /** seed 를 주면 매번 다른 상황(시작 자세·옆 차 자리·색·지하/지상). null 이면 정본 자리(테스트·공식 검증 기준). */
        /** noStartJitter: 시연용 — 옆 차·색은 seed 대로, 시작 자세만 공식 자리 */
        fun build(spec: CarSpec, opt: StageOpt, seed: Long? = null, noStartJitter: Boolean = false): Scenario {
            val T = opt.tier; val SW = opt.slotW ?: T.slotW; val SL = T.slotL; val R = spec.minTurnRadius
            val lcg = seed?.let { Lcg(it) }; val rng = { lcg?.next() ?: 0.5 }; val J = JITTER.getValue(T.name); val rnd = { a: Double -> (rng() - 0.5) * 2 * a }
            val env = if (lcg == null) "underground" else envOf(rng(), T)
            val colorOf = { CAR_COLORS[(rng() * CAR_COLORS.size).toInt() % CAR_COLORS.size] }
            val modelOf = { CarSpec.MODELS[(rng() * CarSpec.MODELS.size).toInt() % CarSpec.MODELS.size] }
            // 옆 차 = 현대차 셋 중 하나, 실제 치수. 평행 앞뒤 차는 차 길이와 무관하게 틈이 같도록 범퍼 선 기준(bumperAt ±1)으로 놓는다.
            fun car(c: Vec, h: Double, label: String, bumperAt: Int = 0): Obstacle {
                // 평행에선 앞뒤 차를 틈 방향으로 흔들지 않는다(고급 틈은 한 번에 들어가는 최소)
                val u = Vec(cos(h), sin(h)); val v = Vec(-sin(h), cos(h)); val dl = if (opt.kind == Kind.PARALLEL) rnd(0.0) else rnd(J.nb * 1.6); val dw = rnd(J.nb * 0.6)
                val color = colorOf(); val M = modelOf(); val hx = M.length / 2; val hy = M.width / 2; val shift = bumperAt * hx
                return Obstacle(OBB(c + u * (dl + shift) + v * dw, h, hx, hy), label, isCar = true, color = color, model = M.key)
            }
            var formulaX = 0.0
            val lane = opt.lane ?: when { opt.kind == Kind.ANGLED -> T.aLane; opt.kind == Kind.PERP_FWD && opt.neighbors != Neigh.NONE -> maxOf(T.lane, 6.5); else -> T.lane }
            val obs = ArrayList<Obstacle>(); val marks = ArrayList<OBB>()
            var slot: OBB; var start: Pose; var forward = false; var coachKey = when (opt.kind) { Kind.PERP_REV -> "perp_rev"; Kind.PERP_FWD -> "perp_fwd"; Kind.PARALLEL -> "parallel"; Kind.ANGLED -> "angled" }
            when (opt.kind) {
                Kind.PERP_REV, Kind.PERP_FWD -> {
                    forward = opt.kind == Kind.PERP_FWD
                    if (forward && opt.neighbors != Neigh.NONE) coachKey = "perp_fwd_tight"
                    slot = OBB(Vec(0.0, -SL / 2), PI / 2, SL / 2, SW / 2)
                    for (k in listOf(-2, -1, 1, 2)) marks += OBB(Vec(k * SW, -SL / 2), PI / 2, SL / 2, SW / 2)
                    obs += Obstacle(OBB(Vec(0.0, -SL - T.backGap), 0.0, 9.0, 0.25), "뒤 벽", isCar = false)   // 뒤 벽 거리는 난이도별(sim.js backGap)
                    obs += Obstacle(OBB(Vec(0.0, lane + 0.3), 0.0, 12.0, 0.3), "벽", isCar = false)
                    // 기둥은 입구 모서리. 후면은 회전 안쪽(오른쪽), 전면은 접근 쪽(왼쪽) — 전면에서 바깥쪽 기둥은 한 번에 들어갈 수 없는 기하.
                    val pillarSide = if (forward) -1.0 else 1.0; val hasPillar = opt.neighbors == Neigh.PILLAR
                    if (hasPillar) obs += Obstacle(OBB(Vec(pillarSide * (SW / 2 + 0.5), -0.5), 0.0, 0.3, 0.3), "기둥", isCar = false)
                    if (opt.neighbors != Neigh.NONE) {
                        obs += car(Vec(-SW - (if (hasPillar && pillarSide < 0) 1.0 else 0.0), -SL / 2), PI / 2, "옆 차")
                        obs += car(Vec(SW + (if (hasPillar && pillarSide > 0) 1.0 else 0.0), -SL / 2), PI / 2, "옆 차")
                        if (lcg != null) for (k in listOf(-2, 2)) if (rng() < 0.7) obs += car(Vec(k * SW + (if (hasPillar && Math.signum(k.toDouble()) == pillarSide) Math.signum(k.toDouble()) * 1.0 else 0.0), -SL / 2), PI / 2, "주차 차량")
                    }
                    // 전면에 옆 차가 있으면 반대쪽(먼 벽 쪽)으로 붙여서 시작 — 연수 공식 '전면주차는 바깥쪽으로 최대한 붙여서'
                    val y0 = lane / 2 + 0.2 + (if (forward && opt.neighbors != Neigh.NONE) 0.7 else 0.0)
                    // 후면: 뒷차축이 칸 중심선에서 R 앞에 있으면 가득 꺾은 후진 사분원 뒤 칸에 정렬(+0.15 여유). 고급은 offset 만큼 더 지나친 자리.
                    // 전면: 뒷차축이 칸 중심선에서 R 뒤일 때 가득 꺾으면 정렬(옆 차 없을 때). 그 1 m 전에서 시작.
                    formulaX = if (forward) (if (opt.neighbors == Neigh.NONE) -R else -R + 2.5) else R + 0.15
                    start = Pose(formulaX - APPROACH, y0, 0.0)   // 칸이 앞에 보이는 자리에서 D 로 다가가 자리를 잡는다
                }
                Kind.PARALLEL -> {
                    val gap = spec.length + T.gapExtra; val W = 2.3; val cy = -W / 2          // 평행 칸 폭 2.3(법정 2.0 + 연석 여유)
                    slot = OBB(Vec(0.0, cy), 0.0, gap / 2, W / 2)
                    obs += Obstacle(OBB(Vec(0.0, -W - 0.15), 0.0, 12.0, 0.15), "연석", isCar = false)
                    if (opt.neighbors != Neigh.NONE) { obs += car(Vec(gap / 2 + 0.1, cy), 0.0, "앞차", 1); obs += car(Vec(-(gap / 2 + 0.1), cy), 0.0, "뒷차", -1) }
                    else coachKey = "parallel_none"
                    // 45° 두 번 꺾는 공식: 옆으로 2·R(1−cos45°) 들어간다 → 시작 y 를 거기에 맞춘다. 뒷차축 = 앞차 뒷범퍼 선(= 칸 앞 끝).
                    formulaX = gap / 2
                    start = Pose(formulaX - APPROACH, cy + 2 * R * (1 - cos(PI / 4)), 0.0)
                }
                Kind.ANGLED -> {
                    forward = true
                    val h = -PI / 4; val s2 = sqrt(0.5); val cy = -(SL / 2 + SW / 2) * s2          // 칸 윗 모서리가 y=0 (차로 가장자리)
                    slot = OBB(Vec(0.0, cy), h, SL / 2, SW / 2)
                    val pitch = SW * sqrt(2.0)
                    for (k in listOf(-2, -1, 1, 2)) marks += OBB(Vec(k * pitch, cy), h, SL / 2, SW / 2)
                    if (opt.neighbors != Neigh.NONE) { obs += car(Vec(-pitch, cy), h, "옆 차"); obs += car(Vec(pitch, cy), h, "옆 차"); if (lcg != null) for (k in listOf(-2, 2)) if (rng() < 0.7) obs += car(Vec(k * pitch, cy), h, "주차 차량") }
                    obs += Obstacle(OBB(Vec(0.0, cy - (SL / 2) * s2 - SW / 2 * s2 - T.backGap), 0.0, 14.0, 0.25), "뒤 벽", isCar = false)
                    obs += Obstacle(OBB(Vec(0.0, lane + 0.3), 0.0, 14.0, 0.3), "벽", isCar = false)
                    // 45° 가득 꺾으면 y 가 R(1−cos45°) 내려가고 x 가 R·sin45° 나아간다. 그 끝이 칸 축 위에 오도록 꺾는 점을 잡고 1 m 전에서 시작.
                    val y0 = lane / 2 + 0.2; val yEnd = y0 - R * (1 - s2)
                    val sAxis = -(yEnd - cy) / s2; val xEnd = sAxis * s2
                    formulaX = xEnd - R * s2
                    start = Pose(formulaX - APPROACH, y0, 0.0)
                }
            }
            if (lcg != null) { val dx = rnd(J.x); val dy = rnd(J.y); val dh = Math.toRadians(rnd(J.h)); if (!noStartJitter) start = Pose(start.x + dx, start.y + dy, start.heading + dh) }
            var coach = COACH.getValue(coachKey)
            val flip = opt.side == Side.L
            if (flip) {
                slot = mirror(slot)
                for (i in obs.indices) obs[i] = obs[i].copy(box = mirror(obs[i].box))
                for (i in marks.indices) marks[i] = mirror(marks[i])
                start = Pose(start.x, -start.y, -start.heading)
                coach = coach.map { CoachStep(it.text, when (it.cond) { '>' -> '<'; '<' -> '>'; else -> it.cond }, it.guide, it.ok) }
            }
            val rw = if (flip) "왼쪽" else "오른쪽"; val lw = if (flip) "오른쪽" else "왼쪽"
            coach = coach.map { CoachStep(it.text.replace("{R}", rw).replace("{L}", lw), it.cond, it.guide.replace("{R}", rw).replace("{L}", lw), it.ok) }
            // 화면 범위: 긴 벽은 세로 범위에만 넣는다
            val walls = obs.filter { it.box.hx > 5 }
            val rest = listOf(slot) + obs.filter { it.box.hx <= 5 }.map { it.box } + marks + listOf(CarModel(spec).body(start), CarModel(spec).body(Pose(formulaX, start.y, 0.0)))
            var x0 = Double.MAX_VALUE; var y0 = Double.MAX_VALUE; var x1 = -Double.MAX_VALUE; var y1 = -Double.MAX_VALUE
            rest.forEach { b -> b.corners().forEach { p -> x0 = min(x0, p.x); y0 = min(y0, p.y); x1 = maxOf(x1, p.x); y1 = maxOf(y1, p.y) } }
            var bx = (x1 - x0) / 2 + 0.4; var by = (y1 - y0) / 2 + 0.4; var cx = (x0 + x1) / 2; var cyb = (y0 + y1) / 2
            if (walls.isNotEmpty()) {
                var wy0 = cyb - by; var wy1 = cyb + by
                walls.forEach { w -> w.box.corners().forEach { p -> wy0 = min(wy0, p.y); wy1 = maxOf(wy1, p.y) } }
                cyb = (wy0 + wy1) / 2; by = (wy1 - wy0) / 2 + 0.2
            }
            return Scenario(opt.title, opt.kind, T, forward, slot, obs, marks, start, coach, T.tolDeg, opt.hazards, opt.hazType, OBB(Vec(cx, cyb), 0.0, bx, by), env, seed, lane, formulaX, APPROACH, T.room)
        }

        fun all(spec: CarSpec) = Stages.ALL.map { build(spec, it) }
    }
}
