package app.parkingcoach

import android.os.Bundle
import android.view.Choreographer
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import app.parkingcoach.game.Coach
import app.parkingcoach.game.DemoDriver
import app.parkingcoach.game.GameEngine
import app.parkingcoach.game.Phase
import app.parkingcoach.render.RearCamView
import app.parkingcoach.render.ReplayView
import app.parkingcoach.render.TopView
import app.parkingcoach.sim.CarSpec
import app.parkingcoach.sim.HazType
import app.parkingcoach.sim.Kind
import app.parkingcoach.sim.Neigh
import app.parkingcoach.sim.Scenario
import app.parkingcoach.sim.Side
import app.parkingcoach.sim.StageOpt
import app.parkingcoach.sim.Stages
import app.parkingcoach.sim.Tier
import app.parkingcoach.vehicle.GearState
import app.parkingcoach.vehicle.PleosVehicleSource
import app.parkingcoach.vehicle.Signal
import app.parkingcoach.vehicle.SimulatedVehicleSource
import app.parkingcoach.vehicle.VehicleInputSource
import app.parkingcoach.vehicle.VehicleState

class MainActivity : AppCompatActivity() {
    private lateinit var engine: GameEngine
    private lateinit var source: VehicleInputSource
    private lateinit var coach: Coach
    private var vs = VehicleState()
    private var spec = CarSpec.AVANTE
    private var stageIdx = 0                       // Stages.ALL 색인, FREE 면 자유 연습
    private var lastFrameNs = 0L
    private var doneShown = false
    private var seed = 1L                          // 상황 번호: 매번 바뀜, [다시]는 같은 상황
    private var coachMode = "on"                   // on(초급) · hint(중급, 누를 때만) · off(고급)
    private var hintUntilNs = 0L
    private var demo: DemoDriver? = null
    private var demoBase = VehicleState()           // 시연 시작 때 실제 입력 — 여기서 달라지면 사용자가 건드린 것

