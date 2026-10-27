package app.parkingcoach.game

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 소리(파일 없음, 합성 — 시안 Web Audio 와 같은 음): 주차 거리 경고(0.3 m 안은 연속음) · 후진 경고음 띠띠띠 · 돌발 경보 · 충돌 · 완료 차임 · 기어 클릭.
 * 깜빡이·비상등 틱톡은 없다(PM 2026-10-25). 진동: 거리 경고·충돌·돌발.
 */
class Sfx(context: Context) {
    var enabled = true
    /** 경고음이 날 때 음악을 잠깐 줄인다(Music.duck) */
    var onDuck: (() -> Unit)? = null
    private val rate = 22050
    private val vib: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } catch (e: Exception) { null }

    private data class Note(val f: Double, val dur: Double, val type: String, val vol: Double, val at: Double)
    private val cache = HashMap<String, ShortArray>()

    private fun render(notes: List<Note>): ShortArray {
        val total = notes.maxOf { it.at + it.dur } + 0.03
        val n = (total * rate).toInt(); val buf = DoubleArray(n)
        for (nt in notes) { val s0 = (nt.at * rate).toInt(); val len = (nt.dur * rate).toInt()
            for (i in 0 until len) { val t = i.toDouble() / rate; val ph = 2 * PI * nt.f * t
                val w = when (nt.type) { "square" -> if (sin(ph) >= 0) 1.0 else -1.0; "sawtooth" -> 2 * ((nt.f * t) % 1.0) - 1; "triangle" -> 2 * Math.abs(2 * ((nt.f * t) % 1.0) - 1) - 1; else -> sin(ph) }
                val env = if (t < 0.005) t / 0.005 else exp(-6.0 * (t - 0.005) / nt.dur)   // 빠른 어택 · 지수 감쇠(시안 exponentialRamp)
                val k = s0 + i; if (k < n) buf[k] += w * nt.vol * env } }
        return ShortArray(n) { (buf[it].coerceIn(-1.0, 1.0) * 32767).toInt().toShort() }
    }
    private fun play(key: String, notes: () -> List<Note>) {
        if (!enabled) return
        if (key != "gear" && key != "parked" && key != "pclick") onDuck?.invoke()
        try {
            val pcm = cache.getOrPut(key) { render(notes()) }
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
            track.write(pcm, 0, pcm.size); track.setNotificationMarkerPosition(pcm.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener { override fun onMarkerReached(t: AudioTrack) { try { t.release() } catch (e: Exception) {} }; override fun onPeriodicNotification(t: AudioTrack) {} })
            track.play()
        } catch (e: Exception) {}
    }
    fun pdw() = play("pdw") { listOf(Note(2200.0, 0.06, "square", 0.08, 0.0)) }
    fun pdwCont() = play("pdwc") { listOf(Note(2200.0, 0.13, "square", 0.08, 0.0)) }
    fun rev() = play("rev") { listOf(Note(1046.0, 0.09, "sine", 0.09, 0.0)) }
    fun hazard() = play("haz") { listOf(Note(880.0, 0.12, "sawtooth", 0.12, 0.0), Note(660.0, 0.12, "sawtooth", 0.12, 0.14), Note(880.0, 0.12, "sawtooth", 0.12, 0.28)) }
    fun hit() = play("hit") { listOf(Note(120.0, 0.25, "triangle", 0.25, 0.0), Note(90.0, 0.3, "sine", 0.2, 0.02)) }
    fun pclick() = play("pclick") { listOf(Note(900.0, 0.03, "square", 0.08, 0.0), Note(500.0, 0.05, "square", 0.08, 0.04)) }
    fun parked() = play("parked") { listOf(523.0, 659.0, 784.0, 1047.0).mapIndexed { i, f -> Note(f, 0.18, "sine", 0.12, i * 0.12) } }
    fun gear() = play("gear") { listOf(Note(500.0, 0.04, "square", 0.04, 0.0)) }
    fun lock() = play("lock") { listOf(Note(140.0, 0.05, "triangle", 0.12, 0.0), Note(80.0, 0.08, "sine", 0.1, 0.0)) }   // 핸들 끝까지 '툭'(PM 2026-10-27)

    // 엔진음(합성 스트리밍): 시동 때 부릉 한 번 → 낮은 울림, 속도에 따라 조금 올라감. 크지 않게(PM 2026-10-27 "부릉 소리는 나야 차 탄 것 같다"). 시안 engStart/engUpdate 와 같은 수치
    @Volatile private var engF = 44.0; @Volatile private var engG = 0.0; @Volatile private var engLp = 200.0
    private var engThread: Thread? = null
    fun engine(run: Boolean, kmh: Double) {
        if (!enabled || !run) { engG = 0.0; return }
        engF = 44 + minOf(kmh, 12.0) * 3.2; engG = if (kmh > 0.3) 0.05 else 0.035; engLp = 200 + kmh * 20
        if (engThread == null) engThread = Thread { engLoop() }.apply { isDaemon = true; start() }
    }
    private fun engLoop() {
        val track = try { AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(8192).build() } catch (e: Exception) { engThread = null; return }
        try { track.play() } catch (e: Exception) { engThread = null; return }
        val ph = DoubleArray(4); val det = doubleArrayOf(1.0, 1.00405, 0.99712); var f = 42.0; var g = 0.0; var lp = 200.0; var y = 0.0; var t = 0.0; val buf = ShortArray(1024); var quiet = 0
        while (true) {
            for (i in buf.indices) {
                val target = if (t < 0.35) 42 + 53 * (t / 0.35) else if (t < 1.1) 95 - 51 * ((t - 0.35) / 0.75) else engF   // 부릉: 올렸다 내림
                val gTarget = if (t < 0.25) 0.06 * (t / 0.25) else if (t < 1.1) 0.06 else engG
                f += (target - f) * 0.0004; g += (gTarget - g) * 0.0003; lp += (engLp - lp) * 0.0004
                var sm = 0.0; for (k in 0..2) { ph[k] += f * det[k] / rate; if (ph[k] >= 1) ph[k] -= 1; sm += 2 * ph[k] - 1 }
                ph[3] += f / rate; if (ph[3] >= 1) ph[3] -= 1; sm += sin(2 * PI * ph[3])
                y += (sm / 4 - y) * (1 - exp(-2 * PI * lp / rate))   // 1차 저역 통과
                buf[i] = (y * g * 1.6 * 32767).coerceIn(-32767.0, 32767.0).toInt().toShort(); t += 1.0 / rate }
            if (track.write(buf, 0, buf.size) < 0) break
            quiet = if (engG == 0.0 && g < 0.0005) quiet + 1 else 0; if (quiet > 8) break
        }
        try { track.stop(); track.release() } catch (e: Exception) {}
        engThread = null
    }

    fun vibrate(pattern: LongArray) { try { val v = vib ?: return; if (!v.hasVibrator()) return
        if (pattern.size == 1) v.vibrate(VibrationEffect.createOneShot(pattern[0], VibrationEffect.DEFAULT_AMPLITUDE)) else v.vibrate(VibrationEffect.createWaveform(longArrayOf(0) + pattern, -1)) } catch (e: Exception) {} }
}
