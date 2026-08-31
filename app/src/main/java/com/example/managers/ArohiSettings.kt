package com.example.managers

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

/**
 * Single source of truth for user-configurable Arohi state.
 *
 * Backed by SharedPreferences so settings survive process death and reboot. The
 * platform API is used deliberately instead of DataStore: no extra dependency,
 * no extra coroutines plumbing, and it works unchanged on API 24+.
 *
 * Nothing here is a cache of fake state - every value is something the user set
 * or something actually measured and recorded.
 */
object ArohiSettings {

    private const val PREFS_NAME = "arohi_settings"

    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    private const val KEY_GEMINI_MODEL = "gemini_model"
    private const val KEY_PRIVATE_MODE = "private_mode"
    private const val KEY_SILENT_MODE = "silent_mode"
    private const val KEY_PROACTIVE_ENABLED = "proactive_enabled"
    private const val KEY_USER_MODE = "user_mode"
    private const val KEY_SEND_DATA_TO_GEMINI = "send_data_to_gemini"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_LAST_SUCCESS_MS = "last_gemini_success_ms"
    private const val KEY_LAST_LATENCY_MS = "last_gemini_latency_ms"
    private const val KEY_LAST_ERROR = "last_gemini_error"

    /** Placeholder values that must never be treated as real credentials. */
    private val PLACEHOLDERS = setOf("YOUR_API_KEY", "MY_GEMINI_API_KEY")

    const val DEFAULT_MODEL = "gemini-2.5-flash"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- Gemini --

    fun isPlaceholder(key: String?): Boolean =
        key.isNullOrBlank() || key.trim() in PLACEHOLDERS

    /**
     * The key to actually use: the user's saved key if there is one, otherwise the
     * build-time value. Returns an empty string when neither is usable, so callers
     * can treat blank as "Gemini is not configured" without repeating the
     * placeholder check everywhere.
     */
    fun geminiApiKey(context: Context): String {
        val stored = prefs(context).getString(KEY_GEMINI_API_KEY, null)?.trim().orEmpty()
        if (stored.isNotEmpty() && !isPlaceholder(stored)) return stored
        val compiled: String? = BuildConfig.GEMINI_API_KEY
        return if (!isPlaceholder(compiled)) compiled!!.trim() else ""
    }

    fun hasGeminiKey(context: Context): Boolean = geminiApiKey(context).isNotEmpty()

    fun setGeminiApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_GEMINI_API_KEY, key.trim()).apply()
    }

    fun clearGeminiApiKey(context: Context) {
        prefs(context).edit().remove(KEY_GEMINI_API_KEY).apply()
    }

    fun geminiModel(context: Context): String =
        prefs(context).getString(KEY_GEMINI_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL

    fun setGeminiModel(context: Context, model: String) {
        prefs(context).edit().putString(KEY_GEMINI_MODEL, model.trim()).apply()
    }

    /** Masks a key for display. Never returns enough of it to be usable. */
    fun maskedKey(key: String): String = when {
        key.isBlank() -> ""
        key.length <= 8 -> "*".repeat(key.length)
        else -> key.take(4) + "*".repeat(6) + key.takeLast(4)
    }

    // ------------------------------------------------- measured Gemini status --

    /** Records the outcome of a real connection attempt. */
    fun recordGeminiSuccess(context: Context, latencyMs: Long) {
        prefs(context).edit()
            .putLong(KEY_LAST_SUCCESS_MS, System.currentTimeMillis())
            .putLong(KEY_LAST_LATENCY_MS, latencyMs.coerceAtLeast(0L))
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    fun recordGeminiFailure(context: Context, error: String) {
        prefs(context).edit()
            .putString(KEY_LAST_ERROR, error.take(300))
            .apply()
    }

    fun lastGeminiSuccessMs(context: Context): Long =
        prefs(context).getLong(KEY_LAST_SUCCESS_MS, 0L)

    fun lastGeminiLatencyMs(context: Context): Long =
        prefs(context).getLong(KEY_LAST_LATENCY_MS, -1L)

    fun lastGeminiError(context: Context): String? =
        prefs(context).getString(KEY_LAST_ERROR, null)

    // ------------------------------------------------------------- behaviour --

    fun privateMode(context: Context): Boolean = prefs(context).getBoolean(KEY_PRIVATE_MODE, false)
    fun setPrivateMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PRIVATE_MODE, enabled).apply()
    }

    fun silentMode(context: Context): Boolean = prefs(context).getBoolean(KEY_SILENT_MODE, false)
    fun setSilentMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SILENT_MODE, enabled).apply()
    }

    fun proactiveEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PROACTIVE_ENABLED, false)
    fun setProactiveEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PROACTIVE_ENABLED, enabled).apply()
    }

    fun userMode(context: Context): String = prefs(context).getString(KEY_USER_MODE, "NORMAL") ?: "NORMAL"
    fun setUserMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_USER_MODE, mode).apply()
    }

    fun sendDataToGemini(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SEND_DATA_TO_GEMINI, true)
    fun setSendDataToGemini(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SEND_DATA_TO_GEMINI, enabled).apply()
    }

    fun userName(context: Context): String = prefs(context).getString(KEY_USER_NAME, "") ?: ""
    fun setUserName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_USER_NAME, name.trim()).apply()
    }
}
