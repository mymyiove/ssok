package app.parkingcoach

import android.graphics.Color
import android.os.Bundle
import android.text.Html
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.parkingcoach.game.Coach
import app.parkingcoach.game.DemoDriver
import app.parkingcoach.game.GameEngine
import app.parkingcoach.game.GameSnapshot
import app.parkingcoach.game.HazStats
import app.parkingcoach.game.Music
import app.parkingcoach.game.Phase
import app.parkingcoach.game.Sfx
import app.parkingcoach.render.BrakeBarView
import app.parkingcoach.render.CameraView
import app.parkingcoach.render.ClusterData
import app.parkingcoach.render.ClusterView
import app.parkingcoach.render.MirrorView
import app.parkingcoach.render.PrepView
import app.parkingcoach.render.ReplayView
import app.parkingcoach.render.Scene
import app.parkingcoach.render.StalkView
import app.parkingcoach.render.TopView
import app.parkingcoach.render.WheelView
import app.parkingcoach.sim.CarSpec
import app.parkingcoach.sim.Kind
import app.parkingcoach.sim.Neigh
import app.parkingcoach.sim.Pose
import app.parkingcoach.sim.Scenario
import app.parkingcoach.sim.Side
import app.parkingcoach.sim.StageOpt
import app.parkingcoach.sim.Stages
import app.parkingcoach.sim.Tier
import app.parkingcoach.sim.Vec
import app.parkingcoach.vehicle.GearState
import app.parkingcoach.vehicle.PleosVehicleSource
import app.parkingcoach.vehicle.Signal
import app.parkingcoach.vehicle.SimulatedVehicleSource
import app.parkingcoach.vehicle.VehicleInputSource
import app.parkingcoach.vehicle.VehicleState
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 쏙 SSOK 화면(브라우저 시안 site/preview/index.html 과 같은 구성·흐름): 오프닝 → 홈(오늘의 상황 · 15 스테이지 · 졸업 코스 · 자유 연습 · [시연 보기][연습하기][점수 모드]) → 연습(카메라 · 어라운드뷰 · 클러스터 · 미러 하나) → 결과(기록 · 리플레이).
 * 세 모드(PM 2026-10-27): 시연 = 차가 공식대로 혼자, 연습 = 한 단계씩 시키고 확인(비상등 → D → 공식 단계 → 멈추고 P), 점수 = 코치 없이 기록.
 * 실차(Pleos)면 핸들·깜빡이·브레이크·비상등을 차에서 읽고 화면의 핸들·기어·브레이크는 표시만, 시뮬 모드(서비스 없음)면 화면 부품을 직접 조작한다.
 */
class MainActivity : AppCompatActivity() {
    // 화면은 1280×720 dp 기준. Pleos 에뮬레이터 2.1.0 실측(2026-10-26, PC 세션): 앱 창이 2560×1440 의 오른쪽 1682×1350 px · 160 dpi 라
    // 그대로면 글자·핸들·브레이크가 기준의 3/4 크기로 작다 → 창이 기준보다 크면 dp 를 키워 기준 폭에 맞춘다(여기선 ×1.31, 작은 화면·테스트는 그대로).
    override fun attachBaseContext(base: android.content.Context) {
        val c = base.resources.configuration
        val s = min(c.screenWidthDp / 1280f, c.screenHeightDp / 720f)
        if (s <= 1.05f) { super.attachBaseContext(base); return }
        val cfg = android.content.res.Configuration(c).apply {
            densityDpi = (c.densityDpi * s).toInt(); screenWidthDp = (c.screenWidthDp / s).toInt(); screenHeightDp = (c.screenHeightDp / s).toInt()
            smallestScreenWidthDp = (c.smallestScreenWidthDp / s).toInt()
        }
        super.attachBaseContext(base.createConfigurationContext(cfg))
    }
    private val prefs by lazy { getSharedPreferences("ssok", MODE_PRIVATE) }
    private lateinit var engine: GameEngine
    private lateinit var source: VehicleInputSource
    private lateinit var coach: Coach
    private lateinit var sfx: Sfx
    private var vs = VehicleState()
    private var sim = true
    private var spec = CarSpec.AVANTE
    private var stageIdx = 0
    private var seed = 1L
    private var running = false
    private var guide = true
    private var coachMode = "on"
    /** 모드: demo(시연 보기) · guide(연습하기, 기본) · score(점수 모드) */
    private var mode = "guide"
    private class Gd(var haz: Boolean = false, var d: Boolean = false, var lastStep: Int = -1)
    private var gd = Gd(); private var guideNow: GuideNow? = null
    private class GuideNow(val k: Int, val n: Int, val t: String, val say: String)
    /** 미러 각도는 쪽마다 따로(끌어 조정한 값, null = 기본 + R 연동 하향) */
    private val mirAdj = HashMap<Int, Pair<Double?, Double?>>()
    private var hintUntil = 0L
    private var demo: DemoDriver? = null; private var demoDone = false
    private var lastFrameNs = 0L
    private var wasDriving = false
    private var wasParked = false; private var lastPhase = Phase.READY; private var lastHazTotal = 0
    private var pdwNext = 0L; private var revNext = 0L
    private var saved = false; private var lastScore: GameEngine.Score? = null
    private var course: Course? = null
    private var toastUntil = 0L
    private var sound = true
    // 시뮬 모드 입력(화면 부품)
    private var simSteer = 0.0; private var simSignal = Signal.OFF; private var simBrakeD = 1.0; private var simHaz = false
    // 거울 맞추기
    private var mpPitch = 2.0; private var mpYaw = 32.0; private var mpEngine: GameEngine? = null; private var mpSnap: GameSnapshot? = null; private var mpDrag: FloatArray? = null

    private class Course(val list: List<Int>, var i: Int = 0, var sum: Double = 0.0, var pen: Double = 0.0, var clean: Boolean = true)
    private val FREE = Stages.ALL.size; private val DAILY = FREE + 1; private val COURSE = FREE + 2; private val DAILY3 = FREE + 3
    private fun isCourse() = stageIdx == COURSE || stageIdx == DAILY3   // 3개 연속(졸업 코스 · 오늘 3개)
    private val COACH_LABEL = mapOf("on" to "코치 켬", "hint" to "코치 힌트", "off" to "코치 끔")
    private val STEPS4 = listOf("S Signal", "S Stop", "O Orbit", "K Keep"); private val STEPS5 = listOf("S Signal", "S Stop", "O Orbit", "O Orbit", "K Keep")

    private fun <T : View> v(id: Int): T = findViewById(id)
    private fun col(id: Int) = getColor(id)
    private fun hex(id: Int) = "#%06X".format(0xFFFFFF and getColor(id))
    private var more = false
    private var clusterMsg = ""; private var clusterMsgUntil = 0L
    private lateinit var music: Music
    private fun clusterSay(t: String, ms: Long = 2000) { clusterMsg = t; clusterMsgUntil = System.currentTimeMillis() + ms }
    private var lastGear: String? = null
    private val stalk by lazy { v<StalkView>(R.id.stalk) }
    private val lastBg = HashMap<Int, Int>()
    /** 배경을 프레임마다 다시 넣으면 requestLayout 이 매 프레임 돈다 — 바뀔 때만 */
    private fun setBg(id: Int, res: Int) { if (lastBg[id] == res) return; lastBg[id] = res; v<View>(id).setBackgroundResource(res) }
    private fun setBgColor(id: Int, color: Int) { if (lastBg[id] == color) return; lastBg[id] = color; v<View>(id).setBackgroundColor(color) }
    private val lastText = HashMap<Int, CharSequence>()
    /** 프레임마다 같은 글을 다시 넣으면 재배치가 돈다(차 화면에서 낭비) — 바뀔 때만 */
    private fun setT(id: Int, t: CharSequence) { val prev = lastText[id]; if (prev != null && prev.toString() == t.toString()) return; lastText[id] = t; v<TextView>(id).text = t }
    private val cam by lazy { v<CameraView>(R.id.cam) }; private val top by lazy { v<TopView>(R.id.topView) }
    private val mir by lazy { v<MirrorView>(R.id.mir) }; private val prep by lazy { v<View>(R.id.prep) }; private val prepView by lazy { v<PrepView>(R.id.prepView) }
    private val wheel by lazy { v<WheelView>(R.id.wheel) }; private val brake by lazy { v<BrakeBarView>(R.id.brake) }
    private val setup by lazy { v<View>(R.id.setup) }; private val done by lazy { v<View>(R.id.done) }; private val tips by lazy { v<View>(R.id.tips) }; private val mirprac by lazy { v<View>(R.id.mirprac) }

