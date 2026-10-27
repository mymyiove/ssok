package app.parkingcoach.game

import app.parkingcoach.sim.CarModel
import app.parkingcoach.sim.CarSpec
import app.parkingcoach.sim.HazType
import app.parkingcoach.sim.OBB
import app.parkingcoach.sim.Pose
import app.parkingcoach.sim.Scenario
import app.parkingcoach.sim.Vec
import app.parkingcoach.vehicle.GearState
import app.parkingcoach.vehicle.Signal
import app.parkingcoach.vehicle.VehicleState
import app.parkingcoach.sim.Lcg
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin

enum class Phase { READY, DRIVING, PARKED, HIT, ENDED }   // ENDED = P 를 눌렀지만 칸에 못 들어감

data class TracePoint(val t: Double, val steeringDeg: Double, val pose: Pose, val v: Double)

/** 돌발(사람·아이·카트): 옆에서 나와 경로 한가운데서 잠깐 멈춰 선 뒤 지나간다. */
class Hazard(val type: HazType, val origin: Vec, val nrm: Vec, side: Double) {
    var pos: Vec = origin + nrm * (type.lat * side)
    val vel: Vec = nrm * (-type.speed * side)
    var crossed = false
    var pauseLeft = type.pause
    var life = (2 * type.lat + 1.0) / type.speed + type.pause
    var wasStopped = false
    fun advance(dt: Double) {
        life -= dt
        if (!crossed) {
            val next = pos + vel * dt
            val s0 = (pos - origin).dot(nrm); val s1 = (next - origin).dot(nrm)
            if (Math.signum(s0) != Math.signum(s1)) { crossed = true; pos = origin } else pos = next
        } else if (pauseLeft > 0) pauseLeft -= dt
        else pos += vel * dt
    }
}

data class HazStats(var total: Int = 0, var hit: Int = 0, var avoided: Int = 0)

data class GameSnapshot(
    val phase: Phase,
    val pose: Pose,
    val body: OBB,
    val steeringDeg: Double,
    val v: Double,
    val hint: String,
    val hits: Int,
    val elapsed: Double,
    val coachStep: Int,
    val coachCount: Int,
    val reverse: Boolean,
    val hazard: Hazard?,
    val hazStats: HazStats,
    val flash: String?,
    val gear: String = "N",          // 들어간 가상 기어 D/N/R (레버 ≠ 기어: D·R 은 브레이크를 밟아야 들어간다)
    val gearRefused: Boolean = false, // 이번 틱에 브레이크 없이 D·R 을 넣으려 했다
    val inSlot: Boolean = false,      // 칸에 들어옴 → 브레이크 밟고 N 이면 끝
    val brakeDepth: Double = 1.0,
)

/**
 * 규칙(PM 2026-10-25 · 방향 10-27): 오토 차 주차처럼 '브레이크를 밟은 채 시작 → 깜빡이로 방향(RIGHT = 레버 위 = D 전진, LEFT = R 후진) → 브레이크를 떼면
 * 크리핑 속도(0.6 m/s)로 움직이고 밟으면 선다'. 가속 페달 없음. 브레이크를 한 번 밟기 전에는 깜빡이를 올려도 움직이지 않는다.
 * 기어가 P 가 아니면 멈춘다(정차 전용 앱 정책). site/preview/sim.js Engine 과 같은 규칙.
 */
