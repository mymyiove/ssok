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
import app.parkingcoach.game.Phase
import app.parkingcoach.game.Sfx
import app.parkingcoach.render.BrakeBarView
import app.parkingcoach.render.CameraView
import app.parkingcoach.render.ClusterData
import app.parkingcoach.render.ClusterView
import app.parkingcoach.render.MirrorView
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
import kotlin.math.min

/**
 * 쏙 SSOK 화면(브라우저 시안 site/preview/index.html 과 같은 구성·흐름): 오프닝 → 홈(오늘의 상황 · 15 스테이지 · 졸업 코스 · 자유 연습) → 연습(네 화면 + 클러스터) → 결과(기록 · 리플레이).
 * 실차(Pleos)면 핸들·깜빡이·브레이크·비상등을 차에서 읽고 화면의 핸들·기어·브레이크는 표시만, 시뮬 모드(서비스 없음)면 화면 부품을 직접 조작한다.
 */
class MainActivity : AppCompatActivity() {
    /** 화면 밀도 맞춤(실측 2026-10-26, Pleos 에뮬레이터): 앱 창이 2560×1440 전체가 아니라 오른쪽 큰 창이라 160dpi 기준 dp 가 아주 작게 보였다.
     *  첫 배치 뒤 창 크기를 재서 '1280×720 기준 화면이 창에 맞게' 밀도를 바꾸고 한 번 다시 만든다(시안과 같은 비율). */
    override fun attachBaseContext(base: android.content.Context) {
        val dpi = dpiOverride
        if (dpi > 0) { val cfg = android.content.res.Configuration(base.resources.configuration); cfg.densityDpi = dpi; super.attachBaseContext(base.createConfigurationContext(cfg)) } else super.attachBaseContext(base)
    }
    private fun fitDensity() {
        if (dpiOverride > 0) return
        val root = window.decorView; root.post {
            val w = root.width; val h = root.height; if (w <= 0 || h <= 0) return@post
            val cur = resources.displayMetrics.densityDpi
            val target = Math.round(160f * Math.min(w / DESIGN_W, h / DESIGN_H))   // 창이 1280×720 dp 로 보이게 하는 밀도
            if (target > 0 && Math.abs(target - cur) > cur * 0.08) { dpiOverride = target; recreate() } else dpiOverride = cur
        }
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
    private var coachMode = "on"; private var coachOverride: String? = null
    private var hintUntil = 0L
    private var demo: DemoDriver? = null; private var demoDone = false
    private var lastFrameNs = 0L
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
    private val FREE = Stages.ALL.size; private val DAILY = FREE + 1; private val COURSE = FREE + 2
    private val COACH_BY_TIER = mapOf("초급" to "on", "중급" to "hint", "고급" to "off"); private val COACH_LABEL = mapOf("on" to "코치 켬", "hint" to "코치 힌트", "off" to "코치 끔")
    private val STEPS4 = listOf("S 신호", "S 자리", "O 돌리기", "K 끝"); private val STEPS5 = listOf("S 신호", "S 자리", "O 돌리기", "O 보정", "K 끝")

    private fun <T : View> v(id: Int): T = findViewById(id)
    private fun col(id: Int) = getColor(id)
    private fun hex(id: Int) = "#%06X".format(0xFFFFFF and getColor(id))
    private var more = false
    private var clusterMsg = ""; private var clusterMsgUntil = 0L
    private val stalk by lazy { v<StalkView>(R.id.stalk) }
    private val lastBg = HashMap<Int, Int>()
    /** 배경을 프레임마다 다시 넣으면 requestLayout 이 매 프레임 돈다 — 바뀔 때만 */
    private fun setBg(id: Int, res: Int) { if (lastBg[id] == res) return; lastBg[id] = res; v<View>(id).setBackgroundResource(res) }
    private fun setBgColor(id: Int, color: Int) { if (lastBg[id] == color) return; lastBg[id] = color; v<View>(id).setBackgroundColor(color) }
    private val lastText = HashMap<Int, CharSequence>()
    /** 프레임마다 같은 글을 다시 넣으면 재배치가 돈다(차 화면에서 낭비) — 바뀔 때만 */
    private fun setT(id: Int, t: CharSequence) { val prev = lastText[id]; if (prev != null && prev.toString() == t.toString()) return; lastText[id] = t; v<TextView>(id).text = t }
    private val cam by lazy { v<CameraView>(R.id.cam) }; private val top by lazy { v<TopView>(R.id.topView) }
    private val mirL by lazy { v<MirrorView>(R.id.mirL) }; private val mirR by lazy { v<MirrorView>(R.id.mirR) }
    private val wheel by lazy { v<WheelView>(R.id.wheel) }; private val brake by lazy { v<BrakeBarView>(R.id.brake) }
    private val setup by lazy { v<View>(R.id.setup) }; private val done by lazy { v<View>(R.id.done) }; private val tips by lazy { v<View>(R.id.tips) }; private val mirprac by lazy { v<View>(R.id.mirprac) }

    // ---- 오늘의 상황 · 졸업 코스 ----
    private data class Daily(val key: String, val seed: Long, val idx: Int, val label: String)
    private fun daily(): Daily { val c = Calendar.getInstance(); val y = c.get(Calendar.YEAR); val mo = c.get(Calendar.MONTH) + 1; val d = c.get(Calendar.DAY_OF_MONTH)
        val n = y * 10000L + mo * 100 + d; return Daily("%04d-%02d-%02d".format(y, mo, d), n, (n % Stages.ALL.size).toInt(), "$mo.$d") }
    private fun pickCourse() = Course(Tier.ALL.map { t -> val l = Stages.ALL.indices.filter { Stages.ALL[it].tier == t }; l[(Math.random() * l.size).toInt().coerceIn(0, l.size - 1)] })
    private fun currentOpt(): StageOpt = when (stageIdx) {
        FREE -> StageOpt(Kind.values()[v<Spinner>(R.id.fKind).selectedItemPosition], Tier.ALL[v<Spinner>(R.id.fTier).selectedItemPosition], Neigh.values()[v<Spinner>(R.id.fNeigh).selectedItemPosition], Side.values()[v<Spinner>(R.id.fSide).selectedItemPosition], hazards = v<Spinner>(R.id.fHaz).selectedItemPosition)
        DAILY -> Stages.ALL[daily().idx]
        COURSE -> Stages.ALL[course!!.list[course!!.i]]
        else -> Stages.ALL[stageIdx] }
    private fun newSeed() { seed = if (stageIdx == DAILY) daily().seed else (System.currentTimeMillis() % 1_000_000L) + 1 }
    private fun stripNo(t: String) = t.replace(Regex("^\\d+\\. "), "")
    private fun stripTier(t: String) = t.replace(Regex("^\\d+\\. \\[[^\\]]+\\] "), "")

    // ---- 리더보드(이 기기): 키 lb.<스테이지>.<차종>, 값 "total|name|date|pen|clean;..." ----
    private data class Rec(val total: Double, val name: String, val date: String, val pen: Double, val clean: Boolean)
    private fun lbKey(idx: Int = stageIdx) = "lb." + (when (idx) { FREE -> "free"; DAILY -> "daily." + daily().key; COURSE -> "course"; else -> Stages.ALL[idx].no.toString() }) + "." + spec.key
    private fun lbList(key: String = lbKey()): MutableList<Rec> = prefs.getString(key, "")!!.split(";").filter { it.isNotBlank() }.mapNotNull { val f = it.split("|"); if (f.size < 5) null else Rec(f[0].toDouble(), f[1], f[2], f[3].toDouble(), f[4] == "1") }.sortedBy { it.total }.toMutableList()
    private fun lbRank(total: Double) = lbList().count { it.total < total } + 1
    private fun lbAdd(r: Rec): Int { val l = lbList(); l += r; l.sortBy { it.total }; val topL = l.take(10)
        prefs.edit().putString(lbKey(), topL.joinToString(";") { "%.2f|%s|%s|%.0f|%s".format(it.total, it.name, it.date, it.pen, if (it.clean) "1" else "0") }).apply(); return topL.indexOf(r) + 1 }
    private fun renderLb() { val l = lbList()
        v<TextView>(R.id.lbTitle).text = "기록 TOP 5 · ${when (stageIdx) { FREE -> "자유 연습"; DAILY -> "오늘의 상황 " + daily().label; COURSE -> "졸업 코스(3개 합계)"; else -> Stages.ALL[stageIdx].no.toString() + "번" }} · ${spec.name} · 이 기기"
        v<TextView>(R.id.lbBody).text = if (l.isEmpty()) "아직 기록이 없어요. 첫 기록을 남겨 보세요." else l.take(5).mapIndexed { i, r -> "%d. %-8s %6.1f s %s%s  %s".format(i + 1, r.name, r.total, if (r.pen > 0) "+%.0f".format(r.pen) else "", if (r.clean) " ✓" else "", r.date) }.joinToString("\n") }

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
        fun pick(idx: Int) { if (idx == COURSE && stageIdx != COURSE) course = pickCourse()
            if (idx == stageIdx && idx != FREE) { if (idx == COURSE) course = pickCourse(); load(false); start(); return }
            stageIdx = idx; renderList(); renderSetupPane(); v<ScrollView>(R.id.paneScroll).scrollTo(0, 0) }
        val dy = daily(); el.addView(tierHead("오늘의 상황 · ${dy.label}"))
        el.addView(stageBtn("📅", stripNo(Stages.ALL[dy.idx].title) + " · 모두 같은 상황", best(lbKey(DAILY)), stageIdx == DAILY) { pick(DAILY) })
        var lastTier = ""
        Stages.ALL.forEachIndexed { i, st -> if (st.tier.name != lastTier) { el.addView(tierHead(st.tier.name)); lastTier = st.tier.name }
            val b = best(lbKey(i)); el.addView(stageBtn("%02d".format(st.no), stripTier(st.title), if (b.isNotEmpty()) b else if (cleared.contains(st.no.toString())) "✓" else "", stageIdx == i) { pick(i) }) }
        el.addView(tierHead("졸업 코스")); el.addView(stageBtn("🎓", "초급 · 중급 · 고급 하나씩 연속 3개 · 합계가 기록", best(lbKey(COURSE)), stageIdx == COURSE) { pick(COURSE) })
        el.addView(tierHead("자유 연습")); el.addView(stageBtn("∞", "유형·난이도·옆 차·칸·돌발 직접 고르기", "", stageIdx == FREE) { pick(FREE) })
    }
    private fun renderSetupPane() {
        val opt = currentOpt(); val T = opt.tier
        v<View>(R.id.freeOpts).visibility = if (stageIdx == FREE) View.VISIBLE else View.GONE
        v<TextView>(R.id.setupTitle).text = when (stageIdx) { FREE -> "자유 연습"; COURSE -> "졸업 코스 · " + course!!.list.joinToString(" → ") { Stages.ALL[it].no.toString() } + "번"; DAILY -> "오늘의 상황 ${daily().label} · " + stripNo(opt.title); else -> stripNo(opt.title) }
        val what = when (opt.kind) { Kind.PERP_REV -> "비상등 켜고 D 로 칸을 지나쳐 자리를 잡고, R 로 들어가는 후면 주차. 뒷차축이 옆 칸을 지나면 멈춰서 핸들 끝까지, 노란선이 주차선 안에 들어오면 풀기."
            Kind.PERP_FWD -> if (opt.neighbors == Neigh.NONE) "D 로 들어가는 전면 주차. 칸 입구가 사이드미러 옆에 오면 끝까지." else "전면 주차는 옆 차가 있으면 한 번에 안 들어갑니다. 바깥쪽으로 붙여 지나치다 35° 꺾고, R 로 핸들 반대로 2 m 뺀 뒤 다시 들어갑니다."
            Kind.PARALLEL -> "평행 주차. D 로 앞차 옆에 나란히 서서 뒷바퀴가 앞차 뒷범퍼 선에 오면 R, 45°에서 핸들 반대로."; Kind.ANGLED -> "사선 45° 칸에 D 로. 칸 입구가 사이드미러 옆에 오면 끝까지, 나란해지면 직진." }
        v<TextView>(R.id.setupNote).text = Html.fromHtml("$what<br><font color='${hex(R.color.fg2)}'>${T.note}${if (opt.hazards > 0) " · 사람·카트가 나오면 브레이크" else ""}</font>", Html.FROM_HTML_MODE_LEGACY)
        v<TextView>(R.id.seedTxt).text = if (stageIdx == DAILY) "오늘 ${daily().key} 같은 상황" else "상황 #$seed"
        for ((id, k) in listOf(R.id.carAvante to "AVANTE", R.id.carGrandeur to "GRANDEUR", R.id.carTucson to "TUCSON")) { v<Button>(id).setBackgroundResource(if (spec.key == k) R.drawable.bg_btn_on else R.drawable.bg_btn); v<Button>(id).setTextColor(col(if (spec.key == k) R.color.inkFg else R.color.fg)) }
        val pv = v<TopView>(R.id.preview); val sc = Scenario.build(spec, opt, seed); val pe = GameEngine(spec, seed = seed); pe.load(sc)
        pv.engine = pe; pv.snap = pe.tick(VehicleState(brake = true, gear = GearState.PARK, sourceName = "sim"), 0.0); pv.guide = false; pv.pdw = false; pv.invalidate()
        renderLb()
    }