    // ---- 오늘의 상황 · 졸업 코스 ----
    private data class Daily(val key: String, val seed: Long, val idx: Int, val label: String)
    private fun daily(): Daily { val c = Calendar.getInstance(); val y = c.get(Calendar.YEAR); val mo = c.get(Calendar.MONTH) + 1; val d = c.get(Calendar.DAY_OF_MONTH)
        val n = y * 10000L + mo * 100 + d; return Daily("%04d-%02d-%02d".format(y, mo, d), n, (n % Stages.ALL.size).toInt(), "$mo.$d") }
    /** 오늘 3개(맥락 간섭, 리서치 2026-10-27): 날짜 씨앗으로 초·중·고 하나씩 — 모두 같은 3개(시안 pickDaily3 와 같은 LCG) */
    private fun pickDaily3(): Course { var x = daily().seed; fun rnd(): Double { x = (x * 1103515245L + 12345L) % 2147483648L; return x / 2147483648.0 }
        return Course(Tier.ALL.map { t -> val l = Stages.ALL.indices.filter { Stages.ALL[it].tier == t }; l[(rnd() * l.size).toInt().coerceIn(0, l.size - 1)] }) }
    private fun newCourse(idx: Int) = if (idx == DAILY3) pickDaily3() else pickCourse()
    private fun pickCourse() = Course(Tier.ALL.map { t -> val l = Stages.ALL.indices.filter { Stages.ALL[it].tier == t }; l[(Math.random() * l.size).toInt().coerceIn(0, l.size - 1)] })
    private fun currentOpt(): StageOpt = when (stageIdx) {
        FREE -> StageOpt(Kind.values()[v<Spinner>(R.id.fKind).selectedItemPosition], Tier.ALL[v<Spinner>(R.id.fTier).selectedItemPosition], Neigh.values()[v<Spinner>(R.id.fNeigh).selectedItemPosition], Side.values()[v<Spinner>(R.id.fSide).selectedItemPosition], hazards = v<Spinner>(R.id.fHaz).selectedItemPosition)
        DAILY -> Stages.ALL[daily().idx]
        COURSE, DAILY3 -> (course ?: newCourse(stageIdx).also { course = it }).let { Stages.ALL[it.list[it.i]] }
        else -> Stages.ALL[stageIdx] }
    private fun newSeed() { seed = if (stageIdx == DAILY) daily().seed else if (stageIdx == DAILY3) daily().seed * 10 + (course?.i ?: 0) + 1 else (System.currentTimeMillis() % 1_000_000L) + 1 }
    private fun stripNo(t: String) = t.replace(Regex("^\\d+\\. "), "")
    private fun stripTier(t: String) = t.replace(Regex("^\\d+\\. \\[[^\\]]+\\] "), "")

    // ---- 리더보드(이 기기): 키 lb.<스테이지>.<차종>, 값 "total|name|date|pen|clean;..." ----
    private data class Rec(val total: Double, val name: String, val date: String, val pen: Double, val clean: Boolean)
    private fun lbKey(idx: Int = stageIdx) = "lb." + (when (idx) { FREE -> "free"; DAILY -> "daily." + daily().key; COURSE -> "course"; DAILY3 -> "daily3." + daily().key; else -> Stages.ALL[idx].no.toString() }) + "." + spec.key
    private fun lbList(key: String = lbKey()): MutableList<Rec> = prefs.getString(key, "")!!.split(";").filter { it.isNotBlank() }.mapNotNull { val f = it.split("|"); if (f.size < 5) null else Rec(f[0].toDouble(), f[1], f[2], f[3].toDouble(), f[4] == "1") }.sortedBy { it.total }.toMutableList()
    private fun lbRank(total: Double) = lbList().count { it.total < total } + 1
    private fun lbAdd(r: Rec): Int { val l = lbList(); l += r; l.sortBy { it.total }; val topL = l.take(10)
        prefs.edit().putString(lbKey(), topL.joinToString(";") { "%.2f|%s|%s|%.0f|%s".format(it.total, it.name, it.date, it.pen, if (it.clean) "1" else "0") }).apply(); return topL.indexOf(r) + 1 }
    private fun renderLb() { val l = lbList()
        v<TextView>(R.id.lbTitle).text = "기록 TOP 5 · ${when (stageIdx) { FREE -> "자유 연습"; DAILY -> "오늘의 상황 " + daily().label; COURSE -> "졸업 코스(3개 합계)"; DAILY3 -> "오늘 3개 " + daily().label + "(합계)"; else -> Stages.ALL[stageIdx].no.toString() + "번" }} · ${spec.name}"
        v<TextView>(R.id.lbBody).text = if (l.isEmpty()) "아직 기록 없음" else l.take(5).mapIndexed { i, r -> "%d. %-8s %6.1f s %s%s  %s".format(i + 1, r.name, r.total, if (r.pen > 0) "+%.0f".format(r.pen) else "", if (r.clean) " ✓" else "", r.date) }.joinToString("\n") }

    // ---- 홈 ----
    private fun stageBtn(title: String, sub: String, right: String, sel: Boolean, onClick: () -> Unit): View {
        val b = LinearLayout(this); b.orientation = LinearLayout.HORIZONTAL; b.gravity = android.view.Gravity.CENTER_VERTICAL; b.setBackgroundResource(if (sel) R.drawable.bg_stage_sel else R.drawable.bg_stage)
        val d = resources.displayMetrics.density; b.setPadding((10 * d).toInt(), (9 * d).toInt(), (10 * d).toInt(), (9 * d).toInt())
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.bottomMargin = (5 * d).toInt(); b.layoutParams = lp
        val no = TextView(this); no.text = title; no.setTextColor(col(if (sel) R.color.accent else R.color.fg2)); no.textSize = 12f; no.typeface = android.graphics.Typeface.MONOSPACE; no.minWidth = (30 * d).toInt()
        val t = TextView(this); t.text = sub; t.textSize = 13f; t.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val r = TextView(this); r.text = right; r.setTextColor(col(R.color.green)); r.textSize = 11f; r.typeface = android.graphics.Typeface.MONOSPACE
        b.addView(no); b.addView(t); b.addView(r); b.setOnClickListener { onClick() }; return b
    }
    private fun tierHead(t: String): View { val tv = TextView(this); tv.text = t; tv.setTextColor(col(R.color.fg2)); tv.textSize = 11f; val d = resources.displayMetrics.density; tv.setPadding(0, (10 * d).toInt(), 0, (6 * d).toInt()); return tv }
    private fun best(key: String) = lbList(key).firstOrNull()?.let { "%.1f s".format(it.total) } ?: ""
    private fun renderList() {
        val el = v<LinearLayout>(R.id.stageList); el.removeAllViews(); val cleared = prefs.getStringSet("cleared", emptySet()) ?: emptySet()
        fun pick(idx: Int) { if ((idx == COURSE || idx == DAILY3) && stageIdx != idx) course = newCourse(idx)
            if (idx == stageIdx && idx != FREE) { if (idx == COURSE || idx == DAILY3) course = newCourse(idx); load(false); start(); return }
            stageIdx = idx; renderList(); renderSetupPane(); v<ScrollView>(R.id.paneScroll).scrollTo(0, 0) }
        val dy = daily(); el.addView(tierHead("오늘의 상황 · ${dy.label}"))
        el.addView(stageBtn("📅", stripNo(Stages.ALL[dy.idx].title) + " · 모두 같은 상황", best(lbKey(DAILY)), stageIdx == DAILY) { pick(DAILY) })
        el.addView(stageBtn("3", "오늘 3개 · 초급 · 중급 · 고급 하나씩 섞어서 · 모두 같은 3개", best(lbKey(DAILY3)), stageIdx == DAILY3) { pick(DAILY3) })
        var lastTier = ""
        Stages.ALL.forEachIndexed { i, st -> if (st.tier.name != lastTier) { el.addView(tierHead(st.tier.name)); lastTier = st.tier.name }
            val b = best(lbKey(i)); el.addView(stageBtn("%02d".format(st.no), stripTier(st.title), if (b.isNotEmpty()) b else if (cleared.contains(st.no.toString())) "✓" else "", stageIdx == i) { pick(i) }) }
        el.addView(tierHead("졸업 코스")); el.addView(stageBtn("🎓", "초급 · 중급 · 고급 하나씩 연속 3개 · 합계가 기록", best(lbKey(COURSE)), stageIdx == COURSE) { pick(COURSE) })
        el.addView(tierHead("자유 연습")); el.addView(stageBtn("∞", "유형·난이도·옆 차·칸·돌발 직접 고르기", "", stageIdx == FREE) { pick(FREE) })
    }
    private fun renderSetupPane() {
        val opt = currentOpt(); val T = opt.tier
        v<View>(R.id.freeOpts).visibility = if (stageIdx == FREE) View.VISIBLE else View.GONE
        v<View>(R.id.startMistake).visibility = if (opt.kind == Kind.PERP_REV) View.VISIBLE else View.GONE
        v<TextView>(R.id.setupTitle).text = when (stageIdx) { FREE -> "자유 연습"; COURSE -> "졸업 코스 · " + course!!.list.joinToString(" → ") { Stages.ALL[it].no.toString() } + "번"; DAILY3 -> "오늘 3개 ${daily().label} · " + course!!.list.joinToString(" → ") { Stages.ALL[it].no.toString() } + "번"; DAILY -> "오늘의 상황 ${daily().label} · " + stripNo(opt.title); else -> stripNo(opt.title) }
        val what = when (opt.kind) { Kind.PERP_REV -> "R 로 넣는 후면 주차"; Kind.PERP_FWD -> if (opt.neighbors == Neigh.NONE) "D 로 넣는 전면 주차" else "한 번 빼서 맞추는 전면 주차"; Kind.PARALLEL -> "앞차 옆에서 R 평행 주차"; Kind.ANGLED -> "D 로 넣는 사선 주차" }   // ≤ 34자
        v<TextView>(R.id.setupNote).text = what   // 한 줄만(PM 2026-10-28 글자 최소화)
        // 상황 번호는 화면 어디에도 안 보인다(PM 2026-10-27) — seed 는 같은 상황 다시·기록에만
        for ((id, k) in listOf(R.id.carAvante to "AVANTE", R.id.carGrandeur to "GRANDEUR", R.id.carTucson to "TUCSON")) { v<Button>(id).setBackgroundResource(if (spec.key == k) R.drawable.bg_btn_on else R.drawable.bg_btn); v<Button>(id).setTextColor(col(if (spec.key == k) R.color.inkFg else R.color.fg)) }
        val pv = v<TopView>(R.id.preview); val sc = Scenario.build(spec, opt, seed); val pe = GameEngine(spec, seed = seed); pe.load(sc)
        pv.engine = pe; pv.snap = pe.tick(VehicleState(brake = true, gear = GearState.PARK, sourceName = "sim"), 0.0); pv.guide = false; pv.pdw = false; pv.ghost = true; pv.invalidate()   // 홈 미리보기에도 목표 자리 점선
        renderLb()
    }

