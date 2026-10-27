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
                override fun onDone(id: String?) { try { am.abandonAudioFocusRequest(focus) } catch (e: Exception) {} }
                @Deprecated("") override fun onError(id: String?) { try { am.abandonAudioFocusRequest(focus) } catch (e: Exception) {} }
            })
        }
    }
    var onDuck: (() -> Unit)? = null
    fun say(text: String) {
        if (text == last) return
        last = text
        if (!ready) return
        onDuck?.invoke()
        try { am.requestAudioFocus(focus) } catch (e: Exception) {}
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "coach")
    }
    fun stop() { try { tts?.stop() } catch (e: Exception) {}; try { am.abandonAudioFocusRequest(focus) } catch (e: Exception) {} }
    fun release() { tts?.stop(); tts?.shutdown(); tts = null; try { am.abandonAudioFocusRequest(focus) } catch (e: Exception) {} }
}
