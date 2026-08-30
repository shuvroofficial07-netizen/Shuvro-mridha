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
import kotlin.math.sin
import kotlin.random.Random

class ArohiVoiceEngine(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var waveformJob: Job? = null

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

    fun startWaveformAnimation(speaking: Boolean) {
        waveformJob?.cancel()
        waveformJob = scope.launch {
            var phase = 0.0
            while (isActive) {
                val wave = List(24) { i ->
                    val base = if (speaking) {
                        (sin(phase + i * 0.4) * 0.45 + 0.5 + Random.nextDouble(-0.1, 0.1)).toFloat().coerceIn(0.1f, 1.0f)
                    } else {
                        (sin(phase + i * 0.2) * 0.15 + 0.25).toFloat().coerceIn(0.08f, 0.4f)
                    }
                    base
                }
                AssistantStateManager.updateWaveform(wave)
                phase += 0.25
                delay(60)
            }
        }
    }

    fun stopWaveformAnimation() {
        waveformJob?.cancel()
        waveformJob = null
        AssistantStateManager.updateWaveform(List(24) { 0.12f })
    }

    fun shutdown() {
        waveformJob?.cancel()
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
