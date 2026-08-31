package com.example.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.managers.AssistantStateManager
import com.example.models.AssistantState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class ArohiSpeechRecognizerManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0.15f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _statusMessage = MutableStateFlow("আরোহীকে বলুন কী করতে হবে...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var onResultCallback: ((String) -> Unit)? = null

    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(onResult: (String) -> Unit) {
        onResultCallback = onResult
        _recognizedText.value = ""
        _partialTranscript.value = ""
        _statusMessage.value = "শুনছি... আপনার কমান্ড বলুন 🎙️"
        AssistantStateManager.updateState(AssistantState.LISTENING)

        mainHandler.post {
            try {
                cleanupRecognizer()

                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    _statusMessage.value = "ভয়েস রিকগনিশন প্রস্তুত করা হচ্ছে..."
                    Log.w("ArohiSpeech", "Speech recognition not natively available, ready for input.")
                }

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createListener())
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra("android.speech.extra.DICTATION_MODE", true)
                }

                speechRecognizer?.startListening(intent)
                _isListening.value = true
            } catch (e: Exception) {
                Log.e("ArohiSpeech", "Error starting speech recognition", e)
                _isListening.value = false
                _statusMessage.value = "ভয়েস ইনপুট শুরু করা যায়নি। নিচে লিখেও পাঠাতে পারেন।"
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                _isListening.value = false
                if (AssistantStateManager.currentState.value == AssistantState.LISTENING) {
                    AssistantStateManager.updateState(AssistantState.ONLINE)
                }
            } catch (e: Exception) {
                Log.e("ArohiSpeech", "Error stopping speech recognition", e)
            }
        }
    }

    fun submitRecognizedCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isNotBlank()) {
            _recognizedText.value = trimmed
            _partialTranscript.value = ""
            _statusMessage.value = "টাস্ক প্ল্যানার প্রসেস করছে ⚡..."
            stopListening()
            onResultCallback?.invoke(trimmed)
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isListening.value = true
                _statusMessage.value = "আরোহী শুনছে... আপনার কমান্ড বলুন 🎙️"
                AssistantStateManager.updateState(AssistantState.LISTENING)
            }

            override fun onBeginningOfSpeech() {
                _statusMessage.value = "কণ্ঠস্বর শনাক্ত করা হচ্ছে..."
            }

            override fun onRmsChanged(rmsdB: Float) {
                // rmsdB is the only real microphone level Android exposes here.
                // It is a single scalar, so every bar shows that same measured
                // value. No sin() shaping and no random jitter: the visual is a
                // faithful rendering of the real signal, not a decoration.
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.1f, 1.0f)
                _rmsLevel.value = normalized
                AssistantStateManager.updateWaveform(
                    List(ArohiVoiceEngine.WAVEFORM_BARS) { normalized }
                )
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _isListening.value = false
                _statusMessage.value = "বিশ্লেষণ ও টাস্ক প্ল্যানিং তৈরি হচ্ছে..."
                AssistantStateManager.updateState(AssistantState.THINKING)
            }

            override fun onError(error: Int) {
                _isListening.value = false
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "অডিও রেকর্ডিং ত্রুটি"
                    SpeechRecognizer.ERROR_CLIENT -> "ক্লায়েন্ট সাইড ত্রুটি"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "মাইক্রোফোন অনুমতি প্রয়োজন"
                    SpeechRecognizer.ERROR_NETWORK -> "ইন্টারনেট সংযোগ ত্রুটি"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "নেটওয়ার্ক টাইমআউট"
                    SpeechRecognizer.ERROR_NO_MATCH -> "কথা স্পষ্ট বোঝা যায়নি, আবার বলুন"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ভয়েস সার্ভিস ব্যস্ত আছে"
                    SpeechRecognizer.ERROR_SERVER -> "সার্ভার রেসপন্স ত্রুটি"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "কোনো শব্দ পাওয়া যায়নি"
                    else -> "ভয়েস রিকগনিশন ত্রুটি ($error)"
                }
                Log.w("ArohiSpeech", "SpeechRecognizer error: $errorMsg ($error)")
                _statusMessage.value = errorMsg
                if (AssistantStateManager.currentState.value == AssistantState.LISTENING) {
                    AssistantStateManager.updateState(AssistantState.ONLINE)
                }
            }

            override fun onResults(results: Bundle?) {
                _isListening.value = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val bestResult = matches?.firstOrNull()?.trim()
                if (!bestResult.isNullOrBlank()) {
                    _recognizedText.value = bestResult
                    _partialTranscript.value = ""
                    _statusMessage.value = "টাস্ক প্ল্যানার সক্রিয় হয়েছে: \"$bestResult\""
                    onResultCallback?.invoke(bestResult)
                } else {
                    _statusMessage.value = "কোনো কমান্ড শনাক্ত হয়নি। আবার চেষ্টা করুন।"
                    if (AssistantStateManager.currentState.value == AssistantState.LISTENING) {
                        AssistantStateManager.updateState(AssistantState.ONLINE)
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()?.trim()
                if (!partial.isNullOrBlank()) {
                    _partialTranscript.value = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun destroy() {
        mainHandler.post {
            cleanupRecognizer()
            _isListening.value = false
        }
    }

    private fun cleanupRecognizer() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e("ArohiSpeech", "Error destroying speech recognizer", e)
        }
    }
}
