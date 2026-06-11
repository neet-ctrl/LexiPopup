package com.lexipopup.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
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

    /**
     * Speaks [text] at [rate]. Returns true if TTS was ready and speech started,
     * false if TTS is not initialised (caller should prompt user to install a TTS engine).
     */
    fun speak(text: String, rate: Float = 0.9f): Boolean {
        if (!isReady) return false
        tts?.setSpeechRate(rate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.take(20))
        return true
    }

    /**
     * Opens the system Text-to-Speech settings so the user can install or
     * select a TTS engine. Falls back to Accessibility settings if TTS settings
     * intent is not available on the device.
     */
    fun openTtsSettings() {
        val intent = Intent("com.android.settings.TTS_SETTINGS").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }

    fun stop() = tts?.stop()

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
