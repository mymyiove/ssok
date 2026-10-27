package app.parkingcoach.game

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper

/**
 * 음악(연주곡, PM이 SUNO 로 만든 mp3 를 assets/music/ 에 넣는다 — 2026-10-27): ⏮ ▶/⏸ ⏭. 파일이 없으면 onEmpty.
 * 더킹: 코치 음성·주차 거리 경고음·후진 경고음·돌발·충돌이 나올 때 1.5초 동안 0.2 로 줄였다가 되돌린다(Sfx.onDuck · Coach.onDuck).
 */
class Music(private val ctx: Context) {
    private val files: List<String> = try { (ctx.assets.list("music") ?: emptyArray()).filter { it.endsWith(".mp3", true) || it.endsWith(".ogg", true) || it.endsWith(".m4a", true) }.sorted() } catch (e: Exception) { emptyList() }
    private var mp: MediaPlayer? = null
    private var idx = 0
    var playing = false; private set
    var onState: ((Boolean) -> Unit)? = null
    var onEmpty: (() -> Unit)? = null
    private val h = Handler(Looper.getMainLooper()); private val unduck = Runnable { try { mp?.setVolume(VOL, VOL) } catch (e: Exception) {} }
    val isEmpty get() = files.isEmpty()
    /** 곡 이름(파일 이름에서 번호·밑줄 정리): 01_easy_parking.mp3 → easy parking */
    val title: String get() = files.getOrNull(idx)?.substringBeforeLast(".")?.replace(Regex("^\\d+_"), "")?.replace("_", " ") ?: "음악 꺼짐"

    private fun play(i: Int) {
        if (files.isEmpty()) { onEmpty?.invoke(); return }
        idx = ((i % files.size) + files.size) % files.size
        try {
            mp?.release(); val p = MediaPlayer(); mp = p
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            ctx.assets.openFd("music/" + files[idx]).use { fd -> p.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            p.setOnCompletionListener { next() }; p.prepare(); p.setVolume(VOL, VOL); p.start(); playing = true; onState?.invoke(true)
        } catch (e: Exception) { playing = false; onState?.invoke(false) }
    }
    fun toggle() { if (playing) { try { mp?.pause() } catch (e: Exception) {}; playing = false; onState?.invoke(false) } else if (mp != null) { try { mp?.start(); playing = true; onState?.invoke(true) } catch (e: Exception) { play(idx) } } else play(idx) }
    fun next() = play(idx + 1)
    fun prev() = play(idx - 1)
    fun duck() { if (!playing) return; try { mp?.setVolume(0.2f, 0.2f) } catch (e: Exception) {}; h.removeCallbacks(unduck); h.postDelayed(unduck, 1500) }
    fun release() { try { mp?.release() } catch (e: Exception) {}; mp = null; playing = false }
    companion object { const val VOL = 0.55f }
}
