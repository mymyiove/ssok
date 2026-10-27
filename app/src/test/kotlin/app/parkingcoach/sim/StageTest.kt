package app.parkingcoach.sim

import app.parkingcoach.game.GameEngine
import app.parkingcoach.game.Phase
import app.parkingcoach.vehicle.Signal
import app.parkingcoach.vehicle.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 스테이지 기하 검증. 기준값은 site/preview/sim.js 를 node 로 돌려 찍은 것(tests/sim.test.mjs 와 같은 풀이) —
 * 두 구현이 같은 수식인지 숫자로 맞춰 본다.
 */
class StageTest {
    private val specs = listOf(CarSpec.AVANTE, CarSpec.GRANDEUR, CarSpec.TUCSON)

    @Test fun startIsCollisionFreeAndNotParked() {
        for (spec in specs) for (opt in Stages.ALL) {
            val sc = Scenario.build(spec, opt); val body = CarModel(spec).body(sc.start)
            assertTrue("${opt.no} ${spec.name} 시작 자세가 장애물과 겹침", sc.collides(body) == null)
            assertTrue("${opt.no} ${spec.name} 시작부터 주차 판정", !sc.parked(body, sc.start.heading))
            assertTrue("${opt.no} 화면 범위 과대", sc.bounds.hx < 14.5 && sc.bounds.hy < 10)
        }
    }

    @Test fun freePracticeCombosStartValid() {
        var n = 0
        for (spec in specs) for (k in Kind.values()) for (t in Tier.ALL) for (nb in Neigh.values()) for (s in Side.values()) {
            val sc = Scenario.build(spec, StageOpt(k, t, nb, s)); assertTrue("자유 $k/$t/$nb/$s 시작 충돌", sc.collides(CarModel(spec).body(sc.start)) == null); n++
        }
        assertEquals(216, n)
    }

    /** node 기준값(sim.js): 시작 자세·칸·장애물 수·화면 범위 */
    @Test fun matchesJsReference() {
        data class Ref(val no: Int, val sx: Double, val sy: Double, val slotCx: Double, val slotCy: Double, val slotH: Double, val slotHx: Double, val slotHy: Double, val obs: Int, val marks: Int, val bx: Double, val by: Double, val bhx: Double, val bhy: Double, val coach: Int, val fwd: Boolean)
        val av = listOf(
            Ref(1, -2.9226, 3.9500, 0.0000, -2.6000, 1.5708, 2.600, 1.300, 2, 4, 0.646, 0.825, 7.546, 7.475, 4, false),
            Ref(3, -10.9274, 3.9500, 0.0000, -2.6000, 1.5708, 2.600, 1.300, 2, 4, -2.739, 0.825, 9.639, 7.475, 4, true),
            Ref(4, -3.4175, 1.1506, 0.0000, -1.1500, 0.0000, 3.582, 1.150, 1, 0, 1.415, -0.111, 6.283, 2.789, 4, false),
            Ref(5, -14.3345, 2.9500, 0.0000, -2.7577, -0.7854, 2.600, 1.300, 2, 4, -2.636, -0.333, 13.148, 6.633, 4, true),
            Ref(7, -8.4274, 4.1500, 0.0000, -2.5000, 1.5708, 2.500, 1.250, 4, 4, -1.614, 0.625, 8.264, 6.675, 5, true),
            Ref(9, -3.6675, 1.1506, 0.0000, -1.1500, 0.0000, 3.332, 1.150, 3, 0, 0.000, -0.111, 8.883, 2.789, 4, false),
            Ref(10, -13.4784, 2.2000, 0.0000, -2.6517, -0.7854, 2.500, 1.250, 4, 4, -2.403, -0.777, 12.526, 5.577, 4, true),
            Ref(11, -2.9226, -3.2000, 0.0000, 2.5000, -1.5708, 2.500, 1.150, 4, 4, 1.021, -0.475, 7.171, 6.325, 4, false),
            Ref(13, -8.4274, 4.1500, 0.0000, -2.5000, 1.5708, 2.500, 1.250, 5, 4, -1.614, 0.725, 8.264, 6.575, 5, true),
            Ref(15, -2.9226, -2.9500, 0.0000, 2.5000, -1.5708, 2.500, 1.150, 5, 4, 1.021, -0.225, 7.171, 6.075, 4, false),
        )
        val gr = listOf(
            Ref(2, -2.7155, 3.9500, 0.0000, -2.6000, 1.5708, 2.600, 1.300, 4, 4, 0.842, 0.825, 7.742, 7.475, 4, false),
            Ref(4, -3.2750, 1.2719, 0.0000, -1.1500, 0.0000, 3.725, 1.150, 1, 0, 1.600, -0.044, 6.425, 2.856, 4, false),
            Ref(13, -8.6345, 4.1500, 0.0000, -2.5000, 1.5708, 2.500, 1.250, 5, 4, -1.767, 0.725, 8.417, 6.575, 5, true),
            Ref(14, -3.6750, 1.2719, 0.0000, -1.1500, 0.0000, 3.325, 1.150, 3, 0, 0.000, -0.044, 8.875, 2.856, 4, false),
        )
        for ((spec, refs) in listOf(CarSpec.AVANTE to av, CarSpec.GRANDEUR to gr)) for (r in refs) {
            val sc = Scenario.build(spec, Stages.ALL[r.no - 1]); val m = "${r.no} ${spec.name}"
            assertEquals("$m start.x", r.sx, sc.start.x, 1e-3); assertEquals("$m start.y", r.sy, sc.start.y, 1e-3)
            assertEquals("$m slot.cx", r.slotCx, sc.slot.c.x, 1e-3); assertEquals("$m slot.cy", r.slotCy, sc.slot.c.y, 1e-3); assertEquals("$m slot.h", r.slotH, sc.slot.heading, 1e-3)
            assertEquals("$m slot.hx", r.slotHx, sc.slot.hx, 1e-3); assertEquals("$m slot.hy", r.slotHy, sc.slot.hy, 1e-3)
            assertEquals("$m obs", r.obs, sc.obstacles.size); assertEquals("$m marks", r.marks, sc.marks.size); assertEquals("$m coach", r.coach, sc.coach.size); assertEquals("$m fwd", r.fwd, sc.forward)
            assertEquals("$m bounds.cy", r.by, sc.bounds.c.y, 1e-2); assertEquals("$m bounds.hy", r.bhy, sc.bounds.hy, 1e-2)   // x 범위는 접근 거리로 바뀜 → node 와의 대조는 seededSituationMatchesJs 가 맡는다
        }
    }

