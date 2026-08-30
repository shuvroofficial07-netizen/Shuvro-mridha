package com.example.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.ai.ArohiBrain
import com.example.managers.AssistantStateManager
import com.example.models.AssistantState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ArohiForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isListening = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val NOTIFICATION_ID = 101
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes

        @Volatile
        var instance: ArohiForegroundService? = null
            private set

        @Volatile
        var brain: ArohiBrain? = null
            private set

        val isRunning: Boolean
            get() = instance != null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (brain == null) {
            brain = ArohiBrain(this)
        }
        AssistantStateManager.updateState(AssistantState.ONLINE)

        createNotificationChannel()
        // API 34+ throws MissingForegroundServiceTypeException if a service that
        // declares android:foregroundServiceType="microphone" calls the 2-arg
        // startForeground. The type constant itself only exists from API 30.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Arohi::BackgroundWakeLock")
            // Bounded on purpose. A 24h PARTIAL_WAKE_LOCK is exactly what Samsung's
            // power management kills, and it drains the S8+ battery. Held only long
            // enough to bridge gaps between recognition cycles.
            wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.e("ArohiService", "WakeLock acquire error", e)
        }

        handler.post { initializeSpeechRecognizer() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isListening) {
            handler.post { startListening() }
        }
        return START_STICKY
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        isListening = false
                        handler.postDelayed({ startListening() }, 1500)
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.let {
                            for (result in it) {
                                val lower = result.lowercase()
                                if (lower.contains("arohi") || lower.contains("আরোহী") || lower.contains("hey arohi") || lower.contains("হেই আরোহী")) {
                                    Log.d("ArohiVoiceEngine", "Wake word detected in background: $result")
                                    scope.launch {
                                        brain?.processUserInput(result)
                                    }
                                    break
                                }
                            }
                        }
                        handler.postDelayed({ startListening() }, 800)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            } catch (e: Exception) {
                Log.e("ArohiService", "Error creating SpeechRecognizer", e)
            }
        }
    }

    private fun startListening() {
        if (speechRecognizer != null && !isListening) {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                }
                speechRecognizer?.startListening(intent)
                isListening = true
            } catch (e: Exception) {
                Log.e("ArohiService", "Failed to start listening", e)
                isListening = false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        AssistantStateManager.updateState(AssistantState.SERVICE_STOPPED)
        handler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e("ArohiService", "Error destroying speech recognizer", e)
            }
        }
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "arohi_channel_v7",
                "Arohi Assistant Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Arohi AI Assistant active in the background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, "arohi_channel_v7")
            .setContentTitle("Arohi AI Assistant v8.0 (by Shù Vrô)")
            .setContentText("আরোহী ব্যাকগ্রাউন্ডে সক্রিয় আছে 💜")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
