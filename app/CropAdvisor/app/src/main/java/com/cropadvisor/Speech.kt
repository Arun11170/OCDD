package com.cropadvisor

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Reads advice aloud with the phone's own offline text-to-speech engine. */
class Speech(ctx: Context, private val finished: () -> Unit) {
    private var ready = false
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(ctx.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onStop(id: String?, interrupted: Boolean) { finished() }
                    override fun onDone(id: String?) { finished() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) { finished() }
                })
            }
        }
    }

    /** Returns false if the voice for this language is not installed. */
    fun speak(text: String, locale: Locale): Boolean {
        val t = tts ?: return false
        if (!ready) return false
        val r = t.setLanguage(locale)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) return false
        val max = TextToSpeech.getMaxSpeechInputLength() - 1
        return t.speak(text.take(max), TextToSpeech.QUEUE_FLUSH, null, "advice") == TextToSpeech.SUCCESS
    }

    fun stop() { tts?.stop() }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