    /** 공식 조작으로 끝까지: 후면(가득 꺾기 → 정렬 → 직진), 사선, 평행(45° 두 번). 고급은 어긋난 0.6 m 를 직진으로 맞춘 뒤 같은 공식. */
    private fun solve(spec: CarSpec, opt: StageOpt, stopForHazard: Boolean = true, keepGoing: Boolean = false, fwdAngle: Double = 35.0): GameEngine {
        val eng = GameEngine(spec, seed = 11L); val sc = Scenario.build(spec, opt); eng.load(sc)
        eng.tick(VehicleState(signal = Signal.RIGHT, brake = true, hazardLights = true, sourceName = "sim"), 0.02)   // 브레이크를 밟은 채 시작(비상등), 밟은 채 D
        val T = opt.tier; val side = if (opt.side == Side.L) -1.0 else 1.0; val dt = 0.02
        var phase = 0; var t0 = 0.0; var t = 0.0; var approaching = true; var trav0 = 0.0
        while (t < 240) {
            t += dt
            if (approaching) { if (eng.pose.x < sc.formulaX) { val sn = eng.tick(VehicleState(signal = Signal.RIGHT, brake = (stopForHazard && eng.hazard != null) || eng.gear != "D", hazardLights = true, sourceName = "sim"), dt); if (sn.phase == Phase.HIT && !keepGoing) break; continue }; approaching = false; trav0 = eng.travelled }
            val hd = sc.headingDiff(eng.pose.heading); val trav = eng.travelled - trav0
            var steer = 0.0; var signal = Signal.OFF
            when (opt.kind) {
                Kind.PERP_REV -> { signal = Signal.LEFT
                    if (phase == 0 && trav >= T.offset) phase = 1
                    if (phase == 1) { steer = 540 * side; if (hd < Math.toRadians(0.7)) phase = 2 }
                }
                Kind.ANGLED -> { signal = Signal.RIGHT
                    if (phase == 0) phase = 1
                    if (phase == 1) { steer = 540 * side; if (hd < Math.toRadians(0.7)) phase = 2 }
                }
                Kind.PERP_FWD -> {
                    if (opt.neighbors == Neigh.NONE) { signal = Signal.RIGHT
                        if (phase == 0) phase = 1
                        if (phase == 1) { steer = 540 * side; if (hd < Math.toRadians(0.7)) phase = 2 }
                    } else { // 한 번 빼기 공식: 2.5 m 지나쳐 40° 꺾고 → 왼쪽 가득 후진 2 m → 오른쪽 가득 전진
                        signal = Signal.RIGHT; val h = -eng.pose.heading * side
                        if (phase == 0) phase = 1
                        if (phase == 1) { steer = 540 * side; if (h >= Math.toRadians(fwdAngle)) { phase = 2; t0 = trav } }
                        if (phase == 2) { steer = -540 * side; signal = Signal.LEFT; if (trav - t0 >= 2.0) phase = 3 }
                        if (phase == 3) { steer = 540 * side; if (hd < Math.toRadians(0.7)) phase = 4 }
                    }
                }
                Kind.PARALLEL -> { signal = Signal.LEFT
                    if (phase == 0 && trav >= T.offset) phase = 1
                    if (phase == 1) { steer = 540 * side; if (hd > Math.toRadians(44.5)) phase = 2 }
                    if (phase == 2) { steer = -540 * side; if (hd < Math.toRadians(0.7)) phase = 3 }
                }
            }
            // 기어를 바꿀 땐 밟고 바꾼다(시프트 록). 들어왔으면 브레이크 밟고 N 으로 마무리.
            val brake = (stopForHazard && eng.hazard != null) || GameEngine.sigOfGear(eng.gear) != signal
            val snap = if (eng.inSlot) eng.tick(VehicleState(steeringDeg = steer.toFloat(), signal = Signal.OFF, brake = true, hazardLights = true, sourceName = "sim"), dt)
                else eng.tick(VehicleState(steeringDeg = steer.toFloat(), signal = signal, brake = brake, hazardLights = true, sourceName = "sim"), dt)
            if (snap.phase == Phase.PARKED) break
            if (snap.phase == Phase.HIT && !keepGoing) break
        }
        return eng
    }