class GameEngine(spec: CarSpec, steeringSign: Double = 1.0, private val seed: Long = 7L) {
    val model = CarModel(spec, steeringSign)
    var scenario: Scenario = Scenario.build(spec, app.parkingcoach.sim.Stages.ALL[0]); private set
    var pose: Pose = scenario.start; private set
    var phase = Phase.READY; private set
    var hits = 0; private set
    var elapsed = 0.0; private set
    var travelled = 0.0; private set
    val trace = ArrayList<TracePoint>()
    var hazard: Hazard? = null; private set
    val hazStats = HazStats()
    private var hazPlan = ArrayList<Pair<Double, HazType>>()
    private var rng = Lcg(seed)
    private var coachStep = 0
    private var stepAt = 0.0
    var hint = ""
    private var lastV = 0.0
    private var lastReverse = true
    private var flash: String? = null
    var firstLock: Pose? = null; private set        // 처음 핸들을 끝까지(500°↑) 돌렸을 때 자세 — 공식 자리와 비교
    var dirChanges = 0; private set                 // 전후진 전환 횟수(보정)
    var steerFlips = 0; private set                 // 핸들 방향 뒤집기 횟수
    private var lastDir = 0; private var lastSteerSign = 0
    var started = false; private set                 // 브레이크를 한 번 밟았나(출발 조건)
    var gear = "N"; private set                      // 가상 기어(들어간 것). sim.js Engine.gear 와 같은 규칙
    var inSlot = false; private set
    private var gearRefused = false; private var brakeD = 1.0; private var gearBlocked = false
    val startHint = "브레이크 밟고 시작. 비상등 켜고 D"
    var hazLightsBeforeLock = false; private set       // 처음 핸들을 끝까지 돌리기 전에 비상등을 켰나(주차 전 알림)

    val speed = 0.6  // m/s ≈ 2.2 km/h (주차 속도 고정)
    companion object { const val BRAKE_STOP = 0.6; /** 깜빡이 레버 = 가상 기어(PM 2026-10-27, 컬럼 레버와 같은 방향): 레버 위(오른쪽) = D · 아래(왼쪽) = R. 종전 왼쪽 = D 는 뒤집었다 */ fun sigOfGear(g: String) = when (g) { "D" -> Signal.RIGHT; "R" -> Signal.LEFT; else -> Signal.OFF } }
    val sessionLimitSec = 300.0  // EPS 과부하·드라이 스티어링 고려: 5분

    fun load(s: Scenario) { scenario = s; reset() }
    fun reset() {
        pose = scenario.start; phase = Phase.READY; hits = 0; elapsed = 0.0; travelled = 0.0; trace.clear()
        coachStep = 0; stepAt = 0.0; hint = startHint; started = false; hazLightsBeforeLock = false; lastV = 0.0; lastReverse = !scenario.forward
        gear = "N"; inSlot = false; gearRefused = false; brakeD = 1.0
        hazard = null; hazStats.total = 0; hazStats.hit = 0; hazStats.avoided = 0; rng = Lcg(seed); hazPlan.clear()
        firstLock = null; dirChanges = 0; steerFlips = 0; lastDir = 0; lastSteerSign = 0
        val types = HazType.values()
        for (i in 0 until scenario.hazards) hazPlan += (1.2 + rng.next() * 1.3 + i * 2.2) to (scenario.hazType ?: types[(rng.next() * types.size).toInt() % types.size])
    }

    private fun spawnHazard(type: HazType, v: Double) {
        val rev = v < 0; val dir = if (rev) -1.0 else 1.0
        val fwd = Vec(cos(pose.heading) * dir, sin(pose.heading) * dir); val nrm = Vec(-sin(pose.heading), cos(pose.heading))
        val side = if (rng.next() < 0.5) 1.0 else -1.0
        val origin = model.bumper(pose, rev) + fwd * type.ahead
        hazard = Hazard(type, origin, nrm, side)
        hazStats.total++; hint = "${type.emoji} ${type.label}! 멈춰요."; flash = type.label
    }

