package app.parkingcoach.game

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper

/**
 * 음악(연주곡, PM이 SUNO 로 만든 mp3 를 assets/music/ 에 넣는다 — 2026-10-27): ⏮ ▶/⏸ ⏭. 파일이 없으면 onEmpty.
 * 더킹: 코치 음성·주차 거리 경고음·후진 경고음·돌발·충돌이 나올 때 1.5초 동안 0.2 로 줄였다가 되돌린다(Sfx.onDuck · Coach.onDuck).
 * 오디오 포커스(AAOS, 리서치 2026-10-27): 음악은 USAGE_MEDIA + AUDIOFOCUS_GAIN — 통화 중엔 거절(REJECT)되니 재생하지 않고, 잠깐 뺏기면 멈췄다가 돌아오면 이어서, CAN_DUCK 이면 줄인다.
 */
class Music(private val ctx: Context) {
    private val files: List<String> = try { (ctx.assets.list("music") ?: emptyArray()).filter { it.endsWith(".mp3", true) || it.endsWith(".ogg", true) || it.endsWith(".m4a", true) }.sorted() } catch (e: Exception) { emptyList() }
    private var mp: MediaPlayer? = null
    private var idx = 0
    var playing = false; private set
    var onState: ((Boolean) -> Unit)? = null
    var onEmpty: (() -> Unit)? = null
    private val h = Handler(Looper.getMainLooper()); private val unduck = Runnable { try { mp?.setVolume(VOL, VOL) } catch (e: Exception) {} }
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private var resumeAfterLoss = false
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs).setWillPauseWhenDucked(false).setOnAudioFocusChangeListener { f ->
        when (f) {
            AudioManager.AUDIOFOCUS_LOSS -> { resumeAfterLoss = false; pause() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { resumeAfterLoss = playing; pause() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { try { mp?.setVolume(0.2f, 0.2f) } catch (e: Exception) {} }
            AudioManager.AUDIOFOCUS_GAIN -> { try { mp?.setVolume(VOL, VOL) } catch (e: Exception) {}; if (resumeAfterLoss) { resumeAfterLoss = false; resume() } }
        } }.build()
    private fun pause() { try { mp?.pause() } catch (e: Exception) {}; playing = false; onState?.invoke(false) }
    private fun resume() { try { mp?.start(); playing = true; onState?.invoke(true) } catch (e: Exception) { play(idx) } }
    val isEmpty get() = files.isEmpty()
    /** 곡 이름(파일 이름에서 번호·밑줄 정리): 01_easy_parking.mp3 → easy parking */
    val title: String get() = files.getOrNull(idx)?.substringBeforeLast(".")?.replace(Regex("^\\d+_"), "")?.replace("_", " ") ?: "음악 꺼짐"

    private fun play(i: Int) {
        if (files.isEmpty()) { onEmpty?.invoke(); return }
        idx = ((i % files.size) + files.size) % files.size
        if (try { am.requestAudioFocus(focus) } catch (e: Exception) { AudioManager.AUDIOFOCUS_REQUEST_FAILED } != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { playing = false; onState?.invoke(false); return }   // 통화 중 등 거절이면 재생하지 않는다
        try {
            mp?.release(); val p = MediaPlayer(); mp = p
            p.setAudioAttributes(attrs)
            ctx.assets.openFd("music/" + files[idx]).use { fd -> p.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            p.setOnCompletionListener { next() }; p.prepare(); p.setVolume(VOL, VOL); p.start(); playing = true; onState?.invoke(true)
        } catch (e: Exception) { playing = false; onState?.invoke(false) }
    }
    fun toggle() { if (playing) { pause(); try { am.abandonAudioFocusRequest(focus) } catch (e: Exception) {} } else if (mp != null) { if (try { am.requestAudioFocus(focus) } catch (e: Exception) { AudioManager.AUDIOFOCUS_REQUEST_FAILED } == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) resume() } else play(idx) }
    fun next() = play(idx + 1)
    fun prev() = play(idx - 1)
    fun duck() { if (!playing) return; try { mp?.setVolume(0.2f, 0.2f) } catch (e: Exception) {}; h.removeCallbacks(unduck); h.postDelayed(unduck, 1500) }
    fun release() { try { mp?.release() } catch (e: Exception) {}; mp = null; playing = false; try { am.abandonAudioFocusRequest(focus) } catch (e: Exception) {} }
    companion object { const val VOL = 0.55f }
}
