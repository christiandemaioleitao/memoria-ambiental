package com.cdl.memoria.util

import android.content.Context
import android.content.SharedPreferences

/** SharedPreferences wrapper for all app configuration. */
class Settings(ctx: Context) {
    private val prefs: SharedPreferences =
        ctx.getSharedPreferences("memoria_settings", Context.MODE_PRIVATE)

    var windowStartMinutes: Int
        get() = prefs.getInt(KEY_START, 8 * 60)          // 08:00
        set(v) = prefs.edit().putInt(KEY_START, v).apply()

    var windowEndMinutes: Int
        get() = prefs.getInt(KEY_END, 20 * 60)           // 20:00
        set(v) = prefs.edit().putInt(KEY_END, v).apply()

    var blockMinutes: Int
        get() = prefs.getInt(KEY_BLOCK, 5)
        set(v) = prefs.edit().putInt(KEY_BLOCK, v).apply()

    var transcriptionProvider: String
        get() = prefs.getString(KEY_TRANSCRIPTION_PROVIDER, "groq")!!
        set(v) = prefs.edit().putString(KEY_TRANSCRIPTION_PROVIDER, v).apply()

    var transcriptionApiKey: String
        get() = prefs.getString(KEY_TRANSCRIPTION_KEY, "")!!
        set(v) = prefs.edit().putString(KEY_TRANSCRIPTION_KEY, v).apply()

    var analysisProvider: String
        get() = prefs.getString(KEY_ANALYSIS_PROVIDER, "groq")!!
        set(v) = prefs.edit().putString(KEY_ANALYSIS_PROVIDER, v).apply()

    var analysisApiKey: String
        get() = prefs.getString(KEY_ANALYSIS_KEY, "")!!
        set(v) = prefs.edit().putString(KEY_ANALYSIS_KEY, v).apply()

    var googleClientId: String
        get() = prefs.getString(KEY_GOOGLE_CLIENT_ID, "")!!
        set(v) = prefs.edit().putString(KEY_GOOGLE_CLIENT_ID, v).apply()

    var driveAccessToken: String
        get() = prefs.getString(KEY_DRIVE_ACCESS, "")!!
        set(v) = prefs.edit().putString(KEY_DRIVE_ACCESS, v).apply()

    var driveRefreshToken: String
        get() = prefs.getString(KEY_DRIVE_REFRESH, "")!!
        set(v) = prefs.edit().putString(KEY_DRIVE_REFRESH, v).apply()

    var driveTokenExpiry: Long
        get() = prefs.getLong(KEY_DRIVE_EXPIRY, 0L)
        set(v) = prefs.edit().putLong(KEY_DRIVE_EXPIRY, v).apply()

    var codeVerifier: String
        get() = prefs.getString(KEY_PKCE_VERIFIER, "")!!
        set(v) = prefs.edit().putString(KEY_PKCE_VERIFIER, v).apply()

    fun isDriveConnected(): Boolean = driveRefreshToken.isNotBlank()

    private companion object {
        const val KEY_START = "window_start"
        const val KEY_END = "window_end"
        const val KEY_BLOCK = "block_minutes"
        const val KEY_TRANSCRIPTION_PROVIDER = "transcription_provider"
        const val KEY_TRANSCRIPTION_KEY = "transcription_api_key"
        const val KEY_ANALYSIS_PROVIDER = "analysis_provider"
        const val KEY_ANALYSIS_KEY = "analysis_api_key"
        const val KEY_GOOGLE_CLIENT_ID = "google_client_id"
        const val KEY_DRIVE_ACCESS = "drive_access_token"
        const val KEY_DRIVE_REFRESH = "drive_refresh_token"
        const val KEY_DRIVE_EXPIRY = "drive_token_expiry"
        const val KEY_PKCE_VERIFIER = "pkce_verifier"
    }
}