    // ---- 로드 / 시작 / 홈 ----
    private fun load(keepSeed: Boolean, withDemo: Boolean = false) {
        if (!keepSeed) newSeed()
        val opt = currentOpt(); var sc = Scenario.build(spec, opt, seed, noStartJitter = withDemo)
        sc = when (stageIdx) { FREE -> sc.copy(title = "자유 연습 · " + StageOpt(opt.kind, opt.tier, opt.neighbors, opt.side, hazards = opt.hazards).title); DAILY -> sc.copy(title = "오늘의 상황 ${daily().label} · " + sc.title); COURSE -> sc.copy(title = "졸업 코스 ${course!!.i + 1}/3 · " + sc.title); else -> sc }
        engine = GameEngine(spec, seed = seed); engine.load(sc)
        cam.engine = engine; top.engine = engine; mirL.engine = engine; mirR.engine = engine
        demo = if (withDemo) DemoDriver(sc, opt) else null; demoDone = false
        v<View>(R.id.demoBar).visibility = if (withDemo) View.VISIBLE else View.GONE; v<TextView>(R.id.demoCap).text = ""
        saved = false; lastScore = null
        v<TextView>(R.id.stageName).text = sc.title
        v<TextView>(R.id.topInfo).text = "${spec.name} · ${envLabel(sc.env)} · #$seed"
        done.visibility = View.GONE; v<View>(R.id.hazBanner).visibility = View.GONE
        coachMode = coachOverride ?: COACH_BY_TIER.getValue(opt.tier.name); syncCoachUi(); hintUntil = 0
        simSignal = Signal.OFF; simBrakeD = 1.0; simHaz = false; simSteer = 0.0; brake.set(1.0, true); wheel.steer = 0.0; syncHaz()
        wasParked = false; lastPhase = Phase.READY; lastHazTotal = 0
        renderSetupPane()
    }
    private fun envLabel(e: String) = when (e) { "underground" -> "지하"; "outdoor" -> "지상"; "night" -> "밤"; "rain" -> "비"; else -> e }
    private fun start() { running = true; setup.visibility = View.GONE; tips.visibility = View.GONE; setDock(R.id.dPlay) }
    private fun goHome() { running = false; demo = null; v<View>(R.id.demoBar).visibility = View.GONE; done.visibility = View.GONE; tips.visibility = View.GONE; mirprac.visibility = View.GONE; setup.visibility = View.VISIBLE; setDock(R.id.dHome); renderList(); renderSetupPane() }
    private fun setDock(id: Int) { for (k in listOf(R.id.dHome, R.id.dPlay, R.id.dLb, R.id.dTips, R.id.dMir)) { val b = v<Button>(k); b.isSelected = k == id; b.setBackgroundResource(if (k == id) R.drawable.bg_dock_cur else android.R.color.transparent) } }
    private fun syncCoachUi() { v<Button>(R.id.coachBtn).text = COACH_LABEL.getValue(coachMode); v<View>(R.id.hintBtn).visibility = if (coachMode == "hint") View.VISIBLE else View.GONE }
    private fun syncHaz() { val on = if (sim) simHaz else vs.hazardLights; val b = v<Button>(R.id.hazBtn); setBg(R.id.hazBtn, if (on) R.drawable.bg_haz_btn_on else R.drawable.bg_haz_btn); b.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(if (on) Color.rgb(17, 18, 22) else col(R.color.fg)); b.setTextColor(if (on) Color.rgb(17, 18, 22) else col(R.color.fg)) }
    private fun toast(msg: String) { val t = v<TextView>(R.id.toast); t.text = msg; t.visibility = View.VISIBLE; toastUntil = System.currentTimeMillis() + 1400 }
    private fun userInput() { if (demo != null) load(true) }   // 핸들·레버를 건드리면 시연이 끝나고 같은 상황을 직접
    private fun setSignal(s: Signal) { if (simSignal != s) sfx.gear(); simSignal = s }

