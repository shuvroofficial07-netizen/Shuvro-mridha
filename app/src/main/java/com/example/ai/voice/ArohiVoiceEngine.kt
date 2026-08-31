package com.example.ai.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.managers.AssistantStateManager
import com.example.models.AssistantState
import com.example.models.EmotionState
import kotlinx.coroutines.*
import java.util.Locale

class ArohiVoiceEngine(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        const val WAVEFORM_BARS = 24
        const val WAVEFORM_IDLE_LEVEL = 0.12f
        const val SPEECH_ACTIVE_LEVEL = 0.45f
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setPitch(1.15f) // Warm, feminine AI tone
            tts?.setSpeechRate(1.0f)

            // Setup utterance listener
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    AssistantStateManager.updateState(AssistantState.SPEAKING)
                    startWaveformAnimation(true)
                }

                override fun onDone(utteranceId: String?) {
                    stopWaveformAnimation()
                    if (!AssistantStateManager.isSilentMode.value) {
                        AssistantStateManager.updateState(AssistantState.ONLINE)
                    }
                }

                override fun onError(utteranceId: String?) {
                    stopWaveformAnimation()
                    if (!AssistantStateManager.isSilentMode.value) {
                        AssistantStateManager.updateState(AssistantState.ONLINE)
                    }
                }
            })

            // Try setting Bengali or English as default
            val bengali = Locale("bn", "BD")
            val available = tts?.isLanguageAvailable(bengali)
            if (available == TextToSpeech.LANG_AVAILABLE || available == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                tts?.language = bengali
            } else {
                tts?.language = Locale.ENGLISH
            }
            Log.d("ArohiVoiceEngine", "TTS Initialized successfully.")
        } else {
            Log.e("ArohiVoiceEngine", "TTS Initialization failed.")
        }
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (AssistantStateManager.isSilentMode.value) {
            Log.d("ArohiVoiceEngine", "Silent mode is active. Suppressing speech output.")
            return
        }

        if (text.isBlank()) return

        if (!isInitialized || tts == null) {
            Log.w("ArohiVoiceEngine", "TTS not initialized yet. Skipping speech.")
            return
        }

        // Auto-detect language
        val targetLocale = detectLanguage(text)
        try {
            tts?.language = targetLocale
        } catch (e: Exception) {
            tts?.language = Locale.ENGLISH
        }

        val utteranceId = "arohi_${System.currentTimeMillis()}"
        val params = Bundle()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        tts?.stop()
        stopWaveformAnimation()
        if (AssistantStateManager.currentState.value == AssistantState.SPEAKING) {
            AssistantStateManager.updateState(AssistantState.ONLINE)
        }
    }

    private fun detectLanguage(text: String): Locale {
        val hasBengali = text.any { it in '\u0980'..'\u09FF' }
        val hasDevanagari = text.any { it in '\u0900'..'\u097F' }

        return when {
            hasBengali -> Locale("bn", "BD")
            hasDevanagari -> Locale("hi", "IN")
            else -> Locale.ENGLISH
        }
    }

    /**
     * Speech-activity indicator.
     *
     * Platform TextToSpeech exposes no output amplitude, and AudioPlaybackCapture
     * requires API 29 while this app targets the Galaxy S8+ (API 28). So there is
     * no real per-frame audio level available on the target device. Rather than
     * fabricate one with sin()+Random, this publishes a flat level that means
     * "Arohi is speaking" and nothing more. The visual must never be read as a
     * measured amplitude.
     */
    fun startWaveformAnimation(speaking: Boolean) {
        val level = if (speaking) SPEECH_ACTIVE_LEVEL else WAVEFORM_IDLE_LEVEL
        AssistantStateManager.updateWaveform(List(WAVEFORM_BARS) { level })
    }

    fun stopWaveformAnimation() {
        AssistantStateManager.updateWaveform(List(WAVEFORM_BARS) { WAVEFORM_IDLE_LEVEL })
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
