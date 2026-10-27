package app.parkingcoach.shot

import android.view.View
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.parkingcoach.R
import app.parkingcoach.game.GameEngine
import app.parkingcoach.render.BrakeBarView
import app.parkingcoach.render.CameraView
import app.parkingcoach.render.MirrorView
import app.parkingcoach.render.ReplayView
import app.parkingcoach.render.TopView
import app.parkingcoach.render.WheelView
import app.parkingcoach.sim.CarSpec
import app.parkingcoach.sim.Scenario
import app.parkingcoach.sim.Stages
import app.parkingcoach.vehicle.GearState
import app.parkingcoach.vehicle.Signal
import app.parkingcoach.vehicle.VehicleState
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

/** 에뮬레이터 없이 화면을 그려 본다(layoutlib). 결과 PNG = app/src/test/snapshots/images/ — 시안(1280×720)과 나란히 비교용. */
class ScreenShotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig(screenWidth = 1280, screenHeight = 720, xdpi = 160, ydpi = 160, density = Density.MEDIUM, orientation = ScreenOrientation.LANDSCAPE, softButtons = false),
        theme = "Theme.ParkingCoach", showSystemUi = false)


    /** 11번(고급·왼쪽 칸·돌발) 상황 6: 밤. 공식 자리에서 R 넣고 핸들 끝까지 돌린 순간 */
    private fun play(root: View, stage: Int, seed: Long, steer: Double, reverse: Boolean) = playShot(root, stage, seed, steer, reverse)
    @Test fun play_night_stage11() { val root = paparazzi.inflate<View>(R.layout.activity_main); play(root, 10, 6, 540.0, true); paparazzi.snapshot(root, "play-11-night") }
    @Test fun play_underground_stage2() { val root = paparazzi.inflate<View>(R.layout.activity_main); play(root, 1, 3, -520.0, true); paparazzi.snapshot(root, "play-02") }
    @Test fun play_forward_stage3() { val root = paparazzi.inflate<View>(R.layout.activity_main); play(root, 2, 5, 540.0, false); paparazzi.snapshot(root, "play-03-forward") }
    @Test fun home() { val root = paparazzi.inflate<View>(R.layout.activity_main); play(root, 0, 1, 0.0, true)
        root.v<View>(R.id.setup).visibility = View.VISIBLE
        val spec = CarSpec.AVANTE; val sc = Scenario.build(spec, Stages.ALL[1], 3); val e = GameEngine(spec, seed = 3); e.load(sc)
        root.v<TopView>(R.id.preview).apply { engine = e; snap = e.tick(VehicleState(brake = true, gear = GearState.PARK, sourceName = "sim"), 0.0); guide = false; pdw = false }
        root.v<TextView>(R.id.setupTitle).text = "[초급] 후면 직각주차 · 양옆 차"; root.v<TextView>(R.id.setupNote).text = "비상등 켜고 D 로 칸을 지나쳐 자리를 잡고, R 로 들어가는 후면 주차."
        root.v<TextView>(R.id.lbBody).text = "1. HJ        23.1 s  ✓  10.25"; root.v<TextView>(R.id.seedTxt).text = "상황 #3"
        val list = root.v<android.widget.LinearLayout>(R.id.stageList)
        for (st in Stages.ALL.take(6)) { val tv = TextView(root.context); tv.text = "${"%02d".format(st.no)}  ${st.title}"; tv.textSize = 13f; tv.setPadding(10, 14, 10, 14); tv.setBackgroundResource(if (st.no == 2) R.drawable.bg_stage_sel else R.drawable.bg_stage); list.addView(tv) }
        paparazzi.snapshot(root, "home") }
    @Test fun done() { val root = paparazzi.inflate<View>(R.layout.activity_main); play(root, 1, 3, 0.0, true)
        root.v<View>(R.id.done).visibility = View.VISIBLE
        root.v<TextView>(R.id.doneTitle).text = "쏙! 클린"; root.v<TextView>(R.id.doneTxt).text = "2. [초급] 후면 직각주차 · 양옆 차 · 후진 62% · 충돌 0회"
        root.v<TextView>(R.id.recBig).text = "23.1 s"; root.v<TextView>(R.id.recPen).text = "주행 23.1 · 패널티 없음"; root.v<View>(R.id.recClean).visibility = View.VISIBLE; root.v<TextView>(R.id.recRank).text = "이 기기 1위 · 신기록"
        root.v<TextView>(R.id.review).text = "• 핸들을 끝까지 돌린 자리가 공식 자리와 거의 같았어요.\n• 주차 전에 비상등을 켰어요. 뒤차가 알아요.\n• 자리 잡고 한 번에 들어갔어요."
        paparazzi.snapshot(root, "done") }
}

internal fun <T : View> View.v(id: Int): T = findViewById(id)
internal fun playShot(root: View, stage: Int, seed: Long, steer: Double, reverse: Boolean) {
    val spec = CarSpec.AVANTE; val opt = Stages.ALL[stage]; val sc = Scenario.build(spec, opt, seed, noStartJitter = true)
    val e = GameEngine(spec, seed = seed); e.load(sc)
    // 브레이크 밟은 채 시작 → D 로 공식 자리까지 → R
    var s = e.tick(VehicleState(brake = true, gear = GearState.PARK, hazardLights = true, sourceName = "sim"), 0.02)
    var t = 0.0
    while (e.pose.x < sc.formulaX && t < 60) { s = e.tick(VehicleState(signal = Signal.RIGHT, brake = false, hazardLights = true, gear = GearState.PARK, sourceName = "sim"), 0.02); t += 0.02 }
    s = e.tick(VehicleState(signal = if (reverse) Signal.LEFT else Signal.RIGHT, brake = true, hazardLights = true, gear = GearState.PARK, sourceName = "sim"), 0.02)
    for (i in 0 until 40) s = e.tick(VehicleState(steeringDeg = steer.toFloat(), signal = if (reverse) Signal.LEFT else Signal.RIGHT, brake = false, hazardLights = true, gear = GearState.PARK, sourceName = "sim"), 0.02)
    root.v<CameraView>(R.id.cam).apply { engine = e; snap = s; this.steer = steer }
    root.v<TopView>(R.id.topView).apply { engine = e; snap = s; this.steer = steer }
    root.v<MirrorView>(R.id.mirL).apply { engine = e; snap = s; side = -1; }
    root.v<MirrorView>(R.id.mirR).apply { engine = e; snap = s; side = 1; }
    root.v<WheelView>(R.id.wheel).steer = steer
    root.v<BrakeBarView>(R.id.brake).set(0.3, false)
    root.v<TextView>(R.id.stageName).text = sc.title; root.v<TextView>(R.id.topInfo).text = "${spec.name} · ${sc.env} · #$seed"
    root.v<TextView>(R.id.hintTxt).text = s.hint; root.v<TextView>(R.id.step).text = "O Orbit"
    root.v<app.parkingcoach.render.ClusterView>(R.id.panel).set(app.parkingcoach.render.ClusterData(kmh = 2.0, gear = s.gear, realP = true, sigL = false, sigR = reverse, brakeD = 0.3, latch = false, steer = steer, elapsed = s.elapsed, hits = s.hits, src = "sim"))
    root.v<TextView>(R.id.clock).text = "14:32"
    root.v<TextView>(R.id.camInfo).text = "가상 기어 ${s.gear} · 크리핑 0.6 m/s"
    root.v<View>(R.id.setup).visibility = View.GONE; root.v<View>(R.id.opening).visibility = View.GONE
    root.v<ReplayView>(R.id.replay).set(e.model, sc, e.trace.toList(), emptyList())
}