    private fun nextStage() {
        val c = course
        if (stageIdx == COURSE && c != null) { if (c.i < 2) { c.i++; load(false); return }; course = pickCourse(); load(false); return }
        if (stageIdx == DAILY) stageIdx = daily().idx
        if (stageIdx != FREE) stageIdx = (stageIdx + 1) % Stages.ALL.size
        renderList(); load(false)
    }

    // ---- 결과 ----
    private fun ghostPath(): List<Vec> = try {
        val sc = engine.scenario; val e = GameEngine(spec, seed = seed); e.load(sc); val d = DemoDriver(sc, currentOpt()); val pts = ArrayList<Vec>(); var t = 0.0
        while (t < 240) { t += 0.02; val inp = d.step(e, 0.02); val sn = e.tick(inp.copy(gear = GearState.PARK, sourceName = "demo"), 0.02); if (sn.v != 0.0) pts += Vec(e.pose.x, e.pose.y); if (sn.phase == Phase.PARKED || sn.phase == Phase.HIT) break }
        pts } catch (e: Exception) { emptyList() }
    private fun showDone() {
        val eng = engine; val hz = eng.hazStats; val sc0 = eng.score(); var sc = sc0; done.visibility = View.VISIBLE; sfx.parked()
        val next = v<Button>(R.id.next); val c = course
        if (stageIdx == COURSE && c != null) { c.sum += sc0.total; c.pen += sc0.pen; c.clean = c.clean && sc0.clean; val last = c.i >= 2
            if (last) sc = GameEngine.Score(c.sum - c.pen, c.pen, c.sum, sc0.corrections, sc0.noHazLights, c.clean)
            next.text = if (last) "새 졸업 코스 ▶" else "다음 코스 ${c.i + 2}/3 ▶"; v<View>(R.id.nameRow).visibility = if (last) View.VISIBLE else View.GONE }
        else { next.text = "다음 스테이지 ▶"; v<View>(R.id.nameRow).visibility = View.VISIBLE }
        lastScore = sc
        val T = eng.trace; val rev = T.count { it.v < 0 }
        v<TextView>(R.id.doneTitle).text = if (stageIdx == COURSE && c != null && c.i >= 2) (if (sc.clean) "졸업! 클린" else "졸업!") else if (stageIdx == COURSE && c != null) "${c.i + 1}/3 쏙" else if (sc.clean) "쏙! 클린" else if (eng.hits == 0 && hz.hit == 0) "쏙!" else "쏙! 들어갔지만 부딪혔어요"
        v<TextView>(R.id.doneTxt).text = "${eng.scenario.title} · 후진 ${if (T.isEmpty()) 0 else rev * 100 / T.size}% · 충돌 ${eng.hits}회" + (if (hz.total > 0) " · 돌발 ${hz.total}회 중 멈춤 ${hz.avoided}회 · 사고 ${hz.hit}회" else "") + (if (sc.corrections > 0) " · 보정 ${sc.corrections}회" else "")
        v<TextView>(R.id.recBig).text = "%.1f s".format(sc.total) + (if (stageIdx == COURSE && c != null) (if (c.i >= 2) "  (3개 합계)" else "  (누적 %.1f s)".format(c.sum)) else "")
        val parts = ArrayList<String>(); parts += "충돌 ${eng.hits}×5"; if (hz.hit > 0) parts += "사고 ${hz.hit}×15"; if (sc.corrections > 0) parts += "보정 ${sc.corrections}×3"; if (sc.noHazLights) parts += "비상등 안 켬 +2"
        v<TextView>(R.id.recPen).text = if (sc.pen > 0) "주행 %.1f + 패널티 %.0f (%s)".format(sc.drive, sc.pen, parts.joinToString(" · ")) else "주행 %.1f · 패널티 없음".format(sc.drive)
        v<View>(R.id.recClean).visibility = if (sc.clean) View.VISIBLE else View.GONE
        val rank = lbRank(sc.total); v<TextView>(R.id.recRank).text = "이 기기 ${rank}위${if (rank == 1) " · 신기록" else ""}"
        v<EditText>(R.id.nameIn).setText(prefs.getString("name", "")); v<TextView>(R.id.saveMsg).text = ""; v<Button>(R.id.saveRec).isEnabled = true
        v<TextView>(R.id.review).text = eng.review().joinToString("\n") { "• $it" }
        v<ReplayView>(R.id.replay).set(eng.model, eng.scenario, T.toList(), ghostPath())
        val cleanStage = if (stageIdx == COURSE && c != null) (if (sc0.clean) Stages.ALL[c.list[c.i]].no else null) else if (stageIdx != FREE && stageIdx != DAILY && sc.clean) Stages.ALL[stageIdx].no else null
        if (cleanStage != null) { val s = HashSet(prefs.getStringSet("cleared", emptySet()) ?: emptySet()); s += cleanStage.toString(); prefs.edit().putStringSet("cleared", s).apply() }
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
        val body = mpYaw in 12.0..26.0; val wheelOk = mpPitch in 11.0..24.0; val line = mpPitch >= 9 && mpYaw <= 28
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
                      else vs.copy(signal = if (running) vs.signal else Signal.OFF)
            val d = demo
            if (d != null) { if (!sim && running && (abs(vs.steeringDeg) > 20f || vs.signal != Signal.OFF)) { load(true) }   // 실차에서 핸들·레버를 건드리면 시연 끝
                else { val di = d.step(engine, dt); inp = di.copy(signal = if (running) di.signal else Signal.OFF, gear = GearState.PARK)
                    simSteer = di.steeringDeg.toDouble(); simSignal = di.signal; simBrakeD = if (di.brake) 1.0 else 0.0; simHaz = di.hazardLights; brake.set(simBrakeD, di.brake); syncHaz()
                    setT(R.id.demoCap, d.caption)
                    if (d.done && !demoDone) { demoDone = true; sfx.parked(); v<TextView>(R.id.demoCap).postDelayed({ if (demo != null) setT(R.id.demoCap, "이제 직접 해 보세요. 같은 상황으로 시작합니다.") }, 1200) } } }
            val snap = engine.tick(inp, dt)
            if (snap.gearRefused && demo == null && sim) { simSignal = GameEngine.sigOfGear(snap.gear); clusterMsg = "브레이크를 밟고 변속하세요"; clusterMsgUntil = now + 2000; sfx.gear() }
            val steer = inp.steeringDeg.toDouble(); val blink = (now / 400) % 2 == 0L; val hazOn = inp.hazardLights
            cam.snap = snap; cam.steer = steer; cam.guide = guide; cam.invalidate()
            top.snap = snap; top.steer = steer; top.guide = guide; top.blink = hazOn && blink; top.invalidate()
            mirL.snap = snap; mirL.blink = hazOn && blink; mirL.invalidate(); mirR.snap = snap; mirR.blink = hazOn && blink; mirR.invalidate()
            wheel.steer = steer
            if (!sim) { brake.set(if (inp.brake) 1.0 else 0.0, inp.brake); syncHaz() }
            stalk.realP = sim || vs.gear != GearState.OTHER; stalk.tick(snap.gear, dt)
            setT(R.id.camLabel, if (snap.reverse) "후방 카메라" else "전방 카메라")
            setT(R.id.camInfo, "가상 기어 ${snap.gear} · ${if (snap.brakeDepth >= GameEngine.BRAKE_STOP) "브레이크" else if (snap.v == 0.0) "정지" else "크리핑 %.1f m/s".format(abs(snap.v))}${if (snap.brakeDepth > 0 && snap.brakeDepth < GameEngine.BRAKE_STOP) " · 살살" else ""}")
            val banner = v<TextView>(R.id.hazBanner)
            if (snap.flash != null && snap.hazard != null) { banner.text = "${snap.hazard.type.emoji} ${snap.flash}! 브레이크"; banner.visibility = View.VISIBLE }
            if (snap.hazard == null) banner.visibility = View.GONE
            if (snap.hazStats.total != lastHazTotal) { lastHazTotal = snap.hazStats.total; if (snap.hazard != null) { sfx.hazard(); sfx.vibrate(longArrayOf(120, 60, 120, 60, 120)) } }
            if (snap.phase == Phase.HIT && lastPhase != Phase.HIT) { sfx.hit(); sfx.vibrate(longArrayOf(80, 40, 80)) }; lastPhase = snap.phase
            val coachText = engine.scenario.coach[min(snap.coachStep, engine.scenario.coach.size - 1)].text
            val alertOn = snap.hazard != null || snap.phase == Phase.HIT || snap.phase == Phase.PARKED || !engine.started || snap.inSlot || snap.hint != coachText
            val showCoach = demo == null && (alertOn || coachMode == "on" || (coachMode == "hint" && now < hintUntil))
            val sens = Scene.sensors(snap, engine.scenario); val near = sens.filter { if (snap.reverse) it.du <= 0 else it.du >= 0 }.minOfOrNull { it.d } ?: 9.0
            setBgColor(R.id.pdw1, if (near < 0.3) Scene.PDW_COL[3] else if (near < 0.6) Scene.PDW_COL[2] else if (near < 1.2) Scene.PDW_COL[1] else Scene.PDW_COL[0])
            setBgColor(R.id.pdw2, if (near < 0.3) Scene.PDW_COL[3] else if (near < 0.6) Scene.PDW_COL[2] else Scene.PDW_COL[0])
            setBgColor(R.id.pdw3, if (near < 0.3) Scene.PDW_COL[3] else Scene.PDW_COL[0])
            if (running && near < 1.2 && snap.v != 0.0 && now > pdwNext) { if (near < 0.3) sfx.pdwCont() else sfx.pdw(); sfx.vibrate(longArrayOf(if (near < 0.3) 60 else 25)); pdwNext = now + (if (near < 0.3) 130 else if (near < 0.6) 300 else 650) }
            if (running && snap.gear == "R" && snap.v != 0.0 && now > revNext) { sfx.rev(); revNext = now + 650 }
            if (running && sound) { if (demo != null) coach.say(v<TextView>(R.id.demoCap).text.toString()) else if (snap.hazard != null && snap.flash != null) coach.say("${snap.hazard.type.label}! 멈춰요") else if (showCoach) coach.say(snap.hint) }
            val sigOn = inp.signal
                        val realP = sim || vs.gear != GearState.OTHER
            // 클러스터 패널(시안 drawCluster 와 같은 값): 속도·가상 기어·깜빡이·브레이크·시간·핸들 각도
            v<ClusterView>(R.id.panel).set(ClusterData(kmh = abs(snap.v) * 3.6, gear = if (realP && snap.gear == "N" && !engine.started) "P" else snap.gear, realP = realP,
                sigL = (sigOn == Signal.LEFT || hazOn) && blink, sigR = (sigOn == Signal.RIGHT || hazOn) && blink, brakeD = snap.brakeDepth.toDouble(), latch = brake.latch,
                steer = steer, elapsed = snap.elapsed, hits = snap.hits, src = if (sim) "sim" else "pleos", msg = if (now < clusterMsgUntil) clusterMsg else ""))
            v<View>(R.id.coach).alpha = if (showCoach) 1f else 0f
            val steps = if (snap.coachCount == 5) STEPS5 else STEPS4
            setT(R.id.step, if (snap.phase == Phase.PARKED) "쏙 완료" else if (snap.inSlot) "K 끝" else steps.getOrElse(min(snap.coachStep, snap.coachCount - 1)) { "${min(snap.coachStep, snap.coachCount - 1) + 1}/${snap.coachCount}" })
            setT(R.id.hintTxt, snap.hint); val hc = col(if (snap.hazard != null) R.color.red else R.color.fg); if (v<TextView>(R.id.hintTxt).currentTextColor != hc) v<TextView>(R.id.hintTxt).setTextColor(hc)
            val cal = Calendar.getInstance(); setT(R.id.clock, "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)))
            v<View>(R.id.gate).visibility = if (!sim && vs.gear == GearState.OTHER) View.VISIBLE else View.GONE
            if (toastUntil > 0 && now > toastUntil) { v<View>(R.id.toast).visibility = View.GONE; toastUntil = 0 }
            val parkedNow = snap.phase == Phase.PARKED; if (parkedNow && !wasParked && demo == null) showDone(); wasParked = parkedNow
            if (done.visibility == View.VISIBLE) v<ReplayView>(R.id.replay).invalidate()
            if (mirprac.visibility == View.VISIBLE) drawMp()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        fitDensity()
        coach = Coach(this); sfx = Sfx(this)
        sound = prefs.getBoolean("sound", true); sfx.enabled = sound; v<Button>(R.id.dSound).text = if (sound) "소리" else "무음"
        spec = CarSpec.byKey(prefs.getString("car", "AVANTE")); stageIdx = prefs.getInt("stage", 0).coerceIn(0, Stages.ALL.size)
        v<TextView>(R.id.gate).text = "주차 연습은 기어 P 에서만 할 수 있어요.\n차를 세우고 P 에 두면 이어서 합니다."
        fun <T> spin(sp: Spinner, items: List<T>, label: (T) -> String) { sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items.map(label)); sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onItemSelected(p: AdapterView<*>?, vv: View?, pos: Int, id: Long) { if (setup.visibility == View.VISIBLE) renderSetupPane() }; override fun onNothingSelected(p: AdapterView<*>?) {} } }
        spin(v(R.id.fKind), Kind.values().toList()) { it.label }; spin(v(R.id.fTier), Tier.ALL) { it.name }; spin(v(R.id.fNeigh), Neigh.values().toList()) { it.label }
        spin(v(R.id.fSide), Side.values().toList()) { it.label }; spin(v(R.id.fHaz), listOf(0, 1, 2)) { if (it == 0) "돌발 없음" else "돌발 ${it}회" }
        v<Spinner>(R.id.fTier).setSelection(1); v<Spinner>(R.id.fNeigh).setSelection(1)