    @Test fun formulaSolvesEveryStage() {
        for (spec in specs) for (opt in Stages.ALL) {
            var eng = solve(spec, opt)
            if (eng.phase != Phase.PARKED && opt.kind == Kind.PERP_FWD) for (a in listOf(40.0, 33.0)) { eng = solve(spec, opt, fwdAngle = a); if (eng.phase == Phase.PARKED) break }
            assertTrue("${opt.no} ${spec.name} 공식 풀이로 안 들어감 (충돌 ${eng.hits})", eng.phase == Phase.PARKED && eng.hits == 0)
            if (opt.hazards > 0) { assertEquals("${opt.no} 돌발 횟수", opt.hazards, eng.hazStats.total); assertEquals("${opt.no} 멈추면 사고 0", 0, eng.hazStats.hit) }
        }
    }

    @Test fun ignoringHazardCausesAccident() {
        for (spec in specs) {
            val eng = solve(spec, Stages.ALL[7], stopForHazard = false, keepGoing = true)
            assertTrue("${spec.name} 돌발을 무시했는데 사고가 안 남", eng.hazStats.hit >= 1)
        }
    }

    /** 상황 번호(seed) 파리티: 같은 seed 면 브라우저 시안(sim.js)과 같은 시작 자세·옆 차 자리·색이어야 한다(node 로 찍은 값). */
    @Test fun seededSituationMatchesJs() {
        val g = CarSpec.GRANDEUR
        data class Car(val x: Double, val y: Double, val color: Int, val model: String)
        fun check(no: Int, seed: Long, env: String, sx: Double, sy: Double, sh: Double, obs: Int, cars: List<Car>) {
            val sc = Scenario.build(g, Stages.ALL[no - 1], seed); val m = "$no seed $seed"
            assertEquals("$m env", env, sc.env); assertEquals("$m obs", obs, sc.obstacles.size)
            assertEquals("$m sx", sx, sc.start.x, 1e-4); assertEquals("$m sy", sy, sc.start.y, 1e-4); assertEquals("$m sh", sh, sc.start.heading, 1e-4)
            val got = sc.obstacles.filter { it.isCar }
            assertEquals("$m cars", cars.size, got.size)
            cars.forEachIndexed { i, c -> assertEquals("$m car$i x", c.x, got[i].box.c.x, 1e-4); assertEquals("$m car$i y", c.y, got[i].box.c.y, 1e-4); assertEquals("$m car$i color", c.color, got[i].color); assertEquals("$m car$i model", c.model, got[i].model) }
        }
        check(6, 12345, "underground", -2.46867, 3.49911, -0.00807, 6, listOf(Car(-2.52428, -2.47929, 0xFF4A5D4F.toInt(), "AVANTE"), Car(2.49130, -2.50197, 0xFF3D5A99.toInt(), "TUCSON"), Car(-5.08385, -2.38298, 0xFFE8E8EA.toInt(), "TUCSON"), Car(4.91729, -2.54969, 0xFF6B6F78.toInt(), "TUCSON")))
        check(10, 777, "outdoor", -13.56449, 2.30791, -0.03444, 6, listOf(Car(-3.42982, -2.77529, 0xFFC9CCD2.toInt(), "GRANDEUR"), Car(3.45275, -2.57118, 0xFFD8D2C2.toInt(), "AVANTE"), Car(-6.93963, -2.74872, 0xFF6B6F78.toInt(), "GRANDEUR"), Car(7.09378, -2.77051, 0xFF6B6F78.toInt(), "TUCSON")))
        check(14, 4242, "underground", -3.67426, 1.35624, -0.00570, 3, listOf(Car(5.95000, -1.11557, 0xFF3D5A99.toInt(), "GRANDEUR"), Car(-5.80750, -1.06704, 0xFF6B6F78.toInt(), "AVANTE")))
        check(11, 6, "night", -2.57841, -3.36747, 0.01093, 6, listOf(Car(-2.37019, 2.29466, 0xFF6B6F78.toInt(), "AVANTE"), Car(2.36535, 2.29362, 0xFF3D5A99.toInt(), "TUCSON"), Car(-4.59550, 2.33371, 0xFF3D5A99.toInt(), "GRANDEUR"), Car(4.61748, 2.43920, 0xFFC9CCD2.toInt(), "TUCSON")))
        check(11, 30, "rain", -2.90954, -3.17063, -0.00102, 6, listOf(Car(-2.35010, 2.56682, 0xFF6B6F78.toInt(), "AVANTE"), Car(2.36757, 2.60808, 0xFFD8D2C2.toInt(), "AVANTE"), Car(-4.67554, 2.60467, 0xFF6B6F78.toInt(), "TUCSON"), Car(4.55182, 2.59487, 0xFF2B2D33.toInt(), "AVANTE")))
    }