    fun tick(vs: VehicleState, dt: Double): GameSnapshot {
        flash = null
        if (phase == Phase.PARKED || phase == Phase.ENDED) return snapshot(vs)
        if (vs.gear == GearState.OTHER && vs.sourceName == "pleos") { hint = "기어를 P에 두면 계속할 수 있어요"; gearBlocked = true; lastV = 0.0; return snapshot(vs) }   // UNKNOWN(아직 못 읽음)은 막지 않는다
        if (gearBlocked) { gearBlocked = false; hint = if (started) scenario.coach[minOf(coachStep, scenario.coach.size - 1)].text else startHint }   // P 로 돌아오면 안내문도 돌아온다(PC 실측 커밋 ad0f7b7 의 hintBeforeGear 와 같은 뜻, 2026-10-27 합침)
        if (vs.hazardLights && firstLock == null) hazLightsBeforeLock = true
        // 실차 규칙(PM 2026-10-25): 가상 기어 D·R 은 브레이크를 밟고 있어야 들어간다(시프트 록), N 은 언제나. 살살 밟으면 느리게(60% 부터 정지). 들어온 뒤 브레이크 + N 이면 끝.
        val bd = (vs.brakeDepth?.toDouble() ?: (if (vs.brake) 1.0 else 0.0)).coerceIn(0.0, 1.0); val braked = bd >= BRAKE_STOP; brakeD = bd; gearRefused = false
        val want = when (vs.signal) { Signal.RIGHT -> "D"; Signal.LEFT -> "R"; else -> "N" }
        if (want != gear) { if (want == "N" || braked) gear = want else gearRefused = true }
        if (gear == "R") lastReverse = true else if (gear == "D") lastReverse = false   // 카메라·가이드라인은 들어간 기어를 따른다
        if (!started) { if (braked) { started = true; hint = scenario.coach.first().text } else { lastV = 0.0; return snapshot(vs) } }
        // 끝내기 = P 버튼(PM 2026-10-27, 멈춘 상태에서만, finish()). 시연(autoPark)만 들어온 뒤 브레이크 + N 이면 저절로 끝
        if (autoPark && inSlot && braked && gear == "N") { phase = Phase.PARKED; hint = doneText(); return snapshot(vs) }
        val creep = speed * maxOf(0.0, 1 - bd / BRAKE_STOP)
        val v = when (gear) { "D" -> creep; "R" -> -creep; else -> 0.0 }
        // 돌발: 움직이는 중 계획된 거리에서 등장, 옆에서 가로질러 와 경로 위에 잠깐 선다
        val hz = hazard
        if (hz != null) {
            hz.advance(dt)
            val d = model.body(pose).distTo(hz.pos)
            if (d <= hz.type.r + 0.1) {
                if (v != 0.0) { hits++; hazStats.hit++; phase = Phase.HIT; hint = "${hz.type.emoji} ${hz.type.label} 충돌! 보이면 바로 브레이크"; hazard = null; lastV = 0.0; return snapshot(vs) }
            } else if (v == 0.0 && d < 3.0) hz.wasStopped = true
            if (hazard != null && hz.life <= 0) { hazard = null; hazStats.avoided++; hint = if (hz.wasStopped) "잘 멈췄어요. 이어서 가요" else "지나갔어요. 보이면 바로 멈춰요"; stepAt = elapsed }
        } else if (v != 0.0 && hazPlan.isNotEmpty() && travelled >= hazPlan[0].first) spawnHazard(hazPlan.removeAt(0).second, v)

        if (v != 0.0) {
            if (phase == Phase.READY) phase = Phase.DRIVING
            val next = model.step(pose, vs.steeringDeg.toDouble(), v, dt)
            val body = model.body(next)
            val hit = scenario.collides(body)
            if (hit != null) {
                if (phase != Phase.HIT) hits++
                phase = Phase.HIT; hint = "${hit.label}에 닿았어요. 반대로 빼요"
            } else {
                pose = next; phase = Phase.DRIVING
                elapsed += dt; travelled += abs(v) * dt
                trace += TracePoint(elapsed, vs.steeringDeg.toDouble(), pose, v)
                lastReverse = v < 0
                if (firstLock == null && abs(vs.steeringDeg) > 500) firstLock = pose
                val dir = if (v > 0) 1 else -1; if (lastDir != 0 && dir != lastDir) dirChanges++; lastDir = dir
                val ss = if (abs(vs.steeringDeg) > 150) (if (vs.steeringDeg > 0) 1 else -1) else 0; if (ss != 0 && lastSteerSign != 0 && ss != lastSteerSign) steerFlips++; if (ss != 0) lastSteerSign = ss
                if (hazard == null) advanceCoach(vs)
                inSlot = scenario.parked(body, pose.heading); if (inSlot) hint = if (autoPark) "들어왔어요. 브레이크 밟고 N" else "들어왔어요. 다 되면 멈추고 P"
            }
        } else if (phase == Phase.HIT) {
            phase = Phase.DRIVING   // 멈춘 상태에서 반대 방향으로 움직이면 다시 DRIVING 으로
        }
        lastV = v
        if (elapsed > sessionLimitSec) hint = "5분 지났어요. 오늘은 여기까지"
        return snapshot(vs)
    }

