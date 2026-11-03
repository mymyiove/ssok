package app.parkingcoach.run

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.Button
import app.parkingcoach.MainActivity
import app.parkingcoach.R
import app.parkingcoach.game.DemoDriver
import app.parkingcoach.game.GameEngine
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration

/**
 * 프레임마다 새로 잡는 메모리(에뮬레이터 2026-11-03: 초당 약 13 MB → GC 로 끊김 위험). 카메라·어라운드뷰·미러·계기판을 한 번씩 그릴 때
 * 이 스레드가 새로 잡은 바이트를 재서 상한을 건다. 숫자는 build 로그(System.out)에도 남긴다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h720dp-land-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)   // LEGACY 는 그리기를 글로 기록해 수백 MB 가 잡힌다
class AllocTest {
    @Before fun pause() { ShadowChoreographer.setPaused(true); ShadowChoreographer.setFrameDelay(Duration.ofMillis(16)) }
    private fun frames(n: Int) { repeat(n) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40)) } }
    private fun field(a: Any, name: String) = a.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun sized(v: View, w: Int, h: Int) { v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)); v.layout(0, 0, w, h) }

    @Test fun bytesPerFrame() {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        a.findViewById<View>(R.id.opening).performClick(); a.findViewById<Button>(R.id.startBtn).performClick(); frames(3)
        val eng = field(a, "engine").get(a) as GameEngine; val demo = DemoDriver(eng.scenario, RunThroughTest.Stage1)
        val fSteer = field(a, "simSteer"); val fSig = field(a, "simSignal"); val fBrake = field(a, "simBrakeD")
        repeat(120) { val d = demo.step(eng, 0.04); fSteer.set(a, d.steeringDeg.toDouble()); fSig.set(a, d.signal); fBrake.set(a, if (d.brake) 1.0 else 0.0); frames(1) }
        val views = listOf(R.id.cam to (850 to 520), R.id.topView to (360 to 520), R.id.mir to (360 to 320), R.id.panel to (420 to 70)).map { (id, s) -> a.findViewById<View>(id).also { sized(it, s.first, s.second) } }
        val bmp = Bitmap.createBitmap(850, 520, Bitmap.Config.ARGB_8888); val c = Canvas(bmp)
        repeat(5) { views.forEach { it.draw(c) } }   // 데우기(캐시·셰이더 첫 생성은 빼고 잰다)
        // android.jar 에는 java.lang.management 가 없어 리플렉션으로(테스트는 JVM 에서 돈다)
        val mx = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        val m = Class.forName("com.sun.management.ThreadMXBean").getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType); val tid = Thread.currentThread().id
        fun alloc(): Long = m.invoke(mx, tid) as Long
        val per: List<Long> = views.map { v -> val b0 = alloc(); repeat(20) { v.draw(c) }; (alloc() - b0) / 20 }
        val total = per.sum()
        println("ALLOC per frame: cam=${per[0]} top=${per[1]} mir=${per[2]} panel=${per[3]} total=$total")
        assertTrue("한 프레임 새 할당 ${total / 1024} KB", total < ALLOC_LIMIT)
    }
    companion object { const val ALLOC_LIMIT = 512L * 1024 }   // 2026-11-03 줄인 뒤 약 240 KB(줄이기 전 2.4 MB)
}