    @Test fun randomSituationsStartValid() {
        var n = 0
        for (spec in specs) for (opt in Stages.ALL) for (seed in 1..20) { val sc = Scenario.build(spec, opt, seed * 7919L + opt.no); assertTrue("랜덤 ${opt.no} ${spec.name} seed $seed 시작 충돌", sc.collides(CarModel(spec).body(sc.start)) == null); n++ }
        assertEquals(900, n)
    }

    /** 시연 운전자: 모든 스테이지 × 차종 × seed(옆 차·색 흔들림, 시작은 공식 자리)에서 충돌 없이 들어간다 */
    @Test fun demoDriverParksEveryStage() {
        for (spec in specs) for (opt in Stages.ALL) for (seed in listOf<Long?>(null, 101L, 2024L)) {
            val sc = Scenario.build(spec, opt, seed, noStartJitter = true); val eng = GameEngine(spec, seed = seed ?: 5L); eng.load(sc)
            val d = app.parkingcoach.game.DemoDriver(sc, opt); var t = 0.0; var parked = false
            while (t < 240) { t += 0.02; val snap = eng.tick(d.step(eng, 0.02), 0.02); if (snap.phase == Phase.PARKED) { parked = true; break }; if (snap.phase == Phase.HIT) break }
            assertTrue("시연 ${opt.no} ${spec.name} seed $seed 실패 (${eng.hits})", parked && eng.hits == 0)
        }
    }

    @Test fun brakeFirstThenCreep() {
        val spec = CarSpec.AVANTE; val eng = GameEngine(spec); eng.load(Scenario.build(spec, Stages.ALL[0]))
        repeat(50) { eng.tick(VehicleState(signal = Signal.RIGHT, brake = false, sourceName = "sim"), 0.02) }
        assertTrue("브레이크 없이 출발함", eng.travelled == 0.0 && !eng.started)
        eng.tick(VehicleState(signal = Signal.RIGHT, brake = true, sourceName = "sim"), 0.02)
        repeat(50) { eng.tick(VehicleState(signal = Signal.RIGHT, brake = false, sourceName = "sim"), 0.02) }
        assertTrue("브레이크를 뗐는데 크리핑하지 않음", eng.travelled > 0.5)
    }

