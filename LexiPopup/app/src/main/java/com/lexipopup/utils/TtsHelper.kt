package com.lexipopup.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsHelper @Inject constructor(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (isReady) tts?.language = Locale.US
        }
    }

    fun speak(text: String, rate: Float = 0.9f) {
        if (!isReady) return
        tts?.setSpeechRate(rate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.take(20))
    }

    fun stop() = tts?.stop()

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