    /** 기록(리더보드용): 주행 시간 + 패널티 초. 충돌 5 · 사람/카트 사고 15 · 공식보다 많은 전후진 보정 3/회. (sim.js score() 와 같은 규칙) */
    data class Score(val drive: Double, val pen: Double, val total: Double, val corrections: Int, val noHazLights: Boolean, val clean: Boolean, val align: Double = 0.0, val angDeg: Int = 0, val offCm: Int = 0)
    private fun expectDirChanges() = if (scenario.kind == app.parkingcoach.sim.Kind.PERP_FWD && scenario.coach.size == 5) 2 else if (scenario.forward) 0 else 1   // 접근(D) 뒤 R 한 번은 기본
    fun score(): Score {
        val corrections = maxOf(0, dirChanges - expectDirChanges()); val noHaz = !hazLightsBeforeLock
        val pen = hits * 5.0 + hazStats.hit * 15.0 + corrections * 3.0 + (if (noHaz) 2.0 else 0.0)
        // 정확도 우선(리서치 2026-10-27): 시간 ÷ 2 + 실수 + 정렬(각 1° 0.3초 · 치우침 10 cm 0.3초), sim.js score() 와 같다
        val sc = scenario; val ux = cos(sc.slot.heading); val uy = sin(sc.slot.heading); val across = (pose.x - sc.slot.c.x) * -uy + (pose.y - sc.slot.c.y) * ux
        val angDeg = Math.toDegrees(sc.headingDiff(pose.heading)).roundToInt(); val offCm = (abs(across) * 100).roundToInt()
        val align = Math.round((minOf(angDeg, 20) * 0.3 + minOf(offCm, 100) / 10.0 * 0.3) * 10) / 10.0; val drive = Math.round(elapsed * 0.5 * 10) / 10.0
        return Score(drive, pen, drive + pen + align, corrections, noHaz, hits == 0 && hazStats.hit == 0 && hazStats.total == hazStats.avoided && !noHaz, align, angDeg, offCm)
    }
    /** 끝난 뒤 피드백: 공식 자리와 비교, 보정 횟수, 핸들 뒤집기 (sim.js review() 와 같은 규칙) */
    fun review(): List<String> {
        val out = ArrayList<String>(); val sc = scenario; val fl = firstLock
        if (fl != null) {
            val d = (fl.x - sc.formulaX) * (if (sc.forward) 1 else -1) * (if (sc.kind == app.parkingcoach.sim.Kind.PARALLEL) -1 else 1)
            out += when { abs(d) < 0.3 -> "핸들을 끝까지 돌린 자리가 공식 자리와 거의 같았어요."; d > 0 -> "핸들을 끝까지 돌린 자리가 공식 자리보다 %.1f m 늦었어요.".format(d); else -> "핸들을 끝까지 돌린 자리가 공식 자리보다 %.1f m 일렀어요.".format(-d) }
        } else out += "핸들을 끝까지 돌리지 않고 들어갔어요. 좁은 곳에서는 가득 꺾어야 회전반경이 가장 작아요."
        out += if (hazLightsBeforeLock) "주차 전에 비상등을 켰어요. 뒤차가 알아요." else "주차 전에 비상등을 안 켰어요(+2초). 뒤차에 '여기 댈게요'를 알려 주세요."
        val expect = expectDirChanges()
        out += if (dirChanges <= expect) (if (expect == 2) "전후진 한 번 빼기만으로 들어갔어요." else "자리 잡고 한 번에 들어갔어요.") else "전후진을 ${dirChanges}번 바꿨어요(보정 ${dirChanges - expect}회)."
        if (steerFlips > (if (sc.kind == app.parkingcoach.sim.Kind.PARALLEL) 1 else if (expect > 0) 2 else 0) + 1) out += "핸들 방향을 ${steerFlips}번 뒤집었어요. 한 번 정한 방향을 끝까지 쓰는 게 깔끔해요."
        return out
    }

