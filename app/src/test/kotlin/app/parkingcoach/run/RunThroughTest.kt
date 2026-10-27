package app.parkingcoach.run

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import app.parkingcoach.MainActivity
import app.parkingcoach.R
import app.parkingcoach.game.DemoDriver
import app.parkingcoach.game.GameEngine
import app.parkingcoach.render.BrakeBarView
import app.parkingcoach.render.WheelView
import app.parkingcoach.sim.StageOpt
import app.parkingcoach.vehicle.Signal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.robolectric.shadows.ShadowChoreographer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.time.Duration

/**
 * 에뮬레이터 없이 앱을 '실행'해 본다(Robolectric, SDK 34, 1280×720 가로): 액티비티 생성 → 오프닝 → 홈에서 [시작] → 프레임 루프를 돌리며
 * 시연 운전자의 입력을 화면 부품(핸들·기어·브레이크·비상등)에 넣어 끝까지 주차 → 결과 카드 → 기록 저장 → 다음 스테이지. 중간 화면을 PNG 로 남긴다(app/build/run-shots/).
 * 기본은 LEGACY 그래픽(빠름, 그림 없음). `gradle :app:testDebugUnitTest -Pshots=1` 이면 NATIVE 로 그려 PNG 를 남긴다.
 * 터치도 본다: 핸들 드래그가 각도를 바꾸는지, 브레이크 슬라이더가 강도를 잡는지, 브레이크 없이 D 를 넣으면 거부되는지.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h720dp-land-mdpi")
class RunThroughTest {
    private val outDir = File("build/run-shots").apply { mkdirs() }
    /** Choreographer 를 '멈춤' 모드로: 프레임 콜백이 시계가 16ms 갈 때만 돈다(기본 모드는 되감기 콜백이 idle 안에서 무한히 돈다 → 엔진 dt 0) */
    @Before fun pauseChoreographer() { ShadowChoreographer.setPaused(true); ShadowChoreographer.setFrameDelay(Duration.ofMillis(16)) }
    private fun frames(n: Int) { repeat(n) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40)) } }   // 한 프레임 40ms(엔진 dt 상한 50ms)
    private fun shot(a: MainActivity, name: String) {
        if (System.getProperty("ssok.shots") == null) return
        val root = a.window.decorView; val w = 1280; val h = 720
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)); root.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); root.draw(Canvas(bmp))
        FileOutputStream(File(outDir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }
    private fun field(a: Any, name: String): java.lang.reflect.Field = a.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun tap(v: View, x: Float, y: Float, action: Int) { val t = SystemClock.uptimeMillis(); v.dispatchTouchEvent(MotionEvent.obtain(t, t, action, x, y, 0)) }

    @Test fun fullRun_stage1_withDemoInputs_thenSaveAndNext() {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        frames(3); shot(a, "01-opening")
        a.findViewById<View>(R.id.opening).performClick(); frames(3); shot(a, "02-home")
        assertEquals(View.VISIBLE, a.findViewById<View>(R.id.setup).visibility)
        a.findViewById<Button>(R.id.startBtn).performClick(); frames(5)
        assertEquals(View.GONE, a.findViewById<View>(R.id.setup).visibility)
        val eng = field(a, "engine").get(a) as GameEngine; val opt = Stage1
        val demo = DemoDriver(eng.scenario, opt)
        val fSteer = field(a, "simSteer"); val fSig = field(a, "simSignal"); val fBrake = field(a, "simBrakeD"); val fHaz = field(a, "simHaz")
        var i = 0; var shots = 0
        while (i < 2500 && a.findViewById<View>(R.id.done).visibility != View.VISIBLE) {
            val d = demo.step(eng, 0.04)
            fSteer.set(a, d.steeringDeg.toDouble()); fSig.set(a, d.signal); fBrake.set(a, if (d.brake) 1.0 else 0.0); fHaz.set(a, d.hazardLights)
            frames(1); i++
            if (eng.inSlot) a.findViewById<Button>(R.id.pBtn).performClick()   // 사람은 P 로 끝(PM 2026-10-27)
            if (i % 300 == 0) { shots++; shot(a, "03-play-%02d".format(shots)) }
        }
        assertEquals("결과 카드가 떠야 한다(프레임 $i)", View.VISIBLE, a.findViewById<View>(R.id.done).visibility)
        shot(a, "04-done")
        val title = a.findViewById<TextView>(R.id.doneTitle).text.toString(); assertTrue(title, title.startsWith("쏙"))
        a.findViewById<EditText>(R.id.nameIn).setText("HJ"); a.findViewById<Button>(R.id.saveRec).performClick(); frames(1)
        assertTrue(a.findViewById<TextView>(R.id.saveMsg).text.toString().contains("저장"))
        a.findViewById<Button>(R.id.next).performClick(); frames(3)
        assertEquals(View.GONE, a.findViewById<View>(R.id.done).visibility)
        assertTrue(a.findViewById<TextView>(R.id.stageName).text.toString().startsWith("2."))
        shot(a, "05-next-stage")
        a.findViewById<Button>(R.id.dHome).performClick(); frames(2)
        assertTrue(a.findViewById<TextView>(R.id.lbBody).text.toString().contains("HJ") || a.findViewById<TextView>(R.id.lbBody).text.toString().contains("아직"))
    }

    @Test fun touches_wheel_brake_gear() {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        a.findViewById<View>(R.id.opening).performClick(); a.findViewById<Button>(R.id.startBtn).performClick(); frames(3)
        val wheel = a.findViewById<WheelView>(R.id.wheel); wheel.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY)); wheel.layout(0, 0, 200, 200)
        tap(wheel, 180f, 100f, MotionEvent.ACTION_DOWN); tap(wheel, 100f, 180f, MotionEvent.ACTION_MOVE); tap(wheel, 100f, 180f, MotionEvent.ACTION_UP)
        assertEquals("오른쪽 3시에서 6시로 끌면 +90°", 90.0, wheel.steer, 1.0)
        assertEquals(90.0, field(a, "simSteer").get(a) as Double, 1.0)
        val brake = a.findViewById<BrakeBarView>(R.id.brake); brake.measure(View.MeasureSpec.makeMeasureSpec(220, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(72, View.MeasureSpec.EXACTLY)); brake.layout(0, 0, 220, 72)
        tap(brake, 15f, 30f, MotionEvent.ACTION_DOWN); tap(brake, 15f, 30f, MotionEvent.ACTION_UP)   // 왼쪽 톡 = 풀림
        assertEquals(0.0, brake.depth, 0.01); frames(1)
        a.findViewById<app.parkingcoach.render.StalkView>(R.id.stalk).select("D"); frames(2)   // 브레이크 없이 D → 거부, 클러스터 팝업
        assertEquals(Signal.OFF, field(a, "simSignal").get(a) as Signal)
        assertEquals("브레이크를 밟고 변속하세요", a.findViewById<app.parkingcoach.render.ClusterView>(R.id.panel).data.msg)
        tap(brake, 210f, 30f, MotionEvent.ACTION_DOWN); tap(brake, 210f, 30f, MotionEvent.ACTION_UP)   // 오른쪽 끝 → 정지 고정
        assertEquals(1.0, brake.depth, 0.01); assertTrue(brake.latch)
        a.findViewById<app.parkingcoach.render.StalkView>(R.id.stalk).select("D"); frames(2)
        assertEquals(Signal.RIGHT, field(a, "simSignal").get(a) as Signal)
        a.findViewById<Button>(R.id.hazBtn).performClick(); assertTrue(field(a, "simHaz").get(a) as Boolean)
        a.findViewById<Button>(R.id.dTips).performClick(); assertEquals(View.VISIBLE, a.findViewById<View>(R.id.tips).visibility); a.findViewById<Button>(R.id.tipsClose).performClick()
        // 타기 전 준비(팁 안): 의자·핸들 → 끌어 보기 → 사이드미러(거울 맞추기)
        a.findViewById<Button>(R.id.dTips).performClick(); a.findViewById<Button>(R.id.prepSeatBtn).performClick(); frames(2); assertEquals(View.VISIBLE, a.findViewById<View>(R.id.prep).visibility); shot(a, "06-prep-seat")
        val pv = a.findViewById<app.parkingcoach.render.PrepView>(R.id.prepView); pv.measure(View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY)); pv.layout(0, 0, 640, 360)
        val s0 = pv.seat; tap(pv, 400f, 250f, MotionEvent.ACTION_DOWN); tap(pv, 460f, 250f, MotionEvent.ACTION_MOVE); tap(pv, 460f, 250f, MotionEvent.ACTION_UP)
        assertEquals("의자를 오른쪽으로 60px 끌면 +0.5", s0 + 0.5, pv.seat, 0.02)
        a.findViewById<Button>(R.id.prepTabSide).performClick(); frames(2); assertEquals(View.VISIBLE, a.findViewById<View>(R.id.mirprac).visibility); shot(a, "06-mirror-practice"); a.findViewById<Button>(R.id.mpClose).performClick(); a.findViewById<Button>(R.id.tipsClose).performClick()
        // 미러 하나: 탭 = 반대쪽
        val mir = a.findViewById<app.parkingcoach.render.MirrorView>(R.id.mir); val side0 = mir.side; a.findViewById<Button>(R.id.mirSw).performClick(); assertEquals(-side0, mir.side); a.findViewById<Button>(R.id.mirSw).performClick(); assertEquals(side0, mir.side)
        a.findViewById<Button>(R.id.demoBtn).performClick(); frames(60); assertEquals(View.VISIBLE, a.findViewById<View>(R.id.demoBar).visibility)
        assertTrue(a.findViewById<TextView>(R.id.demoCap).text.isNotEmpty()); shot(a, "07-demo")
        a.findViewById<Button>(R.id.demoStop).performClick(); frames(2); assertEquals(View.GONE, a.findViewById<View>(R.id.demoBar).visibility)
    }

    companion object { val Stage1: StageOpt = app.parkingcoach.sim.Stages.ALL[0] }
}
