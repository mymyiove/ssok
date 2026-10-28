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
        root.v<TextView>(R.id.setupTitle).text = "[초급] 후면 직각주차 · 양옆 차"; root.v<TextView>(R.id.setupNote).text = "R 로 넣는 후면 주차"
        root.v<TextView>(R.id.lbBody).text = "1. HJ        23.1 s  ✓  10.25"
        val list = root.v<android.widget.LinearLayout>(R.id.stageList)
        // MainActivity.stageBtn 과 같은 줄: 번호 · 위에서 본 그림 · 짧은 이름 · 돌발 사람
        val ctx = root.context; val d = root.resources.displayMetrics.density
        list.addView(TextView(ctx).apply { text = android.text.Html.fromHtml("쏙 <font color='#00AAD2'>SSOK</font>", 0); textSize = 20f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(android.graphics.Color.parseColor("#111216")); gravity = android.view.Gravity.CENTER_VERTICAL; layoutParams = android.widget.LinearLayout.LayoutParams(-1, (30 * d).toInt()) })
        for (st in Stages.ALL.take(8)) { val sel = st.no == 2; val ink = if (sel) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#5F6775")
            val row = android.widget.LinearLayout(ctx).apply { gravity = android.view.Gravity.CENTER_VERTICAL; setBackgroundResource(if (sel) R.drawable.bg_stage_sel else R.drawable.bg_stage); setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt()); layoutParams = android.widget.LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (5 * d).toInt() } }
            row.addView(TextView(ctx).apply { text = "%02d".format(st.no); textSize = 13f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(if (sel) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#8B93A1")); minWidth = (40 * d).toInt() })
            row.addView(app.parkingcoach.render.StageIconView(ctx, st, ink).apply { layoutParams = android.widget.LinearLayout.LayoutParams(-2, -2).apply { rightMargin = (10 * d).toInt() } })
            row.addView(TextView(ctx).apply { text = when (st.kind) { app.parkingcoach.sim.Kind.PERP_REV -> "후면 직각"; app.parkingcoach.sim.Kind.PERP_FWD -> "전면 직각"; app.parkingcoach.sim.Kind.PARALLEL -> "평행"; app.parkingcoach.sim.Kind.ANGLED -> "사선" }; textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(if (sel) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#111216")); layoutParams = android.widget.LinearLayout.LayoutParams(0, -2, 1f) })
            repeat(st.hazards) { row.addView(android.widget.ImageView(ctx).apply { setImageResource(R.drawable.ic_person); layoutParams = android.widget.LinearLayout.LayoutParams((11 * d).toInt(), (16 * d).toInt()) }) }
            list.addView(row) }
        paparazzi.snapshot(root, "home") }
    /** 첫 진입 화면 안내 2단계(사이드 미러): 구멍 + 말풍선. 자리 계산은 MainActivity.tourStep 과 같은 규칙(아래 → 위 → 옆) */
    @Test fun tour_mirror() { val root = paparazzi.inflate<View>(R.layout.activity_main); play(root, 1, 3, -520.0, true)
        val tour = root.v<View>(R.id.tour); tour.visibility = View.VISIBLE
        root.v<TextView>(R.id.tourT).text = "사이드 미러"; root.v<TextView>(R.id.tourD).text = "누르면 반대쪽 · 끌면 각도"; root.v<TextView>(R.id.tourDots).text = "○○●○○○○"
        val dp = root.resources.displayMetrics.density
        root.measure(View.MeasureSpec.makeMeasureSpec((1280 * dp).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((720 * dp).toInt(), View.MeasureSpec.EXACTLY)); root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val target = root.v<View>(R.id.mirBox); root.v<app.parkingcoach.render.SpotlightView>(R.id.tourSpot).show(target)
        val bubble = root.v<View>(R.id.tourBubble); bubble.measure(View.MeasureSpec.makeMeasureSpec((300 * dp).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val gap = 12 * dp; val r = app.parkingcoach.render.SpotlightView.rectIn(tour, target)
        val x = r.left; val y = r.top; val w = r.width(); val h = r.height(); val bw = bubble.measuredWidth.toFloat(); val bh = bubble.measuredHeight.toFloat(); val W = tour.width.toFloat(); val H = tour.height.toFloat()
        var bx: Float; var by: Float
        if (y + h + gap + bh < H) { by = y + h + gap; bx = x + w / 2 - bw / 2 } else if (y - gap - bh > 0) { by = y - gap - bh; bx = x + w / 2 - bw / 2 } else { by = maxOf(gap, minOf(H - bh - gap, y)); bx = if (x + w + gap < W - bw) x + w + gap else x - bw - gap }
        bx = maxOf(gap, minOf(W - bw - gap, bx))
        (bubble.layoutParams as android.widget.FrameLayout.LayoutParams).apply { leftMargin = bx.toInt(); topMargin = by.toInt() }; bubble.layoutParams = bubble.layoutParams
        root.measure(View.MeasureSpec.makeMeasureSpec((1280 * dp).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((720 * dp).toInt(), View.MeasureSpec.EXACTLY)); root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        paparazzi.snapshot(root, "tour-mirror") }
    /** 연습하기 모드(PM 2026-10-27): 단계 배지 3/7 + 어라운드뷰 '여기서 멈춤' 점선 + 상단 '연습' 칩 */
    @Test fun play_guide_stage1() { val root = paparazzi.inflate<View>(R.layout.activity_main); play(root, 0, 2, 0.0, false)
        val e = root.v<TopView>(R.id.topView).engine!!; root.v<TopView>(R.id.topView).stopMark = app.parkingcoach.sim.Vec(e.scenario.formulaX, e.scenario.start.y)
        root.v<TextView>(R.id.step).text = "3/7"; root.v<TextView>(R.id.hintTxt).text = "칸을 지나쳐 뒷차축이 옆 칸을 지날 때까지 천천히."; root.v<TextView>(R.id.modeChip).text = "연습"
        root.v<TextView>(R.id.modeChip).backgroundTintList = android.content.res.ColorStateList.valueOf(root.context.getColor(R.color.accent)); root.v<TextView>(R.id.modeChip).setTextColor(android.graphics.Color.WHITE)
        paparazzi.snapshot(root, "play-01-guide") }
    /** 타기 전 준비(PM 2026-10-27): 옆모습 운전석 — 의자·핸들 끌기, 판정 고리 */
    @Test fun prep_seat() { val root = paparazzi.inflate<View>(R.layout.activity_main); play(root, 0, 1, 0.0, true)
        root.v<View>(R.id.prep).visibility = View.VISIBLE; val pv = root.v<app.parkingcoach.render.PrepView>(R.id.prepView); val G = pv.geom()
        root.v<TextView>(R.id.prepAngle).text = "무릎 굽힘 ${Math.round(G.bend)}° · 팔 거리 좋아요"; root.v<TextView>(R.id.prepPass).text = if (G.okKnee && G.okReach && G.okView) "✓ 의자·핸들 됐어요" else ""
        paparazzi.snapshot(root, "prep-seat") }
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
    root.v<MirrorView>(R.id.mir).apply { engine = e; snap = s; side = 1 }
    root.v<WheelView>(R.id.wheel).steer = steer
    root.v<BrakeBarView>(R.id.brake).set(0.3, false)
    root.v<TextView>(R.id.stageName).text = sc.title
    root.v<TextView>(R.id.hintTxt).text = s.hint; root.v<TextView>(R.id.step).text = "O Orbit"
    root.v<app.parkingcoach.render.ClusterView>(R.id.panel).set(app.parkingcoach.render.ClusterData(kmh = 2.0, gear = s.gear, realP = true, sigL = false, sigR = reverse, brakeD = 0.3, latch = false, steer = steer, elapsed = s.elapsed, hits = s.hits, src = "sim"))
    root.v<TextView>(R.id.clock).text = "14:32"
    root.v<View>(R.id.setup).visibility = View.GONE; root.v<View>(R.id.opening).visibility = View.GONE
    root.v<ReplayView>(R.id.replay).set(e.model, sc, e.trace.toList(), emptyList())
}