    /** 시연·테스트는 N + 브레이크로 저절로 끝, 사람은 P 로 끝(화면이 false 로 둔다) */
    var autoPark = true
    enum class Finish { OK, MOVING, IDLE }
    /** P 버튼(PM 2026-10-27 "유저가 다 됐다고 P 누르면 그때 종료", 멈춘 상태에서만): 칸 안이면 PARKED(쏙), 아니면 ENDED(아직) */
    fun finish(): Finish {
        if (phase == Phase.PARKED || phase == Phase.ENDED || !started) return Finish.IDLE
        if (lastV != 0.0) return Finish.MOVING
        if (inSlot) { phase = Phase.PARKED; hint = doneText() } else { phase = Phase.ENDED; hint = failText() }
        return Finish.OK
    }
    /** 칸에 못 들어간 채 P: 무엇이 모자랐나 — 각(틀어짐) · 옆 치우침 · 칸 밖 (sim.js failText 와 같은 규칙) */
    fun failText(): String {
        val sc = scenario; val b = model.body(pose); val hdDeg = Math.toDegrees(sc.headingDiff(pose.heading)); val out = ArrayList<String>()
        if (hdDeg >= sc.tolDeg) out += "차가 칸과 ${hdDeg.toInt()}° 틀어져 있어요(기준 ${sc.tolDeg.toInt()}°)"
        val ux = cos(sc.slot.heading); val uy = sin(sc.slot.heading); val dx = pose.x - sc.slot.c.x; val dy = pose.y - sc.slot.c.y
        val along = dx * ux + dy * uy; val across = dx * -uy + dy * ux
        if (!b.inside(OBB(sc.slot.c, sc.slot.heading, sc.slot.hx + 0.3, sc.slot.hy + 0.15))) { if (abs(across) > sc.slot.hy * 0.5) out += "옆으로 %.1f m 치우쳤어요".format(abs(across)) else out += "칸 밖이에요(${if (abs(along) > sc.slot.hx) "덜 들어갔어요" else "앞뒤가 걸쳐 있어요"})" }
        return if (out.isEmpty()) "아직이에요. 조금만 더 맞춰 보세요." else "아직이에요. " + out.joinToString(" · ") + "."
    }
    data class Gap(val cm: Int, val what: String)
    data class Gaps(val left: Gap, val right: Gap, val end: Gap, val endLabel: String)
    /** 내려서 보면(PM 2026-10-27 아이디어 ④, sim.js gaps() 와 같은 규칙): 끝난 자세에서 왼쪽·오른쪽·앞(뒤) 간격(cm) — 옆 차·기둥·벽·주차선 중 가까운 것 */
    fun gaps(): Gaps {
        val p = pose; val sc = scenario; val b = model.body(p); val u = Vec(cos(p.heading), sin(p.heading)); val v = Vec(-sin(p.heading), cos(p.heading))
        fun at(du: Double, dv: Double) = Vec(b.c.x + u.x * b.hx * du + v.x * b.hy * dv, b.c.y + u.y * b.hx * du + v.y * b.hy * dv)
        val su = Vec(cos(sc.slot.heading), sin(sc.slot.heading)); val sv = Vec(-su.y, su.x)
        fun lineGap(pt: Vec, sign: Double): Double { val d = (pt.x - sc.slot.c.x) * sv.x + (pt.y - sc.slot.c.y) * sv.y; return sc.slot.hy - sign * d }
        fun side(sign: Double): Gap { var best = 1e9; var what = "주차선"
            for (du in doubleArrayOf(0.8, 0.0, -0.8)) { val pt = at(du, sign)
                for (o in sc.obstacles) { val d = o.box.distTo(pt); if (d < best) { best = d; what = if (o.label == "기둥") "기둥" else if (o.isCar) "옆 차" else o.label.ifEmpty { "벽" } } }
                val lg = lineGap(pt, sign); if (lg < best) { best = lg; what = "주차선" } }
            return Gap(maxOf(0.0, best * 100).toInt(), what) }
        val endSign = if (sc.forward) 1.0 else -1.0; val nose = at(endSign, 0.0); var fb = 1e9; var fwhat = "주차선"
        for (o in sc.obstacles) { val d = o.box.distTo(nose); if (d < fb) { fb = d; fwhat = if (o.isCar) "앞 차" else o.label.ifEmpty { "벽" } } }
        val along = (nose.x - sc.slot.c.x) * su.x + (nose.y - sc.slot.c.y) * su.y; val dl = sc.slot.hx - along * (if (along >= 0) 1 else -1); if (dl < fb) { fb = dl; fwhat = "칸 끝 선" }
        return Gaps(side(1.0), side(-1.0), Gap(maxOf(0.0, fb * 100).toInt(), fwhat), if (sc.forward) "앞" else "뒤")
    }
    fun doneText(): String {
        val base = if (hits == 0) "쏙! 한 번도 부딪히지 않았어요." else "쏙! ${hits}번 부딪혔어요. 리플레이에서 어디서 각을 잘못 잡았나 보세요."
        val h = hazStats
        return if (h.total > 0) "$base 돌발 ${h.total}회 중 ${h.avoided}회 멈춤${if (h.hit > 0) " · ${h.hit}회 사고" else ""}." else base
    }