        source = if (PleosVehicleSource.isAvailable(this)) PleosVehicleSource(this) else SimulatedVehicleSource()
        sim = source is SimulatedVehicleSource
        source.start { vs = it }
        wheel.interactive = sim; brake.interactive = sim
        mirL.side = -1; mirR.side = 1

        engine = GameEngine(spec, seed = seed); newSeed(); load(true); renderList(); renderSetupPane(); setup.visibility = View.VISIBLE; setDock(R.id.dHome)

        // 클러스터 입력(시뮬 모드)
        wheel.onTouchStart = { userInput() }; wheel.onChange = { simSteer = it }
        brake.onTouchStart = { userInput() }; brake.onChange = { simBrakeD = it }
        // 컬럼 레버(시뮬): 끌거나 눌러 D/N/R. 실차는 P 고정 — 깜빡이 레버가 가상 기어
        stalk.locked = !sim; stalk.onGear = { g -> userInput(); setSignal(GameEngine.sigOfGear(g)) }; stalk.onLockedTouch = { toast("실차에서는 깜빡이 레버로") }
        mirL.adjustable = true; mirR.adjustable = true; mirL.onAdjust = { toast("미러 원위치") }; mirR.onAdjust = { toast("미러 원위치") }
        v<Button>(R.id.hazBtn).setOnClickListener { if (!sim) { toast("실차에서는 비상등 버튼으로"); return@setOnClickListener }; userInput(); simHaz = !simHaz; syncHaz() }
        v<Button>(R.id.demoBtn).setOnClickListener { load(true, true); start() }
        v<Button>(R.id.demoStop).setOnClickListener { load(true) }
        v<Button>(R.id.hintBtn).setOnClickListener { hintUntil = System.currentTimeMillis() + 3000 }
        v<Button>(R.id.coachBtn).setOnClickListener { val order = listOf("on", "hint", "off"); coachMode = order[(order.indexOf(coachMode) + 1) % 3]; coachOverride = coachMode; syncCoachUi() }
        v<Button>(R.id.guideBtn).setOnClickListener { guide = !guide; v<Button>(R.id.guideBtn).setBackgroundResource(if (guide) R.drawable.bg_btn_on else R.drawable.bg_btn); v<Button>(R.id.guideBtn).setTextColor(col(if (guide) R.color.inkFg else R.color.fg)) }
        // ⋯ = 자주 안 쓰는 버튼(코치 모드·가이드라인)을 펼친다(시안 #btns.open)
        v<Button>(R.id.moreBtn).setOnClickListener { more = !more; for (id in listOf(R.id.coachBtn, R.id.guideBtn)) v<View>(id).visibility = if (more) View.VISIBLE else View.GONE; v<Button>(R.id.moreBtn).setBackgroundResource(if (more) R.drawable.bg_btn_on else R.drawable.bg_btn); v<Button>(R.id.moreBtn).setTextColor(col(if (more) R.color.inkFg else R.color.fg)) }
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
        v<Button>(R.id.startBtn).setOnClickListener { prefs.edit().putInt("stage", min(stageIdx, Stages.ALL.size)).apply(); load(false); start() }
        v<Button>(R.id.startDemo).setOnClickListener { load(false, true); start() }
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
        v<Button>(R.id.dPlay).setOnClickListener { if (setup.visibility == View.VISIBLE) { load(false); start() } else { tips.visibility = View.GONE; mirprac.visibility = View.GONE; setDock(R.id.dPlay) } }
        v<Button>(R.id.dTips).setOnClickListener { val show = tips.visibility != View.VISIBLE; tips.visibility = if (show) View.VISIBLE else View.GONE; setDock(if (show) R.id.dTips else if (running) R.id.dPlay else R.id.dHome) }
        v<Button>(R.id.tipsClose).setOnClickListener { tips.visibility = View.GONE; setDock(if (running) R.id.dPlay else R.id.dHome) }
        v<TextView>(R.id.tipsTxt).text = Html.fromHtml(TIPS_HTML, Html.FROM_HTML_MODE_LEGACY)
        v<Button>(R.id.dSound).setOnClickListener { sound = !sound; sfx.enabled = sound; prefs.edit().putBoolean("sound", sound).apply(); v<Button>(R.id.dSound).text = if (sound) "소리" else "무음"; v<Button>(R.id.dSound).alpha = if (sound) 1f else 0.45f; if (sound) sfx.gear() }
        // 거울 맞추기
        v<Button>(R.id.dMir).setOnClickListener { if (mirprac.visibility == View.VISIBLE) v<Button>(R.id.mpClose).performClick() else mpOpen() }
        v<Button>(R.id.mpClose).setOnClickListener { mirprac.visibility = View.GONE; setDock(if (running) R.id.dPlay else R.id.dHome) }
        v<Button>(R.id.mpUsual).setOnClickListener { mpPitch = 6.0; mpYaw = 18.0 }; v<Button>(R.id.mpPark).setOnClickListener { mpPitch = 15.0; mpYaw = 18.0 }
        v<Button>(R.id.mpReset).setOnClickListener { mpPitch = 2 + Math.random() * 4; mpYaw = 30 + Math.random() * 10 }
        v<MirrorView>(R.id.mpMirror).setOnTouchListener { vw, e -> when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { mpDrag = floatArrayOf(e.x, e.y, mpPitch.toFloat(), mpYaw.toFloat()); vw.parent.requestDisallowInterceptTouchEvent(true) }
            MotionEvent.ACTION_MOVE -> mpDrag?.let { mpYaw = (it[3] + (e.x - it[0]) * 0.12).toDouble().coerceIn(0.0, 45.0); mpPitch = (it[2] + (e.y - it[1]) * 0.12).toDouble().coerceIn(-5.0, 30.0) }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { mpDrag = null; vw.performClick() } }; true }
        // 오프닝
        val opening = v<View>(R.id.opening); opening.setOnClickListener { opening.visibility = View.GONE }; opening.postDelayed({ opening.visibility = View.GONE }, 4200)
    }

    override fun onResume() { super.onResume(); lastFrameNs = 0L; Choreographer.getInstance().postFrameCallback(frame) }
    override fun onPause() { super.onPause(); Choreographer.getInstance().removeFrameCallback(frame) }
    override fun onDestroy() { super.onDestroy(); source.stop(); coach.release() }

    companion object {
        /** 설계 기준 화면(브라우저 시안): 1280×720 dp */
        const val DESIGN_W = 1280f; const val DESIGN_H = 720f
        @JvmStatic var dpiOverride = 0
        const val TIPS_HTML = """<big><b>초보 팁</b></big> <font color='#9aa3b2'>주차 전에 1분. 거울 세 개를 맞추면 절반은 끝</font><br><br>
<b><font color='#00AAD2'>00 쏙 공식 S · S · O · K</font></b><br>
<b>S 신호 Signal</b> — 브레이크 밟은 채 <b>비상등</b>. 뒤차에 "여기 댈게요". 밟은 채 D.<br>
<b>S 자리 Set</b> — 칸을 <b>지나쳐</b> 자리 잡고 멈춤. 밟은 채 <b>R</b>(카메라가 뒤로 바뀜).<br>
<b>O 돌리기 O-turn</b> — 서서 핸들을 <b>끝까지(O)</b>. 브레이크를 살살 떼며 노란선이 칸에 들어오면 풀기.<br>
<b>K 끝 Keep &amp; stop</b> — 나란하면 핸들 가운데, 빨간선 전에 <b>브레이크</b>, 레버 가운데(N) → 실차는 P.<br><br>
<b><font color='#00AAD2'>01 시트와 핸들부터</font></b><br>시트가 바뀌면 거울도 다 틀어집니다. <b>거울보다 시트가 먼저.</b> 등을 붙이고 팔을 뻗었을 때 손목이 핸들 윗부분에 닿는 거리.<br><br>
<b><font color='#00AAD2'>02 백미러(룸미러)</font></b><br>머리를 움직이지 말고 눈만 돌려 <b>뒷유리 전체가 가운데</b> 오게. 고개를 돌려야 보이면 틀어진 겁니다.<br><br>
<b><font color='#00AAD2'>03 사이드미러 · 평소</font></b><br>차체는 <b>살짝만</b>(안쪽 1/4~1/5). 차체가 많이 보일수록 사각이 커집니다. 땅과 하늘은 반반에서 땅을 조금 더.<br><br>
<b><font color='#00AAD2'>04 사이드미러 · 주차할 때</font></b><br>후진 때는 <b>뒷바퀴 + 주차선</b>이 보여야 합니다. 거울을 아래로 내리거나, 'R 연동 미러 하향' 설정을 켜 두세요. 끝나면 원위치.<br><br>
<b><font color='#00AAD2'>05 후방카메라 선 읽기</font></b><br>• <b>노란선</b> = 지금 핸들 그대로 가면 뒷범퍼가 지나갈 길. 핸들을 돌리면 같이 휩니다.<br>• <b>파란 점선</b> = 핸들을 가운데 두었을 때.<br>• <b>빨간선</b> = 0.5 m. 여기 닿기 전에 서야 합니다.<br><br>
<b><font color='#00AAD2'>06 주차 전 순서</font></b><br>• <b>비상등</b> 먼저. 뒤차에 "여기 댈게요".<br>• D 로 칸을 <b>지나쳐서</b> 자리 잡기. 지나치며 핸들을 살짝 반대로 틀어 꽁무니가 칸을 향하게 하면 한 번에 들어가기 쉽습니다.<br>• 멈추고 R, <b>서서 핸들을 끝까지</b> 돌린 뒤 브레이크를 살살 떼기(크리핑).<br>• 노란선이 주차선 안에 들어오면 풀기. 사람이 보이면 무조건 브레이크."""
    }
}