    private lateinit var topView: TopView
    private lateinit var rearCam: RearCamView
    private lateinit var hud: TextView
    private lateinit var hint: TextView
    private lateinit var flash: TextView
    private lateinit var gate: TextView
    private lateinit var setup: View
    private lateinit var done: View
    private lateinit var freeBox: View
    private lateinit var stageSpin: Spinner
    private lateinit var fKind: Spinner; private lateinit var fTier: Spinner; private lateinit var fNeigh: Spinner; private lateinit var fSide: Spinner; private lateinit var fHaz: Spinner

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(nowNs: Long) {
            val dt = if (lastFrameNs == 0L) 0.0 else ((nowNs - lastFrameNs) / 1e9).coerceAtMost(0.05)
            lastFrameNs = nowNs
            val running = setup.visibility != View.VISIBLE
            val d = demo
            if (d != null && running && (Math.abs(vs.steeringDeg - demoBase.steeringDeg) > 20f || vs.signal != demoBase.signal || vs.brake != demoBase.brake)) { stopDemo(); }
            val input = demo?.let { if (running) it.step(engine, dt) else VehicleState(sourceName = "demo") } ?: (if (running) vs else vs.copy(signal = Signal.OFF))
            val snap = engine.tick(input, dt)
            topView.snap = snap; rearCam.snap = snap
            topView.invalidate(); rearCam.invalidate()
            val hz = snap.hazStats
            hud.text = "핸들 %+.0f°  바퀴 %+.1f°  |  %s  |  %s  |  %.0fs  충돌 %d%s\n입력: %s%s".format(
                snap.steeringDeg, Math.toDegrees(engine.model.wheelAngleRad(snap.steeringDeg)),
                when (vs.signal) { Signal.LEFT -> "◀ 전진"; Signal.RIGHT -> "후진 ▶"; else -> "정지" },
                if (vs.brake) "브레이크" else "—", snap.elapsed, snap.hits,
                if (hz.total > 0) "  돌발 ${hz.total} · 멈춤 ${hz.avoided} · 사고 ${hz.hit}" else "",
                vs.sourceName, vs.lastError?.let { "  ⚠ $it" } ?: "")
            val alertOn = snap.hazard != null || snap.phase == Phase.HIT || snap.phase == Phase.PARKED || snap.hint != engine.scenario.coach[minOf(snap.coachStep, engine.scenario.coach.size - 1)].text
            val showCoach = alertOn || coachMode == "on" || (coachMode == "hint" && nowNs < hintUntilNs)
            val dd = demo
            if (dd != null) { hint.text = "시연 · " + dd.caption; hint.visibility = View.VISIBLE; if (running) coach.say(dd.caption) }
            else { hint.text = snap.hint; hint.visibility = if (showCoach) View.VISIBLE else View.INVISIBLE; if (running && showCoach) coach.say(snap.hint) }
            snap.hazard?.let { flash.text = "${it.type.emoji} ${it.type.label}! 멈추세요"; flash.visibility = View.VISIBLE } ?: run { flash.visibility = View.GONE }
            gate.visibility = if (vs.sourceName == "pleos" && vs.gear != GearState.PARK) View.VISIBLE else View.GONE
            if (snap.phase == Phase.PARKED && !doneShown && demo == null) showDone()
            if (snap.phase == Phase.PARKED && demo != null && demo!!.done) findViewById<Button>(R.id.demoBtn).text = "직접 하기"
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        topView = findViewById(R.id.topView); rearCam = findViewById(R.id.rearCam)
        hud = findViewById(R.id.hud); hint = findViewById(R.id.hint); gate = findViewById(R.id.gate); flash = findViewById(R.id.flash)
        setup = findViewById(R.id.setup); done = findViewById(R.id.done); freeBox = findViewById(R.id.freeBox)
        stageSpin = findViewById(R.id.stageSpin); fKind = findViewById(R.id.fKind); fTier = findViewById(R.id.fTier); fNeigh = findViewById(R.id.fNeigh); fSide = findViewById(R.id.fSide); fHaz = findViewById(R.id.fHaz)
        gate.text = "주차 연습은 기어 P 에서만 할 수 있어요.\n차를 세우고 P 에 두면 이어서 합니다."
        coach = Coach(this)

        val prefs = getSharedPreferences("ssok", MODE_PRIVATE)
        spec = if (prefs.getString("car", "AVANTE") == "GRANDEUR") CarSpec.GRANDEUR else CarSpec.AVANTE
        stageIdx = prefs.getInt("stage", 0).coerceIn(0, Stages.ALL.size)
        findViewById<RadioGroup>(R.id.carGroup).check(if (spec == CarSpec.GRANDEUR) R.id.carGrandeur else R.id.carAvante)

        fun <T> spin(sp: Spinner, items: List<T>, label: (T) -> String) { sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items.map(label)) }
        spin(stageSpin, Stages.ALL.map { it.title } + "자유 연습 (유형·난이도·옆 차·칸·돌발 직접 고르기)") { it }
        spin(fKind, Kind.values().toList()) { it.label }; spin(fTier, Tier.ALL) { it.name }; spin(fNeigh, Neigh.values().toList()) { it.label }
        spin(fSide, Side.values().toList()) { it.label }; spin(fHaz, listOf(0, 1, 2)) { if (it == 0) "돌발 없음" else "돌발 ${it}회" }
        fTier.setSelection(1); fNeigh.setSelection(1)
        stageSpin.setSelection(stageIdx)
        stageSpin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { stageIdx = pos; freeBox.visibility = if (pos == FREE) View.VISIBLE else View.GONE; findViewById<TextView>(R.id.tierNote).text = currentOpt().tier.note }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        engine = GameEngine(spec); loadScenario()

        source = if (PleosVehicleSource.isAvailable(this)) PleosVehicleSource(this) else SimulatedVehicleSource()
        if (source is SimulatedVehicleSource) bindSimPanel(source as SimulatedVehicleSource)
        source.start { vs = it }

