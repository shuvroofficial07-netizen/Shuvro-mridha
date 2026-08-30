package com.example.managers

import com.example.models.ActionConfirmation
import com.example.models.AssistantState
import com.example.models.ChatMessage
import com.example.models.EmotionState
import com.example.models.ProactiveSensitivity
import com.example.models.TaskPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AssistantStateManager {
    private val _currentState = MutableStateFlow(AssistantState.ONLINE)
    val currentState: StateFlow<AssistantState> = _currentState.asStateFlow()

    private val _currentEmotion = MutableStateFlow(EmotionState.CALM)
    val currentEmotion: StateFlow<EmotionState> = _currentEmotion.asStateFlow()

    private val _isSilentMode = MutableStateFlow(false)
    val isSilentMode: StateFlow<Boolean> = _isSilentMode.asStateFlow()

    private val _isPrivateMode = MutableStateFlow(false)
    val isPrivateMode: StateFlow<Boolean> = _isPrivateMode.asStateFlow()

    private val _proactiveSensitivity = MutableStateFlow(ProactiveSensitivity.NORMAL)
    val proactiveSensitivity: StateFlow<ProactiveSensitivity> = _proactiveSensitivity.asStateFlow()

    private val _activePlan = MutableStateFlow<TaskPlan?>(null)
    val activePlan: StateFlow<TaskPlan?> = _activePlan.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<ActionConfirmation?>(null)
    val pendingConfirmation: StateFlow<ActionConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = "AROHI",
                text = "নমস্কার বস! আমি আরোহী v8.0 (by Shù Vrô)। আপনার ব্যক্তিগত সহকারী হিসেবে প্রস্তুত। বলুন কী করতে হবে? 💜",
                emotion = EmotionState.HAPPY
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    /**
     * Waveform bars (normalized 0.0f - 1.0f).
     *
     * While listening these carry the microphone's real RMS level. While speaking
     * they are a flat "speech active" level, because platform TTS exposes no output
     * amplitude on API 28. Never random, never invented.
     */
    private val _waveformAmplitudes = MutableStateFlow(List(24) { 0.15f })
    val waveformAmplitudes: StateFlow<List<Float>> = _waveformAmplitudes.asStateFlow()

    fun updateState(newState: AssistantState) {
        _currentState.value = newState
        when (newState) {
            AssistantState.LISTENING -> _currentEmotion.value = EmotionState.LISTENING
            AssistantState.THINKING, AssistantState.PROCESSING -> _currentEmotion.value = EmotionState.THINKING
            AssistantState.SPEAKING -> _currentEmotion.value = EmotionState.SPEAKING
            AssistantState.EXECUTING -> _currentEmotion.value = EmotionState.EXECUTING
            AssistantState.SILENT -> _currentEmotion.value = EmotionState.SILENT
            AssistantState.ERROR -> _currentEmotion.value = EmotionState.ERROR
            AssistantState.ONLINE -> if (_currentEmotion.value == EmotionState.SPEAKING || _currentEmotion.value == EmotionState.EXECUTING) {
                _currentEmotion.value = EmotionState.HAPPY
            }
            else -> {}
        }
    }

    fun updateEmotion(newEmotion: EmotionState) {
        _currentEmotion.value = newEmotion
    }

    fun setSilentMode(silent: Boolean) {
        _isSilentMode.value = silent
        if (silent) {
            updateState(AssistantState.SILENT)
            updateEmotion(EmotionState.SILENT)
        } else if (_currentState.value == AssistantState.SILENT) {
            updateState(AssistantState.ONLINE)
            updateEmotion(EmotionState.CALM)
        }
    }

    fun setPrivateMode(privateMode: Boolean) {
        _isPrivateMode.value = privateMode
    }

    fun setProactiveSensitivity(sensitivity: ProactiveSensitivity) {
        _proactiveSensitivity.value = sensitivity
    }

    fun setActivePlan(plan: TaskPlan?) {
        _activePlan.value = plan
    }

    fun setPendingConfirmation(confirmation: ActionConfirmation?) {
        _pendingConfirmation.value = confirmation
    }

    fun addChatMessage(message: ChatMessage) {
        _chatMessages.value = _chatMessages.value + message
    }

    fun updateWaveform(amplitudes: List<Float>) {
        _waveformAmplitudes.value = amplitudes
    }
}
