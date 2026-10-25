package app.parkingcoach

import android.os.Bundle
import android.view.Choreographer
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import app.parkingcoach.game.Coach
import app.parkingcoach.game.GameEngine
import app.parkingcoach.game.Phase
import app.parkingcoach.render.RearCamView
import app.parkingcoach.render.ReplayView
import app.parkingcoach.render.TopView
import app.parkingcoach.sim.CarSpec
import app.parkingcoach.sim.Scenario
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
    private var scenarioIdx = 0
    private var lastFrameNs = 0L
    private var doneShown = false

    private lateinit var topView: TopView
    private lateinit var rearCam: RearCamView
    private lateinit var hud: TextView
    private lateinit var hint: TextView
    private lateinit var gate: TextView
    private lateinit var setup: View
    private lateinit var done: View

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(nowNs: Long) {
            val dt = if (lastFrameNs == 0L) 0.0 else ((nowNs - lastFrameNs) / 1e9).coerceAtMost(0.05)
            lastFrameNs = nowNs
            val running = setup.visibility != View.VISIBLE
            val snap = engine.tick(if (running) vs else vs.copy(signal = Signal.OFF), dt)
            topView.snap = snap; rearCam.snap = snap
            topView.invalidate(); rearCam.invalidate()
            hud.text = "핸들 %+.0f°  바퀴 %+.1f°  |  %s  |  %s  |  %.0fs  충돌 %d\n입력: %s%s".format(
                snap.steeringDeg, Math.toDegrees(engine.model.wheelAngleRad(snap.steeringDeg)),
                when (vs.signal) { Signal.LEFT -> "◀ 전진"; Signal.RIGHT -> "후진 ▶"; else -> "정지" },
                if (vs.brake) "브레이크" else "—", snap.elapsed, snap.hits,
                vs.sourceName, vs.lastError?.let { "  ⚠ $it" } ?: "")
            hint.text = snap.hint
            if (running) coach.say(snap.hint)
            gate.visibility = if (vs.sourceName == "pleos" && vs.gear != GearState.PARK) View.VISIBLE else View.GONE
            if (snap.phase == Phase.PARKED && !doneShown) showDone()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        topView = findViewById(R.id.topView); rearCam = findViewById(R.id.rearCam)
        hud = findViewById(R.id.hud); hint = findViewById(R.id.hint); gate = findViewById(R.id.gate)
        setup = findViewById(R.id.setup); done = findViewById(R.id.done)
        gate.text = "주차 연습은 기어 P 에서만 할 수 있어요.\n차를 세우고 P 에 두면 이어서 합니다."
        coach = Coach(this)

        val prefs = getSharedPreferences("ssok", MODE_PRIVATE)
        spec = if (prefs.getString("car", "AVANTE") == "GRANDEUR") CarSpec.GRANDEUR else CarSpec.AVANTE
        scenarioIdx = prefs.getInt("course", 0)
        findViewById<RadioGroup>(R.id.carGroup).check(if (spec == CarSpec.GRANDEUR) R.id.carGrandeur else R.id.carAvante)
        findViewById<RadioGroup>(R.id.courseGroup).check(listOf(R.id.coursePerp, R.id.coursePillar, R.id.courseParallel)[scenarioIdx])
        engine = GameEngine(spec); loadScenario()

        source = if (PleosVehicleSource.isAvailable(this)) PleosVehicleSource(this) else SimulatedVehicleSource()
        if (source is SimulatedVehicleSource) bindSimPanel(source as SimulatedVehicleSource)
        source.start { vs = it }

        findViewById<Button>(R.id.startBtn).setOnClickListener {
            spec = if (findViewById<RadioGroup>(R.id.carGroup).checkedRadioButtonId == R.id.carGrandeur) CarSpec.GRANDEUR else CarSpec.AVANTE
            scenarioIdx = when (findViewById<RadioGroup>(R.id.courseGroup).checkedRadioButtonId) { R.id.coursePillar -> 1; R.id.courseParallel -> 2; else -> 0 }
            prefs.edit().putString("car", if (spec == CarSpec.GRANDEUR) "GRANDEUR" else "AVANTE").putInt("course", scenarioIdx).apply()
            engine = GameEngine(spec); loadScenario(); setup.visibility = View.GONE
        }
        findViewById<Button>(R.id.retryBtn).setOnClickListener { loadScenario() }
        findViewById<Button>(R.id.nextBtn).setOnClickListener { scenarioIdx = (scenarioIdx + 1) % Scenario.all(spec).size; loadScenario() }
    }

    private fun loadScenario() {
        engine.load(Scenario.all(spec)[scenarioIdx]); topView.engine = engine; rearCam.engine = engine
        done.visibility = View.GONE; doneShown = false
    }

    private fun showDone() {
        doneShown = true
        findViewById<TextView>(R.id.doneTxt).text = "쏙! %s · %.0f초 · 충돌 %d회".format(engine.scenario.title, engine.elapsed, engine.hits)
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
        findViewById<Button>(R.id.simReset).setOnClickListener { loadScenario() }
    }

    override fun onResume() { super.onResume(); lastFrameNs = 0L; Choreographer.getInstance().postFrameCallback(frame) }
    override fun onPause() { super.onPause(); Choreographer.getInstance().removeFrameCallback(frame) }
    override fun onDestroy() { super.onDestroy(); source.stop(); coach.release() }
}