        findViewById<Button>(R.id.startBtn).setOnClickListener {
            spec = if (findViewById<RadioGroup>(R.id.carGroup).checkedRadioButtonId == R.id.carGrandeur) CarSpec.GRANDEUR else CarSpec.AVANTE
            prefs.edit().putString("car", if (spec == CarSpec.GRANDEUR) "GRANDEUR" else "AVANTE").putInt("stage", stageIdx).apply()
            newSeed(); loadScenario(); setup.visibility = View.GONE
        }
        findViewById<Button>(R.id.retryBtn).setOnClickListener { loadScenario() }                 // 같은 상황
        findViewById<Button>(R.id.nextBtn).setOnClickListener { if (stageIdx != FREE) { stageIdx = (stageIdx + 1) % Stages.ALL.size; stageSpin.setSelection(stageIdx) }; newSeed(); loadScenario() }
        findViewById<Button>(R.id.hintBtn).setOnClickListener { hintUntilNs = System.nanoTime() + 3_000_000_000L }
        findViewById<Button>(R.id.demoBtn).setOnClickListener { if (demo == null) startDemo() else stopDemo() }
        findViewById<Button>(R.id.startDemoBtn).setOnClickListener {
            spec = if (findViewById<RadioGroup>(R.id.carGroup).checkedRadioButtonId == R.id.carGrandeur) CarSpec.GRANDEUR else CarSpec.AVANTE
            newSeed(); startDemo(); setup.visibility = View.GONE
        }
        findViewById<Button>(R.id.coachBtn).setOnClickListener { coachMode = when (coachMode) { "on" -> "hint"; "hint" -> "off"; else -> "on" }; syncCoachUi() }
        findViewById<Button>(R.id.menuBtn).setOnClickListener { done.visibility = View.GONE; setup.visibility = View.VISIBLE }
    }

    private val FREE get() = Stages.ALL.size
    private fun newSeed() { seed = (System.currentTimeMillis() % 1_000_000L) + 1 }
    private fun syncCoachUi() {
        findViewById<Button>(R.id.coachBtn).text = when (coachMode) { "on" -> "코치 켬"; "hint" -> "코치 힌트"; else -> "코치 끔" }
        findViewById<Button>(R.id.hintBtn).visibility = if (coachMode == "hint") View.VISIBLE else View.GONE
    }
    private fun currentOpt(): StageOpt = if (stageIdx == FREE) StageOpt(
        Kind.values()[fKind.selectedItemPosition], Tier.ALL[fTier.selectedItemPosition], Neigh.values()[fNeigh.selectedItemPosition], Side.values()[fSide.selectedItemPosition],
        hazards = fHaz.selectedItemPosition) else Stages.ALL[stageIdx]

    /** 시연: 같은 상황 번호(옆 차·색), 시작만 공식 자리. 차가 공식대로 스스로 주차하고 자막을 낸다. */
    private fun startDemo() {
        val opt = currentOpt(); val sc = Scenario.build(spec, opt, seed, noStartJitter = true)
        engine = GameEngine(spec, seed = seed); engine.load(sc); topView.engine = engine; rearCam.engine = engine
        demo = DemoDriver(sc, opt); demoBase = vs; done.visibility = View.GONE; doneShown = false
        findViewById<Button>(R.id.demoBtn).text = "직접 하기"
    }
    private fun stopDemo() { demo = null; findViewById<Button>(R.id.demoBtn).text = "시연"; loadScenario() }

    private fun loadScenario() {
        val opt = currentOpt(); demo = null; findViewById<Button>(R.id.demoBtn).text = "시연"
        val sc = Scenario.build(spec, opt, seed).let { if (stageIdx == FREE) it.copy(title = "자유 연습 · " + it.title) else it }
        engine = GameEngine(spec, seed = seed); engine.load(sc); topView.engine = engine; rearCam.engine = engine
        coachMode = when (opt.tier.name) { "초급" -> "on"; "중급" -> "hint"; else -> "off" }; hintUntilNs = 0L; syncCoachUi()
        findViewById<TextView>(R.id.tierNote).text = "${opt.tier.note} · 상황 #$seed"
        done.visibility = View.GONE; doneShown = false
    }

    /** 이 기기 리더보드: 스테이지 × 차종, 기록(초) 작은 순 TOP 10. 키 "lb.<스테이지>.<차종>" 값 "total,name,date;..." */
    private fun lbKey() = "lb.${if (stageIdx == FREE) "free" else Stages.ALL[stageIdx].no}.${if (spec == CarSpec.GRANDEUR) "GRANDEUR" else "AVANTE"}"
    private fun lbList(): MutableList<Pair<Double, String>> = getSharedPreferences("ssok", MODE_PRIVATE).getString(lbKey(), "")!!.split(";").filter { it.isNotBlank() }
        .map { val f = it.split(","); f[0].toDouble() to f.drop(1).joinToString(",") }.sortedBy { it.first }.toMutableList()
    private fun lbAdd(total: Double, label: String): Int {
        val l = lbList(); l += total to label; l.sortBy { it.first }; val top = l.take(10)
        getSharedPreferences("ssok", MODE_PRIVATE).edit().putString(lbKey(), top.joinToString(";") { "%.2f,%s".format(it.first, it.second) }).apply()
        return top.indexOfFirst { it.first == total && it.second == label } + 1
    }

    private fun showDone() {
        doneShown = true
        val sc = engine.score(); val rank = lbList().count { it.first < sc.total } + 1
        val date = java.text.SimpleDateFormat("MM.dd", java.util.Locale.KOREA).format(java.util.Date())
        val saved = lbAdd(sc.total, "$date${if (sc.clean) " ✓" else ""}")
        val top = lbList().take(5).mapIndexed { i, r -> "${i + 1}. %.1f s %s".format(r.first, r.second) }.joinToString("   ")
        findViewById<TextView>(R.id.doneTxt).text = "%s\n기록 %.1f s%s · 이 기기 %d위%s%s\n충돌 %d회%s\n%s\nTOP 5: %s".format(engine.scenario.title, sc.total,
            if (sc.pen > 0) " (주행 %.1f + 패널티 %.0f)".format(sc.drive, sc.pen) else "", rank, if (rank == 1) " · 신기록" else "", if (sc.clean) " · CLEAN" else "",
            engine.hits, if (engine.hazStats.total > 0) " · 돌발 ${engine.hazStats.total}회 중 멈춤 ${engine.hazStats.avoided} · 사고 ${engine.hazStats.hit}" else "",
            engine.review().joinToString("\n") { "· $it" }, top)
        findViewById<ReplayView>(R.id.replay).trace = engine.trace.toList()
        done.visibility = View.VISIBLE
    }

    private fun bindSimPanel(sim: SimulatedVehicleSource) {
        val panel = findViewById<LinearLayout>(R.id.simPanel); panel.visibility = View.VISIBLE
        findViewById<SeekBar>(R.id.simSteer).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, u: Boolean) { sim.set { copy(steeringDeg = (p - 540).toFloat()) } }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        findViewById<Button>(R.id.simLeft).setOnClickListener { sim.set { copy(signal = Signal.LEFT) } }
        findViewById<Button>(R.id.simOff).setOnClickListener { sim.set { copy(signal = Signal.OFF) } }
        findViewById<Button>(R.id.simRight).setOnClickListener { sim.set { copy(signal = Signal.RIGHT) } }
        findViewById<ToggleButton>(R.id.simBrake).setOnCheckedChangeListener { _, on -> sim.set { copy(brake = on) } }
        findViewById<ToggleButton>(R.id.simGear).setOnCheckedChangeListener { _, on -> sim.set { copy(gear = if (on) GearState.OTHER else GearState.PARK) } }
        findViewById<ToggleButton>(R.id.simHaz).setOnCheckedChangeListener { _, on -> sim.set { copy(hazardLights = on) } }
        findViewById<Button>(R.id.simReset).setOnClickListener { loadScenario() }
    }

    override fun onResume() { super.onResume(); lastFrameNs = 0L; Choreographer.getInstance().postFrameCallback(frame) }
    override fun onPause() { super.onPause(); Choreographer.getInstance().removeFrameCallback(frame) }
    override fun onDestroy() { super.onDestroy(); source.stop(); coach.release() }
}
