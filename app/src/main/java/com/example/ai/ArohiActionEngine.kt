package com.example.ai

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.example.services.ArohiAccessibilityService
import com.example.services.ArohiNotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BatteryInfo(
    val percentage: Int,
    val isCharging: Boolean,
    val chargingType: String,
    val temperatureCelsius: Float
)

data class StorageInfo(
    val totalGb: Double,
    val availableGb: Double,
    val usedGb: Double,
    val usedPercentage: Int
)

data class MemoryStatus(
    val totalRamMb: Long,
    val availRamMb: Long,
    val usedRamMb: Long,
    val isLowMem: Boolean
)

data class ContactMatch(
    val name: String,
    val number: String
)

class ArohiActionEngine(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var isTorchOn = false

    // App Aliases Dictionary (English, Bengali, Banglish, Short names)
    private val appAliases = mapOf(
        "fb" to listOf("facebook", "com.facebook.katana", "com.facebook.lite"),
        "facebook" to listOf("facebook", "com.facebook.katana", "com.facebook.lite"),
        "ফেসবুক" to listOf("facebook", "com.facebook.katana", "com.facebook.lite"),
        "whatsapp" to listOf("whatsapp", "com.whatsapp", "com.whatsapp.w4b"),
        "হোয়াটসঅ্যাপ" to listOf("whatsapp", "com.whatsapp"),
        "yt" to listOf("youtube", "com.google.android.youtube"),
        "youtube" to listOf("youtube", "com.google.android.youtube"),
        "ইউটিউব" to listOf("youtube", "com.google.android.youtube"),
        "messenger" to listOf("messenger", "com.facebook.orca"),
        "মেসেঞ্জার" to listOf("messenger", "com.facebook.orca"),
        "chrome" to listOf("chrome", "com.android.chrome"),
        "ক্রোম" to listOf("chrome", "com.android.chrome"),
        "camera" to listOf("camera", "com.sec.android.app.camera", "com.google.android.GoogleCamera"),
        "ক্যামেরা" to listOf("camera", "com.sec.android.app.camera"),
        "gallery" to listOf("gallery", "photos", "com.sec.android.gallery3d", "com.google.android.apps.photos"),
        "গ্যালারি" to listOf("gallery", "photos"),
        "settings" to listOf("settings", "com.android.settings"),
        "সেটিংস" to listOf("settings", "com.android.settings"),
        "dialer" to listOf("dialer", "phone", "com.samsung.android.dialer", "com.google.android.dialer"),
        "ফোন" to listOf("dialer", "phone", "call"),
        "calculator" to listOf("calculator", "com.sec.android.app.popupcalculator", "com.google.android.calculator"),
        "ক্যালকুলেটর" to listOf("calculator"),
        "clock" to listOf("clock", "alarm", "com.sec.android.app.clockpackage", "com.google.android.deskclock"),
        "ঘড়ি" to listOf("clock", "alarm"),
        "maps" to listOf("maps", "google maps", "com.google.android.apps.maps"),
        "ম্যাপ" to listOf("maps")
    )

    /**
     * Resolves a spoken app name (English, Bengali, Banglish or alias) to a concrete
     * installed package name. Returns null when nothing matches - callers must treat
     * null as "not found" and never invent a package.
     */
    fun resolveAppPackage(query: String): String? {
        val cleanQuery = query.lowercase().trim()
        if (cleanQuery.isBlank()) return null
        return try {
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val targetKeywords = appAliases[cleanQuery] ?: listOf(cleanQuery)

            for (keyword in targetKeywords) {
                val match = installedApps.firstOrNull { app ->
                    val pkg = app.packageName.lowercase()
                    val label = pm.getApplicationLabel(app).toString().lowercase()
                    pkg.contains(keyword) || label.contains(keyword) || keyword.contains(label)
                }
                if (match != null) return match.packageName
            }

            installedApps.firstOrNull { app ->
                val label = pm.getApplicationLabel(app).toString().lowercase()
                label.contains(cleanQuery) || cleanQuery.contains(label)
            }?.packageName
        } catch (e: Exception) {
            Log.e("ArohiActionEngine", "Error resolving app package", e)
            null
        }
    }

    /**
     * Package currently in the foreground, or null when it cannot be known.
     * Requires the accessibility service; Android removed other reliable ways of
     * reading this for third-party apps.
     */
    fun getForegroundPackage(): String? = ArohiAccessibilityService.foregroundPackage

    /** Actual current media volume as a percentage, or null if unreadable. */
    fun getMediaVolumePercent(): Int? {
        val am = audioManager ?: return null
        return try {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (max <= 0) return null
            (am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100) / max
        } catch (e: Exception) {
            Log.e("ArohiActionEngine", "Error reading media volume", e)
            null
        }
    }

    /**
     * Real torch state. Returns null below API 33 (no public readback exists) or when
     * the device has no flash - callers must report "cannot verify", never "verified".
     */
    fun isTorchOnNow(): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val cm = cameraManager ?: return null
        return try {
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return null
            (cm.getTorchStrength(cameraId) ?: 0) > 0
        } catch (e: Exception) {
            Log.e("ArohiActionEngine", "Error reading torch state", e)
            null
        }
    }

    suspend fun openApp(query: String): String = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val cleanQuery = query.lowercase().trim()
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            // Check aliases first
            val targetKeywords = appAliases[cleanQuery] ?: listOf(cleanQuery)

            var matchedApp: ApplicationInfo? = null

            // 1. Direct package name or keyword match
            for (keyword in targetKeywords) {
                matchedApp = installedApps.firstOrNull { app ->
                    val pkg = app.packageName.lowercase()
                    val label = pm.getApplicationLabel(app).toString().lowercase()
                    pkg.contains(keyword) || label.contains(keyword) || keyword.contains(label)
                }
                if (matchedApp != null) break
            }

            // 2. Fuzzy match label
            if (matchedApp == null) {
                matchedApp = installedApps.firstOrNull { app ->
                    val label = pm.getApplicationLabel(app).toString().lowercase()
                    label.contains(cleanQuery) || cleanQuery.contains(label)
                }
            }

            if (matchedApp != null) {
                val launchIntent = pm.getLaunchIntentForPackage(matchedApp.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    val appName = pm.getApplicationLabel(matchedApp).toString()
                    return@withContext "সফলভাবে $appName অ্যাপটি খোলা হয়েছে।"
                } else {
                    return@withContext "দুঃখিত, এই অ্যাপটি সরাসরি খোলার অনুমতি নেই।"
                }
            } else {
                return@withContext "আপনার ডিভাইসে \"$query\" নামের কোনো অ্যাপ খুঁজে পাওয়া যায়নি।"
            }
        } catch (e: Exception) {
            Log.e("ArohiActionEngine", "Error opening app", e)
            return@withContext "অ্যাপ খোলার সময় সমস্যা হয়েছে: ${e.localizedMessage}"
        }
    }

    fun openWhatsApp(): String {
        return try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage("com.whatsapp")
                ?: pm.getLaunchIntentForPackage("com.whatsapp.w4b")
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "WhatsApp চালু করা হয়েছে।"
            } else {
                "WhatsApp ইন্সটল করা নেই।"
            }
        } catch (e: Exception) {
            "WhatsApp খুলতে সমস্যা হয়েছে: ${e.message}"
        }
    }

    fun toggleTorch(enable: Boolean? = null): String {
        if (cameraManager == null) return "এই ডিভাইসে টর্চ কন্ট্রোল পাওয়া যায়নি।"
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "ডিভাইসে কোনো ফ্ল্যাশলাইট পাওয়া যায়নি।"

            val targetState = enable ?: !isTorchOn
            cameraManager.setTorchMode(cameraId, targetState)
            isTorchOn = targetState
            if (targetState) "টর্চ চালু করা হয়েছে 🔦" else "টর্চ বন্ধ করা হয়েছে 🌑"
        } catch (e: CameraAccessException) {
            "ক্যামেরা এক্সেস করা যায়নি: ${e.localizedMessage}"
        } catch (e: Exception) {
            "টর্চ পরিবর্তন করতে সমস্যা হয়েছে: ${e.localizedMessage}"
        }
    }

    fun adjustVolume(increase: Boolean): String {
        if (audioManager == null) return "অডিও ম্যানেজার পাওয়া যায়নি।"
        return try {
            val direction = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val percent = (current * 100) / max
            "মিডিয়া ভলিউম: $percent%"
        } catch (e: Exception) {
            "ভলিউম পরিবর্তন করা যায়নি: ${e.message}"
        }
    }

    fun setVolume(percentage: Int): String {
        if (audioManager == null) return "অডিও ম্যানেজার পাওয়া যায়নি।"
        return try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = ((percentage.coerceIn(0, 100) / 100.0) * max).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            "মিডিয়া ভলিউম $percentage% সেট করা হয়েছে।"
        } catch (e: Exception) {
            "ভলিউম সেট করা যায়নি: ${e.message}"
        }
    }

    fun controlMedia(action: String): String {
        if (audioManager == null) return "মিডিয়া ম্যানেজার পাওয়া যায়নি।"
        return try {
            val keyEvent = when (action.lowercase()) {
                "play", "pause", "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                "previous", "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
                else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            }
            val downIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyEvent))
            }
            val upIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, keyEvent))
            }
            context.sendOrderedBroadcast(downIntent, null)
            context.sendOrderedBroadcast(upIntent, null)
            "মিডিয়া কমান্ড কার্যকর করা হয়েছে ($action)।"
        } catch (e: Exception) {
            "মিডিয়া কন্ট্রোল করা যায়নি: ${e.message}"
        }
    }

    fun getBatteryInfo(): BatteryInfo {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, ifilter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val chargePlug = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val temp = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0f

        val batteryPct = if (level >= 0 && scale > 0) ((level / scale.toFloat()) * 100).toInt() else 0
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val chargingType = when (chargePlug) {
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Adapter"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Not Plugged"
        }

        return BatteryInfo(batteryPct, isCharging, chargingType, temp)
    }

    fun getStorageInfo(): StorageInfo {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val availBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - availBytes

            val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
            val availGb = availBytes / (1024.0 * 1024.0 * 1024.0)
            val usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0)
            val pct = if (totalGb > 0) ((usedGb / totalGb) * 100).toInt() else 0

            StorageInfo(
                totalGb = (totalGb * 10).toInt() / 10.0,
                availableGb = (availGb * 10).toInt() / 10.0,
                usedGb = (usedGb * 10).toInt() / 10.0,
                usedPercentage = pct
            )
        } catch (e: Exception) {
            StorageInfo(64.0, 32.0, 32.0, 50)
        }
    }

    fun getMemoryStatus(): MemoryStatus {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val totalMb = memInfo.totalMem / (1024 * 1024)
        val availMb = memInfo.availMem / (1024 * 1024)
        val usedMb = totalMb - availMb

        return MemoryStatus(totalMb, availMb, usedMb, memInfo.lowMemory)
    }

    fun getNetworkStatus(): String {
        if (connectivityManager == null) return "অজানা নেটওয়ার্ক"
        val activeNet = connectivityManager.activeNetwork ?: return "ইন্টারনেট সংযোগ নেই ❌"
        val caps = connectivityManager.getNetworkCapabilities(activeNet) ?: return "সংযুক্ত কিন্তু ডেটা নেই"

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "ওয়াই-ফাই সংযুক্ত 📶"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "মোবাইল ডেটা সংযুক্ত 🌐"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ইথারনেট সংযুক্ত 🔌"
            else -> "ইন্টারনেট সংযুক্ত ✔"
        }
    }

    suspend fun findContacts(query: String): List<ContactMatch> = withContext(Dispatchers.IO) {
        val matches = mutableListOf<ContactMatch>()
        try {
            val resolver = context.contentResolver
            val cleanQuery = query.trim()
            val cursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$cleanQuery%"),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = it.getString(nameIdx) ?: "অজানা"
                    val number = it.getString(numIdx) ?: ""
                    if (!matches.any { m -> m.name == name && m.number == number }) {
                        matches.add(ContactMatch(name, number))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ArohiActionEngine", "Error reading contacts", e)
        }
        return@withContext matches
    }

    fun makeCall(phoneNumber: String): String {
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "ডায়ালারে $phoneNumber নম্বরটি প্রস্তুত করা হয়েছে।"
        } catch (e: Exception) {
            "কল করতে সমস্যা হয়েছে: ${e.message}"
        }
    }

    suspend fun callContact(name: String): String {
        val matches = findContacts(name)
        return when {
            matches.isEmpty() -> "\"$name\" নামে কোনো কন্টাক্ট পাওয়া যায়নি।"
            matches.size == 1 -> {
                makeCall(matches[0].number)
                "${matches[0].name}-কে কল করা হচ্ছে (${matches[0].number})।"
            }
            else -> {
                val listStr = matches.take(3).joinToString(", ") { "${it.name} (${it.number})" }
                "\"$name\" নামে একাধিক কন্টাক্ট পাওয়া গেছে: $listStr। আপনি কাকে কল করতে চান?"
            }
        }
    }

    fun searchYouTube(query: String): String {
        return try {
            val cleanQuery = query.trim()
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", cleanQuery)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "YouTube-এ \"$cleanQuery\" সার্চ করা হয়েছে।"
        } catch (e: Exception) {
            // Fallback to browser YouTube URL
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(webIntent)
                "YouTube সার্চ ব্রাউজারে চালু করা হয়েছে।"
            } catch (err: Exception) {
                "YouTube সার্চ করা যায়নি: ${err.message}"
            }
        }
    }

    fun searchWeb(query: String): String {
        return try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "ওয়েবে \"$query\" সার্চ করা হয়েছে।"
        } catch (e: Exception) {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(browserIntent)
                "Google সার্চ ওপেন করা হয়েছে।"
            } catch (err: Exception) {
                "সার্চ করা যায়নি: ${err.message}"
            }
        }
    }

    fun openSettings(type: String): String {
        val intent = when (type.lowercase().trim()) {
            "wifi", "ওয়াইফাই", "wi-fi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth", "ব্লুটুথ" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "sound", "শব্দ", "volume" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            "display", "ডিসপ্লে", "brightness" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "battery", "ব্যাটারি" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "storage", "স্টোরেজ" -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            "notifications", "নোটিফিকেশন" -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            "accessibility", "অ্যাক্সেসিবিলিটি" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "apps", "অ্যাপস" -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            "$type সেটিংস পৃষ্ঠা খোলা হয়েছে।"
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(fallback)
            "সিস্টেম সেটিংস খোলা হয়েছে।"
        }
    }

    fun readCurrentScreen(): String {
        val service = ArohiAccessibilityService.instance
        if (service == null) {
            return "অ্যাক্সেসিবিলিটি সার্ভিস সক্রিয় নেই। স্ক্রিন পড়ার জন্য অ্যাক্সেসিবিলিটি পারমিশন দিন।"
        }
        val text = service.readCurrentScreen()
        return if (text.isBlank()) "স্ক্রিনে কোনো পাঠযোগ্য টেক্সট পাওয়া যায়নি।" else text
    }

    fun performGlobalAction(actionName: String): String {
        val service = ArohiAccessibilityService.instance
        if (service == null) {
            return "অ্যাক্সেসিবিলিটি সার্ভিস সক্রিয় নেই।"
        }
        val actionId = when (actionName.lowercase().trim()) {
            "back", "পেছনে" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home", "হোম" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents", "রিসেন্ট" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications", "নোটিফিকেশন" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings", "কুইক সেটিংস" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            "lock", "লক" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            } else {
                return "লক স্ক্রিন অ্যাকশন এই Android ভার্সনে সমর্থিত নয়।"
            }
            else -> return "অজানা অ্যাকশন।"
        }
        val success = service.performGlobalAction(actionId)
        return if (success) "অ্যাকশন সফল হয়েছে ($actionName)।" else "অ্যাকশন সম্পন্ন করা যায়নি।"
    }

    fun openFileBrowser(): String {
        return try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "ফাইল ম্যানেজার খোলা হয়েছে।"
        } catch (e: Exception) {
            "ফাইল ব্রাউজার খোলা যায়নি: ${e.message}"
        }
    }
}
