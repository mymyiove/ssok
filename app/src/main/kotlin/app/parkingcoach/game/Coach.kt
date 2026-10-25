package app.parkingcoach.game

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** 코칭 문장을 음성으로. 엔진이 없으면 자막만. 같은 문장은 한 번만 읽는다. */
class Coach(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var last = ""
    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS && tts?.setLanguage(Locale.KOREAN) != TextToSpeech.LANG_NOT_SUPPORTED
            tts?.setSpeechRate(1.05f)
        }
    }
    fun say(text: String) {
        if (text == last) return
        last = text
        if (ready) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "coach")
    }
    fun release() { tts?.stop(); tts?.shutdown(); tts = null }
}