    /** 코칭 진행: 단계마다 '떠나는 조건'(Scenario.CoachStep.cond)이 맞으면 다음 문장으로. */
    private fun advanceCoach(vs: VehicleState) {
        val c = scenario.coach; val cond = c.getOrNull(coachStep)?.cond
        val st = vs.steeringDeg.toDouble(); val hd = scenario.headingDiff(pose.heading)
        val go = when (cond) {
            'X' -> pose.x >= scenario.formulaX - 0.25
            'R' -> gear == "R"; 'F' -> gear == "D"
            '>' -> st > 400; '<' -> st < -400
            '0' -> abs(st) < 200 && elapsed - stepAt > 1.0
            'A' -> hd < Math.toRadians(12.0); 'D' -> hd > Math.toRadians(35.0) && hd < Math.toRadians(55.0)
            else -> false
        }
        if (go && coachStep < c.size - 1) { coachStep++; stepAt = elapsed }
        hint = c[minOf(coachStep, c.size - 1)].text
    }

    private fun snapshot(vs: VehicleState) = GameSnapshot(
        phase, pose, model.body(pose), vs.steeringDeg.toDouble(), lastV, hint, hits, elapsed, coachStep, scenario.coach.size,
        reverse = when (gear) { "R" -> true; "D" -> false; else -> lastReverse }, hazard = hazard, hazStats = hazStats, flash = flash,
        gear = gear, gearRefused = gearRefused, inSlot = inSlot, brakeDepth = brakeD)
}
