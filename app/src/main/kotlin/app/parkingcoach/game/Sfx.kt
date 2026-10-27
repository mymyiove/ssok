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
        if (key != "gear" && key != "parked") onDuck?.invoke()
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
    fun parked() = play("parked") { listOf(523.0, 659.0, 784.0, 1047.0).mapIndexed { i, f -> Note(f, 0.18, "sine", 0.12, i * 0.12) } }
    fun gear() = play("gear") { listOf(Note(500.0, 0.04, "square", 0.04, 0.0)) }

    fun vibrate(pattern: LongArray) { try { val v = vib ?: return; if (!v.hasVibrator()) return
        if (pattern.size == 1) v.vibrate(VibrationEffect.createOneShot(pattern[0], VibrationEffect.DEFAULT_AMPLITUDE)) else v.vibrate(VibrationEffect.createWaveform(longArrayOf(0) + pattern, -1)) } catch (e: Exception) {} }
}
