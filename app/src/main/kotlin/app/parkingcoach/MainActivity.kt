package app.parkingcoach

import android.os.Bundle
import android.view.Choreographer
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import app.parkingcoach.game.GameEngine
import app.parkingcoach.game.Phase
import app.parkingcoach.render.RearCamView
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
    private var vs = VehicleState()
    private var scenarioIdx = 0
    private var lastFrameNs = 0L

    private lateinit var topView: TopView
    private lateinit var rearCam: RearCamView
    private lateinit var hud: TextView
    private lateinit var hint: TextView
    private lateinit var gate: TextView

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(nowNs: Long) {
            val dt = if (lastFrameNs == 0L) 0.0 else ((nowNs - lastFrameNs) / 1e9).coerceAtMost(0.05)
            lastFrameNs = nowNs
            val snap = engine.tick(vs, dt)
            topView.snap = snap; rearCam.snap = snap
            topView.invalidate(); rearCam.invalidate()
            hud.text = "핸들 %+.0f°  바퀴 %+.1f°  |  %s  |  %s  |  %.0fs  충돌 %d\n입력: %s%s".format(
                snap.steeringDeg, Math.toDegrees(engine.model.wheelAngleRad(snap.steeringDeg)),
                when (vs.signal) { Signal.LEFT -> "◀ 전진"; Signal.RIGHT -> "후진 ▶"; else -> "정지" },
                if (vs.brake) "브레이크" else "—", snap.elapsed, snap.hits,
                vs.sourceName, vs.lastError?.let { "  ⚠ $it" } ?: "")
            hint.text = snap.hint
            gate.visibility = if (vs.sourceName == "pleos" && vs.gear != GearState.PARK) View.VISIBLE else View.GONE
            if (snap.phase == Phase.PARKED && engine.trace.isNotEmpty()) hint.text = snap.hint + "\n(핸들 기록 ${engine.trace.size}점 저장됨 · 다시: 깜빡이 OFF 후 브레이크 두 번)"
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        topView = findViewById(R.id.topView); rearCam = findViewById(R.id.rearCam)
        hud = findViewById(R.id.hud); hint = findViewById(R.id.hint); gate = findViewById(R.id.gate)
        gate.text = "주차 연습은 기어 P 에서만 할 수 있어요.\n차를 세우고 P 에 두면 이어서 합니다."

        val spec = CarSpec.AVANTE   // TODO: 설정 화면에서 차종 선택(CarInfo 가 치수를 주지 않음)
        engine = GameEngine(spec)
        engine.load(Scenario.all(spec)[scenarioIdx])
        topView.engine = engine; rearCam.engine = engine

        source = if (PleosVehicleSource.isAvailable(this)) PleosVehicleSource(this) else SimulatedVehicleSource()
        if (source is SimulatedVehicleSource) bindSimPanel(source as SimulatedVehicleSource)
        source.start { vs = it }

        // 다음 시나리오: 주차 완료 상태에서 화면 탭
        findViewById<View>(R.id.panes).setOnClickListener {
            if (engine.phase == Phase.PARKED) { scenarioIdx = (scenarioIdx + 1) % Scenario.all(spec).size; engine.load(Scenario.all(spec)[scenarioIdx]) }
        }
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
        findViewById<Button>(R.id.simReset).setOnClickListener { engine.reset() }
    }

    override fun onResume() { super.onResume(); lastFrameNs = 0L; Choreographer.getInstance().postFrameCallback(frame) }
    override fun onPause() { super.onPause(); Choreographer.getInstance().removeFrameCallback(frame) }
    override fun onDestroy() { super.onDestroy(); source.stop() }
}