    /** 시프트 록 · 살살 밟기 · 브레이크 + N 마무리 (sim.test.mjs 와 같은 검사) */
    @Test fun shiftLockCreepAndFinish() {
        val spec = CarSpec.AVANTE; val sc = Scenario.build(spec, Stages.ALL[0]); val eng = GameEngine(spec, seed = 1L); eng.load(sc); val dt = 0.02
        repeat(50) { eng.tick(VehicleState(signal = Signal.RIGHT, brake = false, sourceName = "sim"), dt) }
        assertTrue("브레이크 없이 기어가 들어감", eng.gear == "N" && !eng.started)
        eng.tick(VehicleState(signal = Signal.RIGHT, brake = true, sourceName = "sim"), dt); assertEquals("D", eng.gear)
        var sn = eng.tick(VehicleState(signal = Signal.LEFT, brake = false, sourceName = "sim"), dt); assertTrue("안 밟고 R 이 들어감", sn.gearRefused && sn.gear == "D" && !sn.reverse)
        sn = eng.tick(VehicleState(signal = Signal.LEFT, brake = true, sourceName = "sim"), dt); assertTrue("밟고 R 을 넣었는데 안 들어감", !sn.gearRefused && sn.gear == "R" && sn.reverse)
        sn = eng.tick(VehicleState(signal = Signal.OFF, brake = false, sourceName = "sim"), dt); assertEquals("N", sn.gear)
        eng.tick(VehicleState(signal = Signal.RIGHT, brake = true, sourceName = "sim"), dt); val t0 = eng.travelled
        repeat(50) { eng.tick(VehicleState(signal = Signal.RIGHT, brake = false, brakeDepth = 0.3f, sourceName = "sim"), dt) }
        assertEquals("살살(0.3) 1초 = 0.30 m", 0.30, eng.travelled - t0, 0.02)
        val t1 = eng.travelled; repeat(20) { eng.tick(VehicleState(signal = Signal.RIGHT, brake = false, brakeDepth = 0.6f, sourceName = "sim"), dt) }; assertEquals("0.6 이면 정지", t1, eng.travelled, 1e-9)
        // 1번 스테이지를 공식대로 넣되 N 없이는 끝나지 않는다
        val e2 = GameEngine(spec, seed = 1L); e2.load(sc)
        e2.tick(VehicleState(signal = Signal.RIGHT, brake = true, hazardLights = true, sourceName = "sim"), dt)
        while (e2.pose.x < sc.formulaX) e2.tick(VehicleState(signal = Signal.RIGHT, brake = false, hazardLights = true, sourceName = "sim"), dt)
        e2.tick(VehicleState(steeringDeg = 540f, signal = Signal.LEFT, brake = true, hazardLights = true, sourceName = "sim"), dt); var ph = 1; var n = 0
        while (!e2.inSlot && n++ < 3000) { if (ph == 1 && sc.headingDiff(e2.pose.heading) < Math.toRadians(0.7)) ph = 2; e2.tick(VehicleState(steeringDeg = if (ph == 1) 540f else 0f, signal = Signal.LEFT, brake = false, hazardLights = true, sourceName = "sim"), dt) }
        assertTrue("칸에 들어왔는데 N 없이 끝남", e2.inSlot && e2.phase != Phase.PARKED)
        e2.tick(VehicleState(signal = Signal.LEFT, brake = true, sourceName = "sim"), dt); assertTrue("R 인 채 밟기만 했는데 끝남", e2.phase != Phase.PARKED)
        val fin = e2.tick(VehicleState(signal = Signal.OFF, brake = true, sourceName = "sim"), dt); assertEquals("브레이크 + N 이면 끝", Phase.PARKED, fin.phase)
    }

    @Test fun mirroredStageIsLeftHanded() {
        val r = Scenario.build(CarSpec.AVANTE, StageOpt(Kind.PERP_REV, Tier.MIDDLE, Neigh.CARS, Side.R))
        val l = Scenario.build(CarSpec.AVANTE, StageOpt(Kind.PERP_REV, Tier.MIDDLE, Neigh.CARS, Side.L))
        assertEquals(r.slot.c.y, -l.slot.c.y, 1e-9); assertEquals(r.start.y, -l.start.y, 1e-9)
        assertEquals('<', l.coach[1].cond); assertTrue(l.coach[1].text.contains("왼쪽으로 끝까지"))
        assertTrue("기어 말(D/R)은 뒤집지 않는다", l.coach[0].text.contains("비상등 켜고 D") && l.coach[1].text.contains("R."))
    }
}