    // ---- 로드 / 시작 / 홈 ----
    private fun load(keepSeed: Boolean, withDemo: Boolean = false, mistake: String? = null) {
        if (!keepSeed) newSeed()
        val opt = currentOpt(); var sc = Scenario.build(spec, opt, seed, noStartJitter = withDemo)
        sc = when (stageIdx) { FREE -> sc.copy(title = "자유 연습 · " + StageOpt(opt.kind, opt.tier, opt.neighbors, opt.side, hazards = opt.hazards).title); DAILY -> sc.copy(title = "오늘의 상황 ${daily().label} · " + sc.title); COURSE -> sc.copy(title = "졸업 코스 ${course!!.i + 1}/3 · " + sc.title); DAILY3 -> sc.copy(title = "오늘 3개 ${course!!.i + 1}/3 · " + sc.title); else -> sc }
        engine = GameEngine(spec, seed = seed); engine.load(sc); engine.autoPark = withDemo   // 사람은 P 로 끝(PM 2026-10-27), 시연만 저절로
        cam.engine = engine; top.engine = engine; mir.engine = engine; top.stopMark = null
        mir.side = if (opt.side == Side.L) -1 else 1; mirAdj.clear(); mir.pitchDeg = null; mir.yawDeg = null; mirLabel()   // 칸 쪽 미러가 먼저(PM 2026-10-27)
        demo = if (withDemo) DemoDriver(sc, opt, mistake) else null; demoDone = false
        top.ghostPoses = if (mode == "guide" && !withDemo) ghostPoses() else emptyList()   // 실시간 고스트 차(연습 모드)
        v<View>(R.id.demoBar).visibility = if (withDemo) View.VISIBLE else View.GONE; v<TextView>(R.id.demoCap).text = ""
        saved = false; lastScore = null
        v<TextView>(R.id.stageName).text = sc.title
        done.visibility = View.GONE; v<View>(R.id.hazBanner).visibility = View.GONE
        coachMode = if (mode == "score") "off" else if (mode == "guide" && opt.tier != Tier.BEGINNER) "hint" else "on"; syncCoachUi(); hintUntil = 0; gd = Gd(); guideNow = null; modeChip()   // 코치 페이딩(리서치 2026-10-27): 연습 모드 중급·고급은 단계가 바뀔 때 2.5초 + [힌트], 점수 모드는 끔
        simSignal = Signal.OFF; simBrakeD = 1.0; simHaz = false; simSteer = 0.0; brake.set(1.0, true); wheel.steer = 0.0; syncHaz()
        wasParked = false; lastPhase = Phase.READY; lastHazTotal = 0; lastGear = null
        renderSetupPane()
    }
    private fun envLabel(e: String) = when (e) { "underground" -> "지하"; "outdoor" -> "지상"; "night" -> "밤"; "rain" -> "비"; else -> e }
    private val help by lazy { v<View>(R.id.help) }; private val tour by lazy { v<View>(R.id.tour) }
    private fun start() { running = true; setup.visibility = View.GONE; tips.visibility = View.GONE; help.visibility = View.GONE; setDock(R.id.dPlay) }   // 화면 안내는 저절로 뜨지 않는다(PM 2026-10-28) — ⋯ › 화면 안내
    /** 첫 진입 화면 안내(PM 2026-10-27): 번호 표식 10개 + 설명 카드. 닫으면 다시 안 뜨고 ⋯ › 화면 안내 · 도움말에서 다시 */
    /** 첫 진입 화면 안내(PM 2026-10-27 '한 단계씩'): 시안 TOUR 와 같은 여섯 단계. 구멍이 대상으로 미끄러지고 말풍선은 아래 → 위 → 옆 순으로 들어가는 자리에. */
    private val TOUR = listOf(Triple(R.id.cam, "카메라", "노란선 = 갈 길 · 빨간선 = 0.5 m"), Triple(R.id.topView, "어라운드뷰", "노란선이 칸 안에 오면 핸들 풀기"), Triple(R.id.mirBox, "사이드 미러", "누르면 반대쪽 · 끌면 각도"), Triple(R.id.wheel, "핸들", "끌어서 돌려요"), Triple(R.id.stalk, "기어", "브레이크 밟고 ▲ D · ▼ R"), Triple(R.id.brake, "브레이크", "왼쪽 살살 · 오른쪽 세게"), Triple(R.id.pBtn, "P", "다 되면 멈추고 P"))   // 말풍선 ≤ 36자
    private var tourI = 0
    private fun tourOpen() { v<app.parkingcoach.render.SpotlightView>(R.id.tourSpot).reset(); tour.visibility = View.VISIBLE; tour.post { tourStep(0) } }
    private fun tourStep(i: Int) { tourI = i; val (id, t, d) = TOUR[i]; val target = v<View>(id); val spot = v<app.parkingcoach.render.SpotlightView>(R.id.tourSpot); spot.show(target)
        setT(R.id.tourT, t); setT(R.id.tourD, d); setT(R.id.tourDots, TOUR.indices.joinToString("") { if (it == i) "●" else "○" }); v<Button>(R.id.tourNext).text = if (i == TOUR.size - 1) "시작하기" else "다음"
        val bubble = v<View>(R.id.tourBubble); val dp = resources.displayMetrics.density; val gap = 12 * dp
        bubble.measure(View.MeasureSpec.makeMeasureSpec((300 * dp).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val bw = bubble.measuredWidth.toFloat(); val bh = bubble.measuredHeight.toFloat(); val W = tour.width.toFloat(); val H = tour.height.toFloat()
        val r = app.parkingcoach.render.SpotlightView.rectIn(tour, target); val x = r.left; val y = r.top; val w = r.width(); val h = r.height()
        var bx: Float; var by: Float
        if (y + h + gap + bh < H) { by = y + h + gap; bx = x + w / 2 - bw / 2 } else if (y - gap - bh > 0) { by = y - gap - bh; bx = x + w / 2 - bw / 2 } else { by = max(gap, min(H - bh - gap, y)); bx = if (x + w + gap < W - bw) x + w + gap else x - bw - gap }
        bx = max(gap, min(W - bw - gap, bx))
        (bubble.layoutParams as android.widget.FrameLayout.LayoutParams).apply { leftMargin = bx.toInt(); topMargin = by.toInt() }; bubble.requestLayout() }
    private fun tourClose() { tour.visibility = View.GONE; prefs.edit().putBoolean("tourSeen", true).apply() }
    private fun goHome() { running = false; demo = null; v<View>(R.id.demoBar).visibility = View.GONE; done.visibility = View.GONE; tips.visibility = View.GONE; mirprac.visibility = View.GONE; prep.visibility = View.GONE; setup.visibility = View.VISIBLE; setDock(R.id.dHome); renderList(); renderSetupPane() }
    // ---- 미러 하나(PM 2026-10-27): 탭 = 반대쪽, 각도는 쪽마다 ----
    private fun mirLabel() { setT(R.id.mirLabel, if (mir.side > 0) "오른쪽 미러" else "왼쪽 미러"); v<Button>(R.id.mirSw).text = if (mir.side > 0) "‹ 왼쪽" else "오른쪽 ›" }
    private fun mirFlip() { mirAdj[mir.side] = mir.pitchDeg to mir.yawDeg; mir.side = -mir.side; val a = mirAdj[mir.side]; mir.pitchDeg = a?.first; mir.yawDeg = a?.second; mirLabel(); sfx.gear() }
    /** 두 번 누름 = 원위치: 첫 탭이 이미 반대쪽으로 넘겼으니 소리 없이 되돌리고, 끌어 둔 이쪽 각도를 지운다(종전엔 넘어간 쪽만 지워져 끌어 둔 각도가 남고 화면은 반대쪽에 머물렀다 — 2026-10-27) */
    private fun mirReset() { mir.side = -mir.side; mirAdj.remove(mir.side); mir.pitchDeg = null; mir.yawDeg = null; mirLabel(); toast("미러 원위치") }
    // ---- 모드 칩(상단 바): 시연 · 연습 · 점수 ----
    private fun modeChip() { val c = v<TextView>(R.id.modeChip); val d = demo != null; c.text = if (d) "시연" else if (mode == "guide") "연습" else "점수"
        c.backgroundTintList = android.content.res.ColorStateList.valueOf(if (d) col(R.color.panel) else if (mode == "guide") col(R.color.accent) else col(R.color.ink)); c.setTextColor(if (d) col(R.color.fg2) else if (mode == "guide") Color.WHITE else col(R.color.inkFg)) }
    /** 연습하기 모드: 한 단계씩 시키고 한 것을 확인하면 다음. 앞 2단계(비상등 · D) + 공식 단계(엔진 coach) + 마지막(멈추고 P). 시안 guideSteps 와 같다 */
    private class GStep(val t: String, val say: String, val ok: String, val done: Boolean)
    private fun guideSteps(snap: GameSnapshot): List<GStep> { val c = engine.scenario.coach; val out = ArrayList<GStep>()
        out += GStep("비상등을 켜요", "비상등을 켜 보세요", "비상등", gd.haz); out += GStep("브레이크 밟고 ▲ 한 번, D", "브레이크를 밟은 채 위 버튼을 한 번 눌러 D 를 넣어요", "D", gd.d)
        c.forEachIndexed { i, st -> val t = st.guide.ifEmpty { st.text }; out += GStep(t, t.replace(Regex("\\([^)]*\\)"), ""), st.ok.ifEmpty { t.split(Regex("[.,—(]"))[0].trim() }, gd.d && snap.coachStep > i) }   // 연습 문장(~해 보세요)은 코치 문장과 다르다(PM 2026-10-27), 음성도 같은 글
        out += GStep("다 되면 멈추고 P", "다 들어왔으면 멈추고 P 를 눌러요", "P", snap.phase == Phase.PARKED || snap.phase == Phase.ENDED); return out }
    private fun setDock(id: Int) { for (k in listOf(R.id.dHome, R.id.dPlay, R.id.dLb, R.id.dTips, R.id.dMir)) { val b = v<Button>(k); b.isSelected = k == id; b.setBackgroundResource(if (k == id) R.drawable.bg_dock_cur else R.drawable.bg_dock) } }
    private fun syncCoachUi() { v<Button>(R.id.coachBtn).text = COACH_LABEL.getValue(coachMode); v<View>(R.id.hintBtn).visibility = if (coachMode == "hint") View.VISIBLE else View.GONE }
    private val hazOnScreen get() = sim || !vs.hazardFromCar   // 차에서 비상등을 못 읽으면(에뮬레이터 2.1.0) 실차 모드에서도 화면 버튼(PC 실측 2026-10-26)
    private var lastCarHaz = false
    private fun syncHaz() { val on = if (hazOnScreen) simHaz else vs.hazardLights; val b = v<Button>(R.id.hazBtn); setBg(R.id.hazBtn, if (on) R.drawable.bg_haz_btn_on else R.drawable.bg_haz_btn); b.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(if (on) Color.rgb(26, 18, 0) else Color.rgb(255, 90, 90)); b.setTextColor(if (on) Color.rgb(26, 18, 0) else Color.rgb(230, 232, 236)) }   // 실물 스위치: 검정 바탕 빨간 삼각, 켜면 주황
    /** 알림 통로(PM 2026-10-27): 달리는 중엔 계기판 팝업 한 곳, 그 밖엔 카메라 가운데 토스트 */
    private fun toast(msg: String) { if (running) { clusterSay(msg, 1600); return }; val t = v<TextView>(R.id.toast); t.text = msg; t.visibility = View.VISIBLE; toastUntil = System.currentTimeMillis() + 1400 }
    private fun userInput() { if (demo != null) load(true) }   // 핸들·레버를 건드리면 시연이 끝나고 같은 상황을 직접
    private fun setSignal(s: Signal) { if (simSignal != s) sfx.gear(); simSignal = s }

    private fun nextStage() {
        val c = course
        if (isCourse() && c != null) { if (c.i < 2) { c.i++; load(false); return }; course = newCourse(stageIdx); load(false); return }
        if (stageIdx == DAILY) stageIdx = daily().idx
        if (stageIdx != FREE) stageIdx = (stageIdx + 1) % Stages.ALL.size
        renderList(); load(false)
    }

    // ---- 결과 ----
    /** 공식 경로의 자세(4틱마다 ≈ 4.8 cm) — 실시간 고스트 차용(sim.js ghostPoses 와 같다) */
    private fun ghostPoses(): List<Pose> = try {
        val sc = engine.scenario; val e = GameEngine(spec, seed = seed); e.load(sc); val d = DemoDriver(sc, currentOpt()); val out = ArrayList<Pose>(); var t = 0.0; var k = 0
        while (t < 240) { t += 0.02; val inp = d.step(e, 0.02); val sn = e.tick(inp.copy(gear = GearState.PARK, sourceName = "demo"), 0.02); if (sn.v != 0.0 && k++ % 4 == 0) out += e.pose; if (sn.phase == Phase.PARKED || sn.phase == Phase.HIT) break }
        out } catch (e: Exception) { emptyList() }
    private fun ghostPath(): List<Vec> = try {
        val sc = engine.scenario; val e = GameEngine(spec, seed = seed); e.load(sc); val d = DemoDriver(sc, currentOpt()); val pts = ArrayList<Vec>(); var t = 0.0
        while (t < 240) { t += 0.02; val inp = d.step(e, 0.02); val sn = e.tick(inp.copy(gear = GearState.PARK, sourceName = "demo"), 0.02); if (sn.v != 0.0) pts += Vec(e.pose.x, e.pose.y); if (sn.phase == Phase.PARKED || sn.phase == Phase.HIT) break }
        pts } catch (e: Exception) { emptyList() }
    private fun showDone() {
        val eng = engine; val hz = eng.hazStats; val sc0 = eng.score(); var sc = sc0; done.visibility = View.VISIBLE
        val guide = mode == "guide"; v<View>(R.id.toScore).visibility = if (guide) View.VISIBLE else View.GONE; v<View>(R.id.recRow).visibility = if (guide) View.GONE else View.VISIBLE   // 연습 모드는 기록 없음(PM 2026-10-27)
        val ended = eng.phase == Phase.ENDED   // P 를 눌렀지만 칸에 못 들어감 — 기록 없음, 왜 아닌지만
        v<TextView>(R.id.gapsTxt).visibility = if (eng.inSlot) View.VISIBLE else View.GONE   // 칸 밖이면 간격이 뜻이 없다(검수 2026-10-27)
        if (eng.inSlot) run { val g = eng.gaps(); v<TextView>(R.id.gapsTxt).text = Html.fromHtml("내려서 보면 <b>왼쪽 ${g.left.cm} cm</b> <font color='${hex(R.color.fg2)}'>${g.left.what}</font> · <b>오른쪽 ${g.right.cm} cm</b> <font color='${hex(R.color.fg2)}'>${g.right.what}</font> · <b>${g.endLabel} ${g.end.cm} cm</b> <font color='${hex(R.color.fg2)}'>${g.end.what}</font>", Html.FROM_HTML_MODE_LEGACY) }
        for (id in listOf(R.id.nameRow, R.id.recClean, R.id.recRank, R.id.recPen, R.id.next)) v<View>(id).visibility = if (ended) View.GONE else View.VISIBLE
        if (ended) { lastScore = null; v<TextView>(R.id.doneTitle).text = "아직이에요"; v<TextView>(R.id.doneTxt).text = eng.hint.removePrefix("아직이에요. ") + " · %.1f s".format(eng.elapsed); v<TextView>(R.id.recBig).text = "%.1f s".format(eng.elapsed)
            v<TextView>(R.id.review).text = (listOf("칸에 완전히 들어가 나란해졌을 때 P 를 누르세요.") + eng.review()).joinToString("\n") { "• $it" }; v<ReplayView>(R.id.replay).set(eng.model, eng.scenario, eng.trace.toList(), ghostPath()); return }
        sfx.parked()
        val next = v<Button>(R.id.next); val c = course
        if (isCourse() && c != null) { c.sum += sc0.total; c.pen += sc0.pen; c.clean = c.clean && sc0.clean; val last = c.i >= 2
            if (last) sc = GameEngine.Score(c.sum - c.pen, c.pen, c.sum, sc0.corrections, sc0.noHazLights, c.clean)
            next.text = if (last) (if (stageIdx == DAILY3) "오늘 3개 다시 ▶" else "새 졸업 코스 ▶") else "다음 ${c.i + 2}/3 ▶"; v<View>(R.id.nameRow).visibility = if (last) View.VISIBLE else View.GONE }
        else { next.text = "다음 스테이지 ▶"; v<View>(R.id.nameRow).visibility = View.VISIBLE }
        if (guide) v<View>(R.id.nameRow).visibility = View.GONE
        lastScore = sc
        val T = eng.trace; val rev = T.count { it.v < 0 }
        v<TextView>(R.id.doneTitle).text = if (isCourse() && c != null && c.i >= 2) (if (sc.clean) (if (stageIdx == DAILY3) "오늘 3개 끝! 클린" else "졸업! 클린") else (if (stageIdx == DAILY3) "오늘 3개 끝!" else "졸업!")) else if (isCourse() && c != null) "${c.i + 1}/3 쏙" else if (sc.clean) "쏙! 클린" else if (eng.hits == 0 && hz.hit == 0) "쏙!" else "쏙! 들어갔지만 부딪혔어요"
        v<TextView>(R.id.doneTxt).text = "${eng.scenario.title} · 충돌 ${eng.hits}회" + (if (hz.total > 0) " · 돌발 멈춤 ${hz.avoided}/${hz.total} · 사고 ${hz.hit}" else "") + (if (sc.corrections > 0) " · 보정 ${sc.corrections}회" else "")
        v<TextView>(R.id.recBig).text = "%.1f s".format(sc.total) + (if (isCourse() && c != null) (if (c.i >= 2) "  (3개 합계)" else "  (누적 %.1f s)".format(c.sum)) else "")
        val parts = ArrayList<String>(); parts += "충돌 ${eng.hits}×5"; if (hz.hit > 0) parts += "사고 ${hz.hit}×15"; if (sc.corrections > 0) parts += "보정 ${sc.corrections}×3"; if (sc.noHazLights) parts += "비상등 안 켬 +2"
        v<TextView>(R.id.recPen).text = "시간 %.1f ÷ 2 = %.1f".format(eng.elapsed, sc.drive) + (if (sc.pen > 0) " + 실수 %.0f (%s)".format(sc.pen, parts.joinToString(" · ")) else "") + (if (sc.align > 0) " + 정렬 %.1f (각 ${sc.angDeg}° · 치우침 ${sc.offCm} cm)".format(sc.align) else " · 정렬 완벽")
        v<View>(R.id.recClean).visibility = if (sc.clean) View.VISIBLE else View.GONE
        val rank = lbRank(sc.total); v<TextView>(R.id.recRank).text = "이 기기 ${rank}위${if (rank == 1) " · 신기록" else ""}"
        v<EditText>(R.id.nameIn).setText(prefs.getString("name", "")); v<TextView>(R.id.saveMsg).text = ""; v<Button>(R.id.saveRec).isEnabled = true
        v<TextView>(R.id.review).text = eng.review().joinToString("\n") { "• $it" }
        v<ReplayView>(R.id.replay).set(eng.model, eng.scenario, T.toList(), ghostPath())
        val cleanStage = if (isCourse() && c != null) (if (sc0.clean) Stages.ALL[c.list[c.i]].no else null) else if (stageIdx != FREE && stageIdx != DAILY && sc.clean) Stages.ALL[stageIdx].no else null
        if (cleanStage != null) { val s = HashSet(prefs.getStringSet("cleared", emptySet()) ?: emptySet()); s += cleanStage.toString(); prefs.edit().putStringSet("cleared", s).apply() }
    }

    // ---- 타기 전 준비(PM 2026-10-27): 의자·핸들 · 룸미러 · 사이드미러. ✓ 넷은 prefs prep.* ----
    private fun prepOk(k: String) = prefs.getBoolean("prep.$k", false)
    private fun prepMark(k: String, v: Boolean) { if (prepOk(k) == v) return; prefs.edit().putBoolean("prep.$k", v).apply(); prepBadges() }
    private fun prepBadges() { for ((k, id) in listOf("seat" to R.id.pkSeat, "wheel" to R.id.pkWheel, "room" to R.id.pkRoom, "side" to R.id.pkSide)) { val on = prepOk(k); val t = v<TextView>(id)
        t.backgroundTintList = android.content.res.ColorStateList.valueOf(if (on) col(R.color.green) else col(R.color.panel)); t.setTextColor(if (on) Color.WHITE else col(R.color.muted)) } }
    private fun prepOpen(tab: String) {
        if (tab == "side") { tips.visibility = View.GONE; prep.visibility = View.GONE; mpOpen(); return }
        val room = tab == "room"; prepView.tab = if (room) "room" else "seat"
        for ((id, on) in listOf(R.id.prepTabSeat to !room, R.id.prepTabRoom to room)) { v<Button>(id).setBackgroundResource(if (on) R.drawable.bg_btn_on else R.drawable.bg_btn); v<Button>(id).setTextColor(col(if (on) R.color.inkFg else R.color.fg)) }
        setT(R.id.prepTitle, if (room) "룸미러" else if (tab == "wheel") "핸들 높이·거리" else "의자 위치")
        setT(R.id.prepSub, if (room) "거울을 끌어서 뒷유리가 한가운데 오게" else "의자를 좌우로, 핸들을 위아래·앞뒤로 끌어 보세요"); setT(R.id.prepHand, if (room) "거울을 끌어서 맞추기" else "의자 · 핸들을 끌어서 맞추기")
        setT(R.id.pp1, if (room) "1  뒷유리 전체가 거울 한가운데" else "1  브레이크를 끝까지 밟아도 무릎이 살짝 굽어요"); setT(R.id.pp2, if (room) "2  머리를 안 움직여도 뒤가 보여요" else "2  등을 붙이고 팔을 뻗으면 손목이 핸들 위에 닿아요")
        v<View>(R.id.pp3).visibility = if (room) View.GONE else View.VISIBLE
        tips.visibility = View.GONE; prep.visibility = View.VISIBLE; setDock(R.id.dTips); prepView.invalidate(); prepUpdate()
    }
    /** 판정 글: 시안 drawPrep 의 pp1~3 · prepAngle · prepPass 와 같은 규칙 */
    private fun prepUpdate() {
        if (prepView.tab == "room") { val ok = prepView.roomOk; for (id in listOf(R.id.pp1, R.id.pp2)) v<TextView>(id).setTextColor(col(if (ok) R.color.green else R.color.fg2))
            setT(R.id.prepAngle, "거울 좌우 ${if (prepView.mx > 0) "+" else ""}${Math.round(prepView.mx)} · 위아래 ${if (prepView.my > 0) "+" else ""}${Math.round(prepView.my)}"); setT(R.id.prepPass, if (ok) "✓ 룸미러 됐어요" else ""); prepMark("room", ok); return }
        val G = prepView.geom(); val u = G.u
        for ((id, ok) in listOf(R.id.pp1 to G.okKnee, R.id.pp2 to G.okReach, R.id.pp3 to G.okView)) v<TextView>(id).setTextColor(col(if (ok) R.color.green else R.color.fg2))
        setT(R.id.prepAngle, if (!G.reach) "의자가 너무 뒤 — 페달에 발이 안 닿아요" else "무릎 굽힘 ${Math.round(G.bend)}°" + (if (G.da > 2.5 * u - 1) " · 핸들이 멀어요" else if (G.da < 2.5 * u - 10) " · 핸들이 가까워요" else " · 팔 거리 좋아요") + (if (!G.okView) (if (G.W[1] + G.R >= G.K[1] - 6) " · 핸들이 무릎에 닿아요" else " · 계기판이 가려요") else ""))
        setT(R.id.prepPass, if (G.okKnee && G.okReach && G.okView) "✓ 의자·핸들 됐어요" else ""); prepMark("seat", G.okKnee); prepMark("wheel", G.okReach && G.okView)
    }

    // ---- 거울 맞추기 ----
    private fun mpOpen() {
        if (mpEngine == null) { val opt = Stages.ALL[5]; val sc = Scenario.build(spec, opt, 77, noStartJitter = true); val e = GameEngine(spec, seed = 77); e.load(sc); mpEngine = e
            val pose = Pose(sc.formulaX, sc.start.y, 0.0); mpSnap = GameSnapshot(Phase.DRIVING, pose, e.model.body(pose), 0.0, 0.0, "", 0, 0.0, 0, 1, true, null, HazStats(), null) }
        val m = v<MirrorView>(R.id.mpMirror); m.engine = mpEngine; m.snap = mpSnap; m.housing = false; m.noBcw = true; m.side = 1
        mirprac.visibility = View.VISIBLE; setDock(R.id.dMir); drawMp()
    }
    private fun drawMp() {
        val m = v<MirrorView>(R.id.mpMirror); m.pitchDeg = mpPitch; m.yawDeg = mpYaw; m.invalidate()
        val body = mpYaw in 12.0..26.0; val wheelOk = mpPitch in 11.0..24.0; val line = mpPitch >= 9 && mpYaw <= 28; prepMark("side", body && wheelOk && line)
        for ((id, ok) in listOf(R.id.mp1 to body, R.id.mp2 to wheelOk, R.id.mp3 to line)) v<TextView>(id).setTextColor(col(if (ok) R.color.green else R.color.fg2))
        v<TextView>(R.id.mpAngle).text = "아래로 %.0f° · 바깥으로 %.0f°".format(mpPitch, mpYaw)
        v<TextView>(R.id.mpPass).text = if (body && wheelOk && line) "✓ 주차 각도 완성. 뒷바퀴와 주차선이 같이 보여요" else if (mpPitch < 11) "거울을 더 아래로(↓ 끌기)" else if (mpYaw > 26) "거울을 안쪽으로(← 끌기), 차체가 보여야 거리감이 생겨요" else if (mpYaw < 12) "거울을 바깥으로(→ 끌기), 차체가 너무 많이 보여요" else ""
    }

    // ---- 프레임 루프 ----
    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(nowNs: Long) {
            val dt = if (lastFrameNs == 0L) 0.0 else ((nowNs - lastFrameNs) / 1e9).coerceAtMost(0.05); lastFrameNs = nowNs
            val now = System.currentTimeMillis()
            var inp = if (sim) VehicleState(steeringDeg = simSteer.toFloat(), signal = if (running) simSignal else Signal.OFF, brake = simBrakeD >= GameEngine.BRAKE_STOP, brakeDepth = simBrakeD.toFloat(), hazardLights = simHaz, gear = GearState.PARK, sourceName = "sim")
                      else vs.copy(signal = if (running) vs.signal else Signal.OFF, hazardLights = if (vs.hazardFromCar) vs.hazardLights else simHaz)
            if (!hazOnScreen && vs.hazardLights != lastCarHaz) { lastCarHaz = vs.hazardLights; syncHaz() }
            val d = demo
            if (d != null) { if (!sim && running && (abs(vs.steeringDeg) > 20f || vs.signal != Signal.OFF)) { load(true) }   // 실차에서 핸들·레버를 건드리면 시연 끝
                else { val di = d.step(engine, dt); inp = di.copy(signal = if (running) di.signal else Signal.OFF, gear = GearState.PARK)
                    simSteer = di.steeringDeg.toDouble(); simSignal = di.signal; simBrakeD = if (di.brake) 1.0 else 0.0; simHaz = di.hazardLights; brake.set(simBrakeD, di.brake); syncHaz()
                    setT(R.id.demoCap, d.caption)
                    if (d.done && !demoDone) { demoDone = true; if (d.mistake != null) sfx.hit() else { sfx.parked(); v<TextView>(R.id.demoCap).postDelayed({ if (demo != null) setT(R.id.demoCap, "이제 직접 해 보세요") }, 1200) } } } }
            val snap = engine.tick(inp, dt)
            if (snap.gearRefused && demo == null && sim) { simSignal = GameEngine.sigOfGear(snap.gear); clusterMsg = "브레이크를 밟고 변속하세요"; clusterMsgUntil = now + 2000; sfx.gear() }
            val steer = inp.steeringDeg.toDouble(); val blink = (now / 400) % 2 == 0L; val hazOn = inp.hazardLights
            cam.snap = snap; cam.steer = steer; cam.guide = guide; cam.invalidate()
            top.snap = snap; top.steer = steer; top.guide = guide; top.blink = hazOn && blink; top.invalidate()
            mir.snap = snap; mir.blink = hazOn && blink; mir.invalidate()
            wheel.steer = steer
            if (!sim) { brake.set(if (inp.brake) 1.0 else 0.0, inp.brake); syncHaz() }
            if (snap.gear != lastGear) { if (snap.gear == "R" && lastGear != null && mir.dip != "off") clusterSay(if (mir.dip == "both") "R 연동 미러 · 양쪽 내림" else "R 연동 미러 · 오른쪽 내림", 1500); lastGear = snap.gear }
            stalk.set(snap.gear)
            run { val fin = snap.phase == Phase.PARKED || snap.phase == Phase.ENDED; setBg(R.id.pBtn, if (fin) R.drawable.bg_pbtn_on else R.drawable.bg_pbtn); val pc = if (fin) Color.rgb(10, 11, 13) else Color.rgb(244, 245, 247); if (v<Button>(R.id.pBtn).currentTextColor != pc) v<Button>(R.id.pBtn).setTextColor(pc) }   // P 는 눌러 끝낸 뒤 켜진 채
            setT(R.id.camLabel, if (snap.reverse) "후방 카메라" else "전방 카메라")
            setT(R.id.kindLabel, when (engine.scenario.kind) { Kind.PARALLEL -> "평행 · 후진 주차"; Kind.ANGLED -> "사선 · 전진 주차"; else -> if (engine.scenario.forward) "전진 주차" else "후진 주차" })
            val banner = v<TextView>(R.id.hazBanner)
            if (snap.flash != null && snap.hazard != null) { banner.text = "${snap.hazard.type.emoji} ${snap.flash}! 브레이크"; banner.visibility = View.VISIBLE }
            if (snap.hazard == null) banner.visibility = View.GONE
            if (snap.hazStats.total != lastHazTotal) { lastHazTotal = snap.hazStats.total; if (snap.hazard != null) { sfx.hazard(); sfx.vibrate(longArrayOf(120, 60, 120, 60, 120)) } }
            if (snap.phase == Phase.HIT && lastPhase != Phase.HIT) { sfx.hit(); sfx.vibrate(longArrayOf(80, 40, 80)) }; lastPhase = snap.phase
            val coachText = engine.scenario.coach[min(snap.coachStep, engine.scenario.coach.size - 1)].text
            val alertOn = snap.hazard != null || snap.phase == Phase.HIT || snap.phase == Phase.PARKED || !engine.started || snap.inSlot || snap.hint != coachText
            val showCoach = demo == null && (alertOn || coachMode == "on" || (coachMode == "hint" && now < hintUntil))
            // 연습하기 모드: 한 단계씩, 한 것을 확인하면 ✓ 팝업 + 다음. 공식 자리 단계(k = 2)엔 어라운드뷰에 '여기서 멈춤' 점선
            if (mode == "guide" && demo == null && running) { if (hazOn) gd.haz = true; if (snap.gear == "D") gd.d = true
                val gs = guideSteps(snap); var k = gs.indexOfFirst { !it.done }; if (k < 0) k = gs.size - 1
                if (gd.lastStep >= 0 && k > gd.lastStep) { clusterSay("✓ " + gs[gd.lastStep].ok, 1400); sfx.gear() }
                if (k != gd.lastStep && coachMode == "hint") hintUntil = maxOf(hintUntil, now + 2500)   // 새 단계는 2.5초 보여 주고 사라진다
                val alert = snap.hazard != null || snap.phase == Phase.HIT; gd.lastStep = k; guideNow = GuideNow(k, gs.size, if (alert) snap.hint else gs[k].t, if (alert) snap.hint else gs[k].say) } else guideNow = null
            top.stopMark = if (guideNow?.k == 2) Vec(engine.scenario.formulaX, engine.scenario.start.y) else null
            top.ghostCar = if (mode == "guide" && demo == null && engine.started && !snap.inSlot && snap.phase != Phase.PARKED) top.ghostCarNow(snap.pose) else null
            val sens = Scene.sensors(snap, engine.scenario); val near = sens.filter { if (snap.reverse) it.du <= 0 else it.du >= 0 }.minOfOrNull { it.d } ?: 9.0
            v<app.parkingcoach.render.PdwView>(R.id.pdw).set(Scene.pdwLevel(near))
            if (running && near < 1.2 && snap.v != 0.0 && now > pdwNext) { if (near < 0.3) sfx.pdwCont() else sfx.pdw(); sfx.vibrate(longArrayOf(if (near < 0.3) 60 else 25)); pdwNext = now + (if (near < 0.3) 130 else if (near < 0.6) 300 else 650) }
            if (running && snap.gear == "R" && snap.v != 0.0 && now > revNext) { sfx.rev(); revNext = now + 650 }
            if (running && sound) { if (demo != null) coach.say(v<TextView>(R.id.demoCap).text.toString()) else if (snap.hazard != null && snap.flash != null) coach.say("${snap.hazard.type.label}! 멈춰요") else if (showCoach) coach.say(guideNow?.say ?: run { val st = engine.scenario.coach.getOrNull(snap.coachStep); if (st != null && snap.hint == st.text && st.guide.isNotEmpty()) st.guide else snap.hint }) }   // 글은 짧게, 말은 문장으로(PM 2026-10-27): 코치 줄 = text, 음성 = 연습 문장(guide)
            sfx.engine(running && sound && snap.phase != Phase.READY && snap.phase != Phase.PARKED && snap.phase != Phase.ENDED, kotlin.math.abs(snap.v) * 3.6)
            val sigOn = inp.signal
                        val realP = sim || vs.gear != GearState.OTHER
            // 클러스터 패널(시안 drawCluster 와 같은 값): 속도·가상 기어·깜빡이·브레이크·시간·핸들 각도
            v<ClusterView>(R.id.panel).set(ClusterData(kmh = abs(snap.v) * 3.6, gear = if (realP && snap.gear == "N" && !engine.started) "P" else snap.gear, realP = realP,
                sigL = (sigOn == Signal.LEFT || hazOn) && blink, sigR = (sigOn == Signal.RIGHT || hazOn) && blink, brakeD = snap.brakeDepth.toDouble(), latch = brake.latch,
                steer = steer, elapsed = snap.elapsed, hits = snap.hits, src = if (sim) "sim" else if (!vs.hazardFromCar) "pleos-haz" else "pleos", msg = if (now < clusterMsgUntil) clusterMsg else ""))
            v<View>(R.id.coach).alpha = if (showCoach) 1f else 0f
            val steps = if (snap.coachCount == 5) STEPS5 else STEPS4; val gn = guideNow
            setT(R.id.step, if (gn != null) "${gn.k + 1}/${gn.n}" else if (snap.phase == Phase.PARKED) "쏙 완료" else if (snap.inSlot) "K Keep" else steps.getOrElse(min(snap.coachStep, snap.coachCount - 1)) { "${min(snap.coachStep, snap.coachCount - 1) + 1}/${snap.coachCount}" })
            setT(R.id.hintTxt, gn?.t ?: snap.hint); val hc = col(if (snap.hazard != null) R.color.red else R.color.fg); if (v<TextView>(R.id.hintTxt).currentTextColor != hc) v<TextView>(R.id.hintTxt).setTextColor(hc)
            val cal = Calendar.getInstance(); setT(R.id.clock, "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)))
            run { val block = !sim && (vs.gear == GearState.OTHER || vs.driving); v<View>(R.id.gate).visibility = if (block) View.VISIBLE else View.GONE
                if (vs.driving != wasDriving) { wasDriving = vs.driving; if (vs.driving) { v<TextView>(R.id.gate).text = "주행 중에는 쓸 수 없어요.\n차를 세우고 P 에 두면 이어서 합니다."; if (music.playing) music.toggle(); sfx.engine(false, 0.0); coach.stop() } else v<TextView>(R.id.gate).text = GATE_P } }
            if (toastUntil > 0 && now > toastUntil) { v<View>(R.id.toast).visibility = View.GONE; toastUntil = 0 }
            val parkedNow = snap.phase == Phase.PARKED || snap.phase == Phase.ENDED; if (parkedNow && !wasParked && demo == null) showDone(); wasParked = parkedNow
            if (done.visibility == View.VISIBLE) v<ReplayView>(R.id.replay).invalidate()
            if (mirprac.visibility == View.VISIBLE) drawMp()
            if (demo != null && v<TextView>(R.id.modeChip).text != "시연") modeChip()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        coach = Coach(this); sfx = Sfx(this)
        sound = prefs.getBoolean("sound", true); sfx.enabled = sound; v<Button>(R.id.dSound).text = if (sound) "소리" else "무음"
        spec = CarSpec.byKey(prefs.getString("car", "AVANTE")); stageIdx = prefs.getInt("stage", 0).coerceIn(0, Stages.ALL.size)
        v<TextView>(R.id.gate).text = GATE_P
        fun <T> spin(sp: Spinner, items: List<T>, label: (T) -> String) { sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items.map(label)); sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onItemSelected(p: AdapterView<*>?, vv: View?, pos: Int, id: Long) { if (setup.visibility == View.VISIBLE) renderSetupPane() }; override fun onNothingSelected(p: AdapterView<*>?) {} } }
        spin(v(R.id.fKind), Kind.values().toList()) { it.label }; spin(v(R.id.fTier), Tier.ALL) { it.name }; spin(v(R.id.fNeigh), Neigh.values().toList()) { it.label }
        spin(v(R.id.fSide), Side.values().toList()) { it.label }; spin(v(R.id.fHaz), listOf(0, 1, 2)) { if (it == 0) "돌발 없음" else "돌발 ${it}회" }
        v<Spinner>(R.id.fTier).setSelection(1); v<Spinner>(R.id.fNeigh).setSelection(1)

        source = if (PleosVehicleSource.isAvailable(this)) PleosVehicleSource(this) else SimulatedVehicleSource()
        sim = source is SimulatedVehicleSource
        source.start { vs = it }
        wheel.interactive = sim; brake.interactive = sim

        engine = GameEngine(spec, seed = seed); newSeed(); load(true); renderList(); renderSetupPane(); setup.visibility = View.VISIBLE; setDock(R.id.dHome)

        // 클러스터 입력(시뮬 모드)
        wheel.onTouchStart = { userInput() }; wheel.onChange = { simSteer = it }; wheel.onLock = { sfx.lock() }
        brake.onTouchStart = { userInput() }; brake.onChange = { simBrakeD = it }
        // 컬럼 레버(시뮬): 끌거나 눌러 D/N/R. 실차는 P 고정 — 깜빡이 레버가 가상 기어
        stalk.locked = !sim; stalk.onGear = { g -> userInput(); setSignal(GameEngine.sigOfGear(g)) }; stalk.onLockedTouch = { toast("실차에서는 깜빡이 레버로") }
        v<Button>(R.id.gUp).setOnClickListener { stalk.step(1) }; v<Button>(R.id.gDn).setOnClickListener { stalk.step(-1) }
        // P = 주차 끝(PM 2026-10-27): 멈춘 뒤 누르면 결과(칸 안이면 쏙, 아니면 '아직'), 움직이는 중이면 패널 안내
        v<Button>(R.id.pBtn).setOnClickListener { if (demo != null) load(true); when (engine.finish()) { GameEngine.Finish.MOVING -> clusterSay("멈춘 뒤 P를 누르세요"); GameEngine.Finish.IDLE -> if (!engine.started) clusterSay("브레이크를 밟고 시작하세요"); GameEngine.Finish.OK -> { sfx.pclick(); sfx.vibrate(longArrayOf(30, 40, 30)) } } }
        // 음악(assets/music, PM SUNO 연주곡): ⏮ ▶/⏸ ⏭ + 더킹
        music = Music(this); music.onEmpty = { toast("음악 파일이 아직 없어요 (assets/music)") }; music.onState = { on -> val pb = v<Button>(R.id.mPlay); pb.text = if (on) "⏸" else "▶"; pb.isSelected = on; pb.setTextColor(if (on) Color.rgb(10, 11, 13) else Color.rgb(244, 245, 247)); val t = v<TextView>(R.id.mTitle); t.text = "♪ " + (if (on) music.title else "일시정지 · " + music.title); t.setCompoundDrawablesWithIntrinsicBounds(if (on) R.drawable.ic_dot_on else 0, 0, 0, 0) }   // ♪ = 노래라는 표시, 재생 중은 초록 ▶(PM 2026-10-27)
        if (!music.isEmpty) v<TextView>(R.id.mTitle).text = "♪ " + music.title
        sfx.onDuck = { music.duck() }; coach.onDuck = { music.duck() }
        v<Button>(R.id.mPlay).setOnClickListener { music.toggle() }; v<Button>(R.id.mPrev).setOnClickListener { music.prev() }; v<Button>(R.id.mNext).setOnClickListener { music.next() }
        // R 연동 미러: 양쪽(기본) · 오른쪽 · 끔 (⋯ 안, PM 2026-10-27 "왼쪽은 안 되나")
        val dipL = mapOf("both" to "양쪽", "right" to "오른쪽", "off" to "끔"); var dip = prefs.getString("mirDip", "both") ?: "both"; mir.dip = dip; v<Button>(R.id.dipBtn).text = "R 미러 " + dipL[dip]
        v<Button>(R.id.dipBtn).setOnClickListener { val o = listOf("both", "right", "off"); dip = o[(o.indexOf(dip) + 1) % 3]; prefs.edit().putString("mirDip", dip).apply(); mir.dip = dip; v<Button>(R.id.dipBtn).text = "R 미러 " + dipL[dip] }
        // 미러 하나: 탭 = 반대쪽(라벨 옆 칩도), 끌기 = 각도, 두 번 = 원위치
        mir.adjustable = true; mir.onReset = { mirReset() }; mir.onTap = { mirFlip() }; v<Button>(R.id.mirSw).setOnClickListener { mirFlip() }
        v<Button>(R.id.hazBtn).setOnClickListener { if (!hazOnScreen) { toast("실차에서는 비상등 버튼으로"); return@setOnClickListener }; userInput(); simHaz = !simHaz; syncHaz() }
        v<Button>(R.id.demoBtn).setOnClickListener { load(true, true); start() }
        v<Button>(R.id.demoStop).setOnClickListener { mode = "guide"; load(true) }   // 시연 → 직접 하기 = 연습하기(검수 2026-10-27)
        v<Button>(R.id.hintBtn).setOnClickListener { hintUntil = System.currentTimeMillis() + 3000 }
        v<Button>(R.id.coachBtn).setOnClickListener { val order = listOf("on", "hint", "off"); coachMode = order[(order.indexOf(coachMode) + 1) % 3]; syncCoachUi() }
        v<Button>(R.id.guideBtn).setOnClickListener { guide = !guide; v<Button>(R.id.guideBtn).setBackgroundResource(if (guide) R.drawable.bg_btn_on else R.drawable.bg_btn); v<Button>(R.id.guideBtn).setTextColor(col(if (guide) R.color.inkFg else R.color.fg)) }
        // ⋯ = 자주 안 쓰는 버튼(코치 모드·가이드라인)을 펼친다(시안 #btns.open)
        v<Button>(R.id.moreBtn).setOnClickListener { more = !more; for (id in listOf(R.id.guideBtn, R.id.dipBtn, R.id.tourBtn)) v<View>(id).visibility = if (more) View.VISIBLE else View.GONE; v<Button>(R.id.moreBtn).setBackgroundResource(if (more) R.drawable.bg_btn_on else R.drawable.bg_btn); v<Button>(R.id.moreBtn).setTextColor(col(if (more) R.color.inkFg else R.color.fg)) }
        v<Button>(R.id.resetBtn).setOnClickListener { load(true) }
        // 카메라 화면을 끌어도 핸들(시뮬)
        var camDrag: FloatArray? = null
        cam.setOnTouchListener { vw, e -> if (!sim) return@setOnTouchListener false
            when (e.actionMasked) { MotionEvent.ACTION_DOWN -> { userInput(); camDrag = floatArrayOf(e.x, simSteer.toFloat()); vw.parent.requestDisallowInterceptTouchEvent(true) }
                MotionEvent.ACTION_MOVE -> camDrag?.let { simSteer = (it[1] + (e.x - it[0]) * 2.2).toDouble().coerceIn(-540.0, 540.0) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { camDrag = null; vw.performClick() } }
            true }
        // 홈
        for ((id, k) in listOf(R.id.carAvante to CarSpec.AVANTE, R.id.carGrandeur to CarSpec.GRANDEUR, R.id.carTucson to CarSpec.TUCSON)) v<Button>(id).setOnClickListener { spec = k; prefs.edit().putString("car", spec.key).apply(); renderList(); renderSetupPane() }
        v<Button>(R.id.startBtn).setOnClickListener { prefs.edit().putInt("stage", min(stageIdx, Stages.ALL.size)).apply(); mode = "score"; load(false); start() }
        v<Button>(R.id.startGuide).setOnClickListener { prefs.edit().putInt("stage", min(stageIdx, Stages.ALL.size)).apply(); mode = "guide"; load(false); start() }
        v<Button>(R.id.startDemo).setOnClickListener { mode = "demo"; load(false, true); start() }
        v<Button>(R.id.startMistake).setOnClickListener { mode = "demo"; load(false, true, "early"); start() }   // 실수 보기(오류 훈련)
        v<Button>(R.id.toScore).setOnClickListener { mode = "score"; load(true); start() }
        // 결과
        v<Button>(R.id.again).setOnClickListener { load(true) }; v<Button>(R.id.againNew).setOnClickListener { load(false) }
        v<Button>(R.id.next).setOnClickListener { nextStage() }; v<Button>(R.id.toMenu).setOnClickListener { goHome() }
        v<Button>(R.id.rpPlay).setOnClickListener { v<ReplayView>(R.id.replay).play() }
        v<Button>(R.id.saveRec).setOnClickListener { val ls = lastScore ?: return@setOnClickListener; if (saved) return@setOnClickListener
            val name = v<EditText>(R.id.nameIn).text.toString().ifBlank { "—" }.take(8).uppercase(); prefs.edit().putString("name", name).apply()
            val cal = Calendar.getInstance(); val date = "%02d.%02d".format(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
            val rank = lbAdd(Rec(Math.round(ls.total * 100) / 100.0, name, date, ls.pen, ls.clean)); saved = true; v<Button>(R.id.saveRec).isEnabled = false
            v<TextView>(R.id.saveMsg).text = if (rank > 0) "${rank}위로 저장됐어요" else "TOP 10 밖이라 순위에는 안 들어요"; renderList(); renderLb() }
        // 독
        v<Button>(R.id.dHome).setOnClickListener { goHome() }
        v<Button>(R.id.dLb).setOnClickListener { goHome(); setDock(R.id.dLb); v<ScrollView>(R.id.paneScroll).post { v<ScrollView>(R.id.paneScroll).smoothScrollTo(0, v<View>(R.id.lbTitle).top) } }
        v<Button>(R.id.dPlay).setOnClickListener { if (setup.visibility == View.VISIBLE) { load(false); start() } else { tips.visibility = View.GONE; mirprac.visibility = View.GONE; prep.visibility = View.GONE; setDock(R.id.dPlay) } }
        v<Button>(R.id.dTips).setOnClickListener { val show = tips.visibility != View.VISIBLE; tips.visibility = if (show) View.VISIBLE else View.GONE; prep.visibility = View.GONE; mirprac.visibility = View.GONE; setDock(if (show) R.id.dTips else if (running) R.id.dPlay else R.id.dHome) }
        v<Button>(R.id.tipsClose).setOnClickListener { tips.visibility = View.GONE; setDock(if (running) R.id.dPlay else R.id.dHome) }
        // 도움말 페이지 · 화면 안내
        v<TextView>(R.id.helpTxt).text = Html.fromHtml(HELP_HTML, Html.FROM_HTML_MODE_LEGACY)
        v<Button>(R.id.dHelp).setOnClickListener { val show = help.visibility != View.VISIBLE; help.visibility = if (show) View.VISIBLE else View.GONE; tips.visibility = View.GONE }
        v<Button>(R.id.helpClose).setOnClickListener { help.visibility = View.GONE }
        v<Button>(R.id.tourX).setOnClickListener { tourClose() }; v<Button>(R.id.tourNext).setOnClickListener { if (tourI >= TOUR.size - 1) tourClose() else tourStep(tourI + 1) }
        v<Button>(R.id.tourBtn).setOnClickListener { more = false; for (id in listOf(R.id.guideBtn, R.id.dipBtn, R.id.tourBtn)) v<View>(id).visibility = View.GONE; tourOpen() }
        v<TextView>(R.id.tipsHead).text = Html.fromHtml(TIPS_HEAD_HTML, Html.FROM_HTML_MODE_LEGACY); v<TextView>(R.id.tipsTxt).text = Html.fromHtml(TIPS_HTML, Html.FROM_HTML_MODE_LEGACY)
        // 타기 전 준비: 팁 카드 [해 보기] → 옆모습 운전석(의자·핸들) · 룸미러 · 사이드미러(거울 맞추기)
        for ((id, k) in listOf(R.id.prepSeatBtn to "seat", R.id.prepWheelBtn to "wheel", R.id.prepRoomBtn to "room", R.id.prepSideBtn to "side")) v<Button>(id).setOnClickListener { prepOpen(k) }
        v<Button>(R.id.prepTabSeat).setOnClickListener { prepOpen("seat") }; v<Button>(R.id.prepTabRoom).setOnClickListener { prepOpen("room") }; v<Button>(R.id.prepTabSide).setOnClickListener { prepOpen("side") }
        v<Button>(R.id.prepShuffle).setOnClickListener { if (prepView.tab == "room") { prepView.mx = (Math.random() - 0.5) * 70; prepView.my = (Math.random() - 0.5) * 50 } else { prepView.seat = Math.random(); prepView.wx = Math.random(); prepView.wy = Math.random() }; prepView.invalidate(); prepUpdate() }
        v<Button>(R.id.prepClose).setOnClickListener { prep.visibility = View.GONE; tips.visibility = View.VISIBLE; setDock(R.id.dTips) }
        prepView.onChange = { prepUpdate() }; prepBadges()
        v<Button>(R.id.dSound).setOnClickListener { sound = !sound; sfx.enabled = sound; prefs.edit().putBoolean("sound", sound).apply(); v<Button>(R.id.dSound).text = if (sound) "소리" else "무음"; v<Button>(R.id.dSound).alpha = if (sound) 1f else 0.45f; if (sound) sfx.gear() }
        // 거울 맞추기
        v<Button>(R.id.dMir).setOnClickListener { if (mirprac.visibility == View.VISIBLE) v<Button>(R.id.mpClose).performClick() else mpOpen() }
        v<Button>(R.id.mpClose).setOnClickListener { mirprac.visibility = View.GONE; tips.visibility = View.VISIBLE; setDock(R.id.dTips) }   // 팁 › 타기 전 준비로 돌아간다
        v<Button>(R.id.mpUsual).setOnClickListener { mpPitch = 6.0; mpYaw = 18.0 }; v<Button>(R.id.mpPark).setOnClickListener { mpPitch = 15.0; mpYaw = 18.0 }
        v<Button>(R.id.mpReset).setOnClickListener { mpPitch = 2 + Math.random() * 4; mpYaw = 30 + Math.random() * 10 }
        v<MirrorView>(R.id.mpMirror).setOnTouchListener { vw, e -> when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { mpDrag = floatArrayOf(e.x, e.y, mpPitch.toFloat(), mpYaw.toFloat()); vw.parent.requestDisallowInterceptTouchEvent(true) }
            MotionEvent.ACTION_MOVE -> mpDrag?.let { mpYaw = (it[3] + (e.x - it[0]) * 0.12).toDouble().coerceIn(0.0, 45.0); mpPitch = (it[2] + (e.y - it[1]) * 0.12).toDouble().coerceIn(-5.0, 30.0) }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { mpDrag = null; vw.performClick() } }; true }
        // 오프닝
        val opening = v<View>(R.id.opening); opening.setOnClickListener { opening.visibility = View.GONE }; opening.postDelayed({ opening.visibility = View.GONE }, 4200)
    }

    // 프레임 루프는 보이는 동안(onStart~onStop). Pleos 는 깜빡이를 켜면 사각지대 카메라 창(CarEvsBlindSpotCameraPreviewActivity)을 띄워
    // 이 앱이 보이는 채로 onPause 된다(에뮬레이터 2.1.0 실측) — onResume/onPause 로 묶으면 D·R 을 넣는 순간 게임이 멈춘다.
    override fun onStart() { super.onStart(); lastFrameNs = 0L; Choreographer.getInstance().postFrameCallback(frame) }
    override fun onStop() { super.onStop(); Choreographer.getInstance().removeFrameCallback(frame); sfx.engine(false, 0.0) }
    override fun onDestroy() { super.onDestroy(); source.stop(); coach.release(); music.release() }

    companion object {
        const val GATE_P = "주차 연습은 기어 P 에서만 할 수 있어요.\n차를 세우고 P 에 두면 이어서 합니다."
        const val HELP_HTML = """<big><b>도움말</b></big> <font color='#9aa3b2'>화면 · 조작 · 결과</font><br><br>
<b><font color='#00AAD2'>1 카메라</font></b><br><b>노란선</b> 갈 길 · <b>빨간선</b> 0.5 m<br><br>
<b><font color='#00AAD2'>2 어라운드뷰</font></b><br><b>점선</b> 들어갈 자리 · <b>삼각</b> 앞코<br><br>
<b><font color='#00AAD2'>3 사이드 미러</font></b><br>누르면 반대쪽 · 끌면 각도<br><br>
<b><font color='#00AAD2'>4 계기판</font></b><br>속도 · 기어 · 핸들 각도<br><br>
<b><font color='#00AAD2'>5 핸들</font></b><br>끌어서 돌리기 · 두 번 누르면 가운데<br><br>
<b><font color='#00AAD2'>6 기어</font></b><br><b>브레이크 밟고</b> ▲ D · ▼ R<br><br>
<b><font color='#00AAD2'>7 P</font></b><br>다 되면 <b>멈추고 P</b><br><br>
<b><font color='#00AAD2'>8 비상등 · 음악</font></b><br>주차 전 비상등 켜기<br><br>
<b><font color='#00AAD2'>9 브레이크</font></b><br>왼쪽 살살 · 오른쪽 세게<br><br>
<b><font color='#00AAD2'>10 위 줄 버튼</font></b><br>시연 · 다시 · ⋯ 설정<br><br>
<b><font color='#00AAD2'>11 세 모드</font></b><br>시연 · 연습 · 점수<br><br>
<b><font color='#00AAD2'>12 타기 전 준비</font></b><br>의자 → 핸들 → 거울<br><br><b><font color='#00AAD2'>13 기록</font></b><br>시간 ÷ 2 + 실수<br><br>"""
        const val TIPS_HEAD_HTML = """<big><b>초보 팁</b></big> <font color='#9aa3b2'>주차 전에 1분. 의자·거울 먼저</font><br><br>
<b><font color='#00AAD2'>00 쏙 공식 S · S · O · K</font></b><br>SSOK 네 글자가 곧 주차 순서.<br>
<b>S Signal</b> — 비상등을 켜서 주차할 것임을 주변에 알립니다.<br>
<b>S Stop</b> — 핸들을 돌릴 자리에 멈춥니다.<br>
<b>O Orbit</b> — 천천히 선회합니다.<br>
<b>K Keep</b> — 유지하며 선을 맞춰 정지합니다.<br>
<b>쏙하고 들어오면 주차 끝!</b><br><font color='#9aa3b2'>정답은 없어요. 내 방법을 찾아요.</font>"""
        const val TIPS_HTML = """<b><font color='#00AAD2'>05 후방카메라 선 읽기</font></b><br>• <b>노란선</b> 갈 길<br>• <b>파란 점선</b> 핸들 가운데<br>• <b>빨간선</b> 0.5 m에서 정지<br><br>
<b><font color='#00AAD2'>06 주차 전 순서</font></b><br>• <b>비상등</b><br>• D 로 <b>지나치기</b><br>• 멈추고 R, <b>핸들 끝까지</b><br>• 노란선이 칸 안 → 풀기<br>• 사람 보이면 <b>브레이크</b>"""
    }
}
