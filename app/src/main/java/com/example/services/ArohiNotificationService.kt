package com.example.services

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.ai.memory.ArohiDatabase
import com.example.ai.memory.CapturedNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ArohiNotificationService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        @Volatile
        var instance: ArohiNotificationService? = null
            private set

        val isConnected: Boolean
            get() = instance != null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.d("ArohiNotification", "Notification Listener connected.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        Log.d("ArohiNotification", "Notification Listener disconnected.")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: ""
        if (pkg == packageName) return // Ignore self-notifications

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val finalContent = if (bigText.isNotBlank()) bigText else text

        if (title.isBlank() && finalContent.isBlank()) return

        val appName = getAppLabel(pkg)
        val priority = classifyPriority(pkg, title, finalContent)

        val captured = CapturedNotification(
            packageName = pkg,
            appName = appName,
            sender = if (title.isNotBlank()) title else appName,
            title = title,
            text = finalContent,
            timestamp = sbn.postTime,
            priority = priority
        )

        scope.launch {
            try {
                val db = ArohiDatabase.getDatabase(applicationContext)
                db.notificationDao().insertNotification(captured)
            } catch (e: Exception) {
                Log.e("ArohiNotification", "Failed to store notification", e)
            }
        }
    }

    private fun getAppLabel(pkg: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            pkg.substringAfterLast('.')
        }
    }

    private fun classifyPriority(pkg: String, title: String, text: String): String {
        val lowerPkg = pkg.lowercase()
        val lowerContent = "$title $text".lowercase()

        return when {
            lowerPkg.contains("dialer") || lowerPkg.contains("telecom") || lowerPkg.contains("incall") -> "HIGH"
            lowerPkg.contains("whatsapp") || lowerPkg.contains("orca") || lowerPkg.contains("telegram") || lowerPkg.contains("mms") || lowerPkg.contains("messaging") -> "HIGH"
            lowerContent.contains("otp") || lowerContent.contains("code") || lowerContent.contains("security") || lowerContent.contains("alert") -> "HIGH"
            lowerContent.contains("promo") || lowerContent.contains("discount") || lowerContent.contains("sale") || lowerContent.contains("update available") -> "LOW"
            else -> "NORMAL"
        }
    }

    fun getActiveNotificationsList(): List<StatusBarNotification> {
        return try {
            activeNotifications?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
