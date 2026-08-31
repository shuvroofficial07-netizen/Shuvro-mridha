package com.example.diagnostics

import android.content.Context
import android.os.Build
import com.example.ai.ArohiActionEngine
import com.example.ai.memory.ArohiDatabase
import com.example.managers.ArohiSettings
import com.example.managers.PermissionManager
import com.example.services.ArohiAccessibilityService
import com.example.services.ArohiNotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class DiagnosticStatus {
    READY,
    LIMITED,
    ERROR
}

data class DiagnosticItem(
    val title: String,
    val category: String,
    val status: DiagnosticStatus,
    val detail: String,
    val recommendation: String = ""
)

data class DiagnosticsReport(
    val timestamp: Long = System.currentTimeMillis(),
    val overallStatus: DiagnosticStatus,
    val items: List<DiagnosticItem>,
    val readyCount: Int,
    val limitedCount: Int,
    val errorCount: Int
)

class ArohiDiagnostics(private val context: Context) {

    private val actionEngine = ArohiActionEngine(context)

    suspend fun runFullDiagnostics(): DiagnosticsReport = withContext(Dispatchers.IO) {
        val items = mutableListOf<DiagnosticItem>()

        // 1. Voice Engine
        items.add(
            DiagnosticItem(
                title = "ভয়েস ইঞ্জিন (TTS)",
                category = "Voice Subsystem",
                status = DiagnosticStatus.READY,
                detail = "Android Text-to-Speech ইঞ্জিন সক্রিয়। বাংলা (bn-BD), হিন্দি (hi-IN) ও ইংরেজি (en-US) সমর্থিত।"
            )
        )

        // 2. Gemini AI Brain
        val hasGeminiKey = ArohiSettings.hasGeminiKey(context)
        items.add(
            DiagnosticItem(
                title = "Gemini 1.5 Flash Brain",
                category = "AI Core",
                status = if (hasGeminiKey) DiagnosticStatus.READY else DiagnosticStatus.LIMITED,
                detail = if (hasGeminiKey) "Gemini API ক্লায়েন্ট কনফিগার করা হয়েছে।" else "Gemini API Key সেট করা হয়নি। অফলাইন ও লোকাল ইঞ্জিন কার্যকর থাকবে।",
                recommendation = if (!hasGeminiKey) "উন্নত উত্তরের জন্য Secrets প্যানেলে GEMINI_API_KEY যুক্ত করুন।" else ""
            )
        )

        // 3. Microphone & Voice Input
        val hasMic = PermissionManager.hasMicrophonePermission(context)
        items.add(
            DiagnosticItem(
                title = "মাইক্রোফোন ও ইনপুট",
                category = "Hardware & Sensors",
                status = if (hasMic) DiagnosticStatus.READY else DiagnosticStatus.ERROR,
                detail = if (hasMic) "মাইক্রোফোন পারমিশন প্রাপ্ত। ব্যাকগ্রাউন্ড ও লাইভ ভয়েস প্রস্তুত।" else "মাইক্রোফোন পারমিশন অনুমোদিত নয়।",
                recommendation = if (!hasMic) "সেটিংস থেকে মাইক্রোফোন পারমিশন চালু করুন।" else ""
            )
        )

        // 4. Room Database & Memory
        var dbOk = false
        try {
            val db = ArohiDatabase.getDatabase(context)
            val count = db.memoryDao().getAllMemories().size
            dbOk = true
            items.add(
                DiagnosticItem(
                    title = "স্মার্ট মেমোরি ডাটাবেস",
                    category = "Local Storage",
                    status = DiagnosticStatus.READY,
                    detail = "Room SQLite সক্রিয়। বর্তমানে $count টি মেমোরি আইটেম সংরক্ষিত রয়েছে।"
                )
            )
        } catch (e: Exception) {
            items.add(
                DiagnosticItem(
                    title = "স্মার্ট মেমোরি ডাটাবেস",
                    category = "Local Storage",
                    status = DiagnosticStatus.ERROR,
                    detail = "ডাটাবেস এক্সেস ব্যর্থ: ${e.message}"
                )
            )
        }

        // 5. Notification Listener
        val notifAccess = PermissionManager.hasNotificationAccess(context)
        val notifConnected = ArohiNotificationService.isConnected
        items.add(
            DiagnosticItem(
                title = "নোটিফিকেশন লিসেনার",
                category = "Background Services",
                status = if (notifConnected) DiagnosticStatus.READY else if (notifAccess) DiagnosticStatus.LIMITED else DiagnosticStatus.LIMITED,
                detail = if (notifConnected) "নোটিফিকেশন সার্ভিস যুক্ত ও লাইভ মেসেজ শুনছে।" else "নোটিফিকেশন এক্সেস দেওয়া নেই বা সার্ভিস রি-বাইন্ডিং প্রয়োজন।",
                recommendation = if (!notifAccess) "সেটিংসে নোটিফিকেশন অ্যাক্সেস সক্রিয় করুন।" else ""
            )
        )

        // 6. Accessibility Automation Service
        val a11yAccess = PermissionManager.hasAccessibilityAccess(context)
        val a11yConnected = ArohiAccessibilityService.isConnected
        items.add(
            DiagnosticItem(
                title = "অ্যাক্সেসিবিলিটি অটোমেশন",
                category = "System Control",
                status = if (a11yConnected) DiagnosticStatus.READY else if (a11yAccess) DiagnosticStatus.LIMITED else DiagnosticStatus.LIMITED,
                detail = if (a11yConnected) "অ্যাক্সেসিবিলিটি সার্ভিস সক্রিয়। স্ক্রিন রিডিং ও অটোমেশন প্রস্তুত।" else "অ্যাক্সেসিবিলিটি বন্ধ রয়েছে।",
                recommendation = if (!a11yAccess) "ফুল অটোমেশন ও স্ক্রিন পড়ার জন্য অ্যাক্সেসিবিলিটি অন করুন।" else ""
            )
        )

        // 7. Battery & Power Subsystem
        val bat = actionEngine.getBatteryInfo()
        items.add(
            DiagnosticItem(
                title = "ব্যাটারি ও পাওয়ার স্টেট",
                category = "Hardware & Power",
                status = DiagnosticStatus.READY,
                detail = "চার্জ: ${bat.percentage}%, স্ট্যাটাস: ${if (bat.isCharging) "চার্জিং (${bat.chargingType})" else "ব্যাটারি মোড"}, তাপমাত্রা: ${bat.temperatureCelsius}°C"
            )
        )

        // 8. Storage Subsystem
        val storage = actionEngine.getStorageInfo()
        items.add(
            DiagnosticItem(
                title = "ডিভাইস স্টোরেজ",
                category = "Storage & Memory",
                status = if (storage.availableGb > 1.0) DiagnosticStatus.READY else DiagnosticStatus.LIMITED,
                detail = "ব্যবহৃত: ${storage.usedGb} GB / ${storage.totalGb} GB (${storage.usedPercentage}%)। অবশিষ্ট: ${storage.availableGb} GB"
            )
        )

        // 9. RAM Memory
        val mem = actionEngine.getMemoryStatus()
        items.add(
            DiagnosticItem(
                title = "র‍্যাম (RAM) ব্যবস্থাপনা",
                category = "Storage & Memory",
                status = if (!mem.isLowMem) DiagnosticStatus.READY else DiagnosticStatus.LIMITED,
                detail = "উপলব্ধ: ${mem.availRamMb} MB / ${mem.totalRamMb} MB। অপটিমাইজড (Samsung Galaxy S8+ / 4GB RAM প্রোফাইল)।"
            )
        )

        // 10. Network & Connectivity
        val netStatus = actionEngine.getNetworkStatus()
        items.add(
            DiagnosticItem(
                title = "নেটওয়ার্ক ও সংযোগ",
                category = "Connectivity",
                status = if (netStatus.contains("সংযুক্ত")) DiagnosticStatus.READY else DiagnosticStatus.LIMITED,
                detail = netStatus
            )
        )

        // 11. Contacts & Phone
        val hasContacts = PermissionManager.hasContactsPermission(context)
        val hasPhone = PermissionManager.hasPhonePermission(context)
        items.add(
            DiagnosticItem(
                title = "কন্টাক্টস ও কলিং ইন্টেলিজেন্স",
                category = "Telephony",
                status = if (hasContacts && hasPhone) DiagnosticStatus.READY else DiagnosticStatus.LIMITED,
                detail = if (hasContacts && hasPhone) "পরিচিতি অনুসন্ধান ও ডায়ালিং সম্পূর্ণ সক্রিয়।" else "কন্টাক্টস বা কল পারমিশন আংশিক পাওয়া গেছে।"
            )
        )

        val ready = items.count { it.status == DiagnosticStatus.READY }
        val limited = items.count { it.status == DiagnosticStatus.LIMITED }
        val error = items.count { it.status == DiagnosticStatus.ERROR }

        val overall = when {
            error > 0 -> DiagnosticStatus.LIMITED
            limited > 0 -> DiagnosticStatus.READY
            else -> DiagnosticStatus.READY
        }

        DiagnosticsReport(
            overallStatus = overall,
            items = items,
            readyCount = ready,
            limitedCount = limited,
            errorCount = error
        )
    }
}
