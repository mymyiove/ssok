package app.parkingcoach.game

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * 코칭 문장을 음성으로. 엔진이 없으면 자막만. 같은 문장은 한 번만 읽는다.
 * 오디오 포커스(AAOS 규칙, 리서치 2026-10-27): 음성은 USAGE_ASSISTANT + GAIN_TRANSIENT_MAY_DUCK(음악이 잠깐 작아짐), 내비 usage 는 쓰지 않는다(내비 앱 전용 채널).
 */
class Coach(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var last = ""
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build()
    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS && tts?.setLanguage(Locale.KOREAN) != TextToSpeech.LANG_NOT_SUPPORTED
            tts?.setSpeechRate(1.05f); tts?.setAudioAttributes(attrs)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { val n = pending; pending = null; if (n != null) speakNow(n, false) else try { am.abandonAudioFocusRequest(focus) } catch (e: Exception) {} }
                @Deprecated("") override fun onError(id: String?) { try { am.abandonAudioFocusRequest(focus) } catch (e: Exception) {} }
            })
        }
    }
    var onDuck: (() -> Unit)? = null
    // 대사 순서(R12, 시안 say/sayNow 와 같다): 급한 말(돌발·충돌)만 끊고 바로, 나머지는 지금 말을 끝낸 뒤 가장 최근 것 하나 — 다음 단계로 넘어가며 앞 대사가 잘리던 것
    @Volatile private var pending: String? = null
    fun say(text: String, urgent: Boolean = false) {
        if (text == last) return
        last = text
        if (!ready) return
        if (!urgent && tts?.isSpeaking == true) { pending = text; return }
        pending = null; speakNow(text, urgent)
    }
    private fun speakNow(text: String, flush: Boolean) {
        onDuck?.invoke()
        try { am.requestAudioFocus(focus) } catch (e: Exception) {}
        tts?.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "coach")
    }
    fun stop() { pending = null; try { tts?.stop() } catch (e: Exception) {}; try { am.abandonAudioFocusRequest(focus) } catch (e: Exception) {} }
    fun release() { tts?.stop(); tts?.shutdown(); tts = null; try { am.abandonAudioFocusRequest(focus) } catch (e: Exception) {} }
}
