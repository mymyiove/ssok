package app.parkingcoach.run

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import app.parkingcoach.MainActivity
import app.parkingcoach.R
import app.parkingcoach.game.GameEngine
import app.parkingcoach.render.BrakeBarView
import app.parkingcoach.render.ClusterData
import app.parkingcoach.render.ClusterView
import app.parkingcoach.render.MirrorView
import app.parkingcoach.render.PrepView
import app.parkingcoach.render.StalkView
import app.parkingcoach.render.TopView
import app.parkingcoach.sim.HazType
import app.parkingcoach.vehicle.Signal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import java.io.File
import java.io.FileOutputStream
import java.time.Duration

/**
 * Pleos Connect 2.1.0 앱 창 크기에서 돌려 본다. 에뮬레이터 실측(2026-10-26): 앱 창 1682×1350 px · 160 dpi → MainActivity.attachBaseContext 가 ×1.314 로 dp 를 키워
 * 앱이 보는 화면은 1280×1027 dp. 여기서는 그 dp 상자를 그대로 쓴다(1280×720 기준 레이아웃이 세로로 1.43배 늘어난 상태 — 아래 줄이 가장 많이 변한다).
 * 그림은 NATIVE 그래픽으로 실제로 그려 app/build/pleos-shots/ 에 남긴다(에뮬레이터 캡처가 아니라 Robolectric 소프트웨어 캔버스).
 * 확인 항목(PM 2026-10-27): ① 핸들·기어·조작부·계기판이 잘리거나 겹치지 않는지 ② 미러 탭 = 반대쪽 · 끌기 = 각도 · 두 번 = 원위치 ③ 연습하기 비상등 → D 로 배지 1/7 → 3/7 + ✓ 팝업 + '여기서 멈춤'
 * ④ 타기 전 준비 의자·핸들·룸미러 끌기, 사이드미러 탭 = 거울 맞추기 ⑤ 8번 보행자 등장 ⑥ onPause 중에도 프레임 루프(깜빡이 카메라 창).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h1027dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PleosWindowTest {
    private val W = 1280; private val H = 1027
    private val outDir = File("build/pleos-shots").apply { mkdirs() }
    @Before fun pauseChoreographer() { ShadowChoreographer.setPaused(true); ShadowChoreographer.setFrameDelay(Duration.ofMillis(16)) }
    private fun frames(n: Int) { repeat(n) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40)) } }
    private fun layout(a: MainActivity): View { val root = a.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY)); root.layout(0, 0, W, H); return root }
    private fun shot(a: MainActivity, name: String) { val root = layout(a)
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888); root.draw(Canvas(bmp))
        FileOutputStream(File(outDir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) } }
    private fun shotView(v: View, w: Int, h: Int, name: String) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)); v.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); v.draw(Canvas(bmp)); FileOutputStream(File(outDir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) } }
    private fun field(a: Any, name: String): java.lang.reflect.Field = a.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun tap(v: View, x: Float, y: Float, action: Int) { val t = SystemClock.uptimeMillis(); v.dispatchTouchEvent(MotionEvent.obtain(t, t, action, x, y, 0)) }
    private fun click(v: View, x: Float, y: Float) { tap(v, x, y, MotionEvent.ACTION_DOWN); tap(v, x, y, MotionEvent.ACTION_UP) }
    /** 창 좌표 사각형(레이아웃 뒤) */
    private fun rectOf(root: View, v: View): Rect { val r = Rect(0, 0, v.width, v.height); (root as android.view.ViewGroup).offsetDescendantRectToMyCoords(v, r); return r }
    private fun open(mode: Int = R.id.startGuide): Pair<ActivityController<MainActivity>, MainActivity> {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        a.findViewById<View>(R.id.opening).performClick(); a.findViewById<Button>(mode).performClick(); frames(12)
        if (a.findViewById<View>(R.id.tour).visibility == View.VISIBLE) a.findViewById<Button>(R.id.tourX).performClick()   // 첫 진입 화면 안내는 건너뛴다(그림을 가린다)
        frames(2); return c to a }
    private fun engine(a: MainActivity) = field(a, "engine").get(a) as GameEngine
    private fun brakeFull(a: MainActivity) { val b = a.findViewById<BrakeBarView>(R.id.brake); tap(b, b.width - 8f, b.height / 2f, MotionEvent.ACTION_DOWN); tap(b, b.width - 8f, b.height / 2f, MotionEvent.ACTION_UP); assertTrue(b.latch) }
    private fun brakeOff(a: MainActivity) { val b = a.findViewById<BrakeBarView>(R.id.brake); click(b, 4f, b.height / 2f); assertEquals(0.0, b.depth, 0.01) }

    /** ① 아래 줄: 핸들 · 기어 기둥 · 조작부(계기판 / P · 비상등 · 라디오 / 페달) · 미러가 줄 안에 들어오고 서로 겹치지 않는다. 계기판은 조작부 폭 그대로 */
    @Test fun layout_bottomRow_fits_and_noOverlap() {
        val (_, a) = open(); val root = layout(a)
        val bottom = rectOf(root, a.findViewById(R.id.bottom))
        val ids = listOf(R.id.wheelBox, R.id.stalkCol, R.id.ctl, R.id.mirBox)
        val rs = ids.map { rectOf(root, a.findViewById(it)) }
        for ((i, r) in rs.withIndex()) { assertTrue("${ids[i]} 가 아래 줄 밖: $r / $bottom", bottom.contains(r)); assertTrue("${ids[i]} 크기 0", r.width() > 40 && r.height() > 40) }
        for (i in 0 until rs.size - 1) assertTrue("${ids[i]} 와 ${ids[i + 1]} 가 겹침: ${rs[i]} ${rs[i + 1]}", rs[i].right <= rs[i + 1].left)
        val ctl = rs[2]; val panel = rectOf(root, a.findViewById(R.id.panel)); val rowTop = rectOf(root, a.findViewById(R.id.rowTop)); val brake = rectOf(root, a.findViewById(R.id.brake))
        for ((n, r) in listOf("계기판" to panel, "P·비상등·라디오" to rowTop, "페달" to brake)) assertTrue("$n 이 조작부 밖: $r / $ctl", ctl.contains(r))
        assertTrue("계기판 폭이 조작부 폭과 다름", panel.width() == ctl.width() && panel.height() == 64)
        assertTrue("계기판 가로세로비 ${panel.width() / 64f}", panel.width() / 64f > 5.5f)   // 이 비율에서 '실차 신호 · 비상등 화면' 글자가 켜진다(ClusterView)
        // 간격: 핸들 ↔ 기둥 ↔ 조작부 양옆 26dp(여백) 이상
        assertTrue("핸들-기둥 간격 ${rs[1].left - rs[0].right}", rs[1].left - rs[0].right >= 26); assertTrue("기둥-조작부 간격 ${rs[2].left - rs[1].right}", rs[2].left - rs[1].right >= 26)
        println("PLEOS bottom=$bottom wheel=${rs[0]} stalk=${rs[1]} ctl=${rs[2]} mir=${rs[3]} panel=$panel brake=$brake")
        shot(a, "pleos-1-play-guide")
        // 계기판만: 실차 모드에서 비상등을 못 읽을 때의 글자('실차 신호 · 비상등 화면')가 TIME 과 겹치지 않는지 눈으로 — 같은 폭으로 따로 그린다
        val cv = ClusterView(a); cv.set(ClusterData(kmh = 2.0, gear = "D", realP = true, sigR = true, brakeD = 1.0, latch = true, steer = -200.0, elapsed = 12.3, src = "pleos-haz"))
        shotView(cv, panel.width(), panel.height(), "pleos-1b-cluster-pleos-haz")
        cv.set(ClusterData(src = "pleos", brakeD = 0.0, latch = false)); shotView(cv, panel.width(), panel.height(), "pleos-1c-cluster-pleos")
    }

    /** ② 미러 하나: 탭 = 반대쪽(라벨 '왼쪽 미러'/'오른쪽 미러'), 끌기 = 각도, 두 번 누름 = 그 미러 원위치(반대쪽으로 넘어가 버리지 않는다) */
    @Test fun mirror_tap_drag_doubleTap() {
        val (_, a) = open(); layout(a)
        val mir = a.findViewById<MirrorView>(R.id.mir); val label = a.findViewById<TextView>(R.id.mirLabel); val sw = a.findViewById<Button>(R.id.mirSw)
        assertEquals("1번(오른쪽 칸)은 오른쪽 미러가 먼저", 1, mir.side); assertEquals("오른쪽 미러", label.text.toString()); assertEquals("‹ 왼쪽", sw.text.toString())
        val cx = mir.width / 2f; val cy = mir.height / 2f
        click(mir, cx, cy); assertEquals("한 번 누르면 반대쪽", -1, mir.side); assertEquals("왼쪽 미러", label.text.toString()); assertEquals("오른쪽 ›", sw.text.toString())
        frames(10); Thread.sleep(350)   // 두 번 누름 창(300ms) 밖으로
        tap(mir, cx, cy, MotionEvent.ACTION_DOWN); tap(mir, cx - 80, cy + 60, MotionEvent.ACTION_MOVE); tap(mir, cx - 80, cy + 60, MotionEvent.ACTION_UP)
        assertEquals("끌어도 쪽은 그대로", -1, mir.side); assertNotNull("끌면 각도(yaw)", mir.yawDeg); assertNotNull("끌면 각도(pitch)", mir.pitchDeg)
        val yaw = mir.yawDeg!!; val pitch = mir.pitchDeg!!; assertTrue("왼쪽 미러를 왼쪽으로 끌면 바깥(yaw↑): $yaw", yaw > 18.0); assertTrue("아래로 끌면 pitch↑: $pitch", pitch > 8.0)
        frames(2); shot(a, "pleos-2-mirror-left-dragged")
        frames(10); Thread.sleep(350)
        click(mir, cx, cy); click(mir, cx, cy)   // 두 번 누름
        assertEquals("두 번 누르면 그 미러가 원위치(반대쪽으로 안 넘어감)", -1, mir.side); assertNull(mir.yawDeg); assertNull(mir.pitchDeg); assertEquals("왼쪽 미러", label.text.toString())
        frames(10); Thread.sleep(350); click(mir, cx, cy); assertEquals(1, mir.side); assertNull("끌지 않은 오른쪽은 기본 각도", mir.yawDeg)
        sw.performClick(); assertEquals("칩으로도 반대쪽", -1, mir.side)
    }

    /** ③ 연습하기: 비상등(화면 버튼) → 브레이크 밟고 D → 배지 1/7 → 2/7 → 3/7, 계기판 ✓ 팝업, 어라운드뷰 '여기서 멈춤' 점선 */
    @Test fun guideMode_hazard_then_D_advancesBadge() {
        val (_, a) = open(R.id.startGuide); layout(a)
        val step = a.findViewById<TextView>(R.id.step); val panel = a.findViewById<ClusterView>(R.id.panel); val top = a.findViewById<TopView>(R.id.topView)
        assertEquals("연습", a.findViewById<TextView>(R.id.modeChip).text.toString()); assertEquals("1/7", step.text.toString()); assertNull(top.stopMark)
        assertTrue(a.findViewById<TextView>(R.id.hintTxt).text.toString().contains("비상등"))
        a.findViewById<Button>(R.id.hazBtn).performClick(); frames(2)
        assertEquals("비상등 → 2/7", "2/7", step.text.toString()); assertTrue("✓ 팝업: ${panel.data.msg}", panel.data.msg.startsWith("✓ 비상등"))
        assertTrue("브레이크는 시작 때 밟힌 채(고정)", a.findViewById<BrakeBarView>(R.id.brake).latch)
        a.findViewById<StalkView>(R.id.stalk).step(1); frames(2)   // ▲ 한 번 = D
        assertEquals("D 가 들어갔다", "D", engine(a).gear); assertEquals("D → 3/7", "3/7", step.text.toString()); assertTrue("✓ 팝업: ${panel.data.msg}", panel.data.msg == "✓ D")   // 완료 이름은 짧게(비상등 · D · 자리 잡기 …, 2026-10-27)
        assertNotNull("공식 자리 단계엔 '여기서 멈춤' 점선", top.stopMark); assertEquals(engine(a).scenario.formulaX, top.stopMark!!.x, 1e-9)
        shot(a, "pleos-3-guide-3of7")
        // 브레이크를 떼면 크리핑 → 공식 자리를 지나면 4/7 로
        brakeOff(a); var i = 0; while (i < 600 && step.text.toString() == "3/7") { frames(1); i++ }
        assertEquals("공식 자리 도달 → 4/7 (프레임 $i)", "4/7", step.text.toString()); assertNull("점선은 그 단계에서만", top.stopMark)
    }

    /** ④ 팁 › 타기 전 준비: 의자·핸들 끌기, 룸미러 탭에서 거울 끌기, 사이드미러 탭 = 거울 맞추기 */
    @Test fun prep_seat_room_side() {
        val (_, a) = open(); layout(a)
        a.findViewById<Button>(R.id.dTips).performClick(); assertEquals(View.VISIBLE, a.findViewById<View>(R.id.tips).visibility)
        a.findViewById<Button>(R.id.prepSeatBtn).performClick(); frames(2); layout(a)
        val prep = a.findViewById<View>(R.id.prep); val pv = a.findViewById<PrepView>(R.id.prepView); assertEquals(View.VISIBLE, prep.visibility); assertEquals("seat", pv.tab)
        assertTrue("준비 화면이 그려질 크기 ${pv.width}×${pv.height}", pv.width >= 400 && pv.height >= 200)
        val k = pv.width / 640f; val G = pv.geom()
        val sx = G.sx.toFloat(); val s0 = pv.seat; tap(pv, (sx + 10) * k, 300 * k, MotionEvent.ACTION_DOWN); tap(pv, (sx + 70) * k, 300 * k, MotionEvent.ACTION_MOVE); tap(pv, (sx + 70) * k, 300 * k, MotionEvent.ACTION_UP)
        assertEquals("의자를 60px 끌면 +0.5", s0 + 0.5, pv.seat, 0.03)
        val w0 = pv.wy; tap(pv, G.W[0].toFloat() * k, G.W[1].toFloat() * k, MotionEvent.ACTION_DOWN); tap(pv, G.W[0].toFloat() * k, (G.W[1] + 35).toFloat() * k, MotionEvent.ACTION_MOVE); tap(pv, G.W[0].toFloat() * k, (G.W[1] + 35).toFloat() * k, MotionEvent.ACTION_UP)
        assertEquals("핸들을 35px 내리면 wy +0.5", w0 + 0.5, pv.wy, 0.03)
        assertTrue(a.findViewById<TextView>(R.id.prepAngle).text.isNotEmpty()); shot(a, "pleos-4a-prep-seat")
        a.findViewById<Button>(R.id.prepTabRoom).performClick(); frames(1); assertEquals("room", pv.tab)
        val mx0 = pv.mx; tap(pv, 320 * k, 140 * k, MotionEvent.ACTION_DOWN); tap(pv, 270 * k, 140 * k, MotionEvent.ACTION_MOVE); tap(pv, 270 * k, 140 * k, MotionEvent.ACTION_UP)
        assertEquals("룸미러를 왼쪽으로 50px 끌면 -40", mx0 - 40, pv.mx, 0.5); assertTrue(a.findViewById<TextView>(R.id.prepAngle).text.toString().startsWith("거울 좌우")); shot(a, "pleos-4b-prep-room")
        a.findViewById<Button>(R.id.prepTabSide).performClick(); frames(2)
        assertEquals("사이드미러 탭 = 거울 맞추기", View.VISIBLE, a.findViewById<View>(R.id.mirprac).visibility); assertEquals(View.GONE, prep.visibility); shot(a, "pleos-4c-mirror-practice")
        a.findViewById<Button>(R.id.mpClose).performClick(); assertEquals(View.VISIBLE, a.findViewById<View>(R.id.tips).visibility)
    }

    /** ⑤ 8번(중급 · 후면 · 보행자): D 로 가면 사람이 옆에서 걸어 나오고 경보 배너 · 어라운드뷰 · 카메라에 보인다. 멈추면 '멈춤'으로 센다 */
    @Test fun stage8_walker_appears() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        a.findViewById<View>(R.id.opening).performClick(); field(a, "stageIdx").set(a, 7); a.findViewById<Button>(R.id.startBtn).performClick(); frames(12)
        if (a.findViewById<View>(R.id.tour).visibility == View.VISIBLE) a.findViewById<Button>(R.id.tourX).performClick(); frames(2); layout(a)
        val e = engine(a); assertEquals(8, e.scenario.title.substringBefore('.').toInt()); assertEquals(HazType.WALKER, e.scenario.hazType)
        a.findViewById<Button>(R.id.hazBtn).performClick(); a.findViewById<StalkView>(R.id.stalk).step(1); frames(2); assertEquals("D", e.gear)
        brakeOff(a); var i = 0; while (i < 400 && e.hazard == null) { frames(1); i++ }
        val hz = e.hazard; assertNotNull("돌발이 나와야 한다(프레임 $i, 이동 ${"%.1f".format(e.travelled)} m)", hz); assertEquals(HazType.WALKER, hz!!.type)
        assertEquals(View.VISIBLE, a.findViewById<View>(R.id.hazBanner).visibility); assertTrue(a.findViewById<TextView>(R.id.hazBanner).text.toString().contains("보행자"))
        val p0 = hz.pos; brakeFull(a); frames(12); assertTrue("사람이 걸어 나온다", Math.hypot(hz.pos.x - p0.x, hz.pos.y - p0.y) > 0.3)
        shot(a, "pleos-5-stage8-walker")
        var j = 0; while (j < 600 && e.hazard != null) { frames(1); j++ }
        assertEquals("멈춰 섰으니 사고 0", 0, e.hazStats.hit); assertEquals(1, e.hazStats.avoided); assertEquals(View.GONE, a.findViewById<View>(R.id.hazBanner).visibility)
    }

    /** ⑥ 깜빡이를 켜면 Pleos 가 사각지대 카메라 창을 띄워 onPause 된다 — 프레임 루프는 onStart~onStop 이라 그동안에도 돈다. onStop 이면 멈추고 다시 start 하면 이어진다 */
    @Test fun frameLoop_runsWhilePaused_stopsOnStop() {
        val (c, a) = open(R.id.startBtn); layout(a)
        a.findViewById<StalkView>(R.id.stalk).step(1); frames(2); brakeOff(a); frames(5)
        val e = engine(a); val t0 = e.elapsed; assertTrue("움직이는 중", t0 > 0)
        c.pause(); frames(10); val t1 = e.elapsed; assertTrue("onPause 중에도 게임이 간다: $t0 → $t1", t1 > t0 + 0.2)
        c.stop(); frames(10); val t2 = e.elapsed; assertEquals("onStop 이면 멈춘다", t1, t2, 1e-9)
        c.restart().resume(); frames(10); val t3 = e.elapsed; assertTrue("다시 보이면 이어진다: $t2 → $t3", t3 > t2 + 0.2)
        assertFalse("세션은 그대로(홈으로 안 튕김)", a.findViewById<View>(R.id.setup).visibility == View.VISIBLE)
    }
}
