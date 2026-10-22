package com.cdl.memoria.drive

import android.net.Uri
import com.cdl.memoria.util.Settings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64

/** PKCE OAuth2 flow for Google Drive (drive.file scope). */
object OAuthHelper {

    const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    const val REDIRECT_URI = "memoria://oauth"
    private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    private val ok = OkHttpClient()

    /** The verifier must be kept until token exchange; persisted in Settings. */
    fun Settings.setCodeVerifier(v: String) {
        codeVerifier = v
    }

    fun Settings.getCodeVerifier(): String = codeVerifier

    fun buildAuthUrl(clientId: String, codeChallenge: String): String =
        "$AUTH_URL?client_id=$clientId&response_type=code&scope=$SCOPE" +
            "&redirect_uri=${Uri.encode(REDIRECT_URI)}&access_type=offline" +
            "&prompt=consent&code_challenge=$codeChallenge&code_challenge_method=S256"

    fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun codeChallenge(verifier: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /** Exchanges an auth code for tokens and stores them. Returns true on success. */
    fun exchangeCode(
        context: android.content.Context,
        settings: Settings,
        code: String
    ): Boolean {
        val body = "code=$code&client_id=${Uri.encode(settings.googleClientId)}" +
            "&redirect_uri=${Uri.encode(REDIRECT_URI)}&grant_type=authorization_code" +
            "&code_verifier=${Uri.encode(settings.getCodeVerifier())}"
        val req = Request.Builder()
            .url(TOKEN_URL)
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()
        ok.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return false
            val json = JSONObject(text)
            settings.driveAccessToken = json.optString("access_token")
            val refresh = json.optString("refresh_token", "")
            if (refresh.isNotBlank()) settings.driveRefreshToken = refresh
            settings.driveTokenExpiry =
                System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000
            return true
        }
    }

    /** Refreshes the access token when expired. */
    fun refresh(settings: Settings): Boolean {
        val refresh = settings.driveRefreshToken
        if (refresh.isBlank()) return false
        val body = "refresh_token=$refresh" +
            "&client_id=${Uri.encode(settings.googleClientId)}&grant_type=refresh_token"
        val req = Request.Builder()
            .url(TOKEN_URL)
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()
        ok.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return false
            val json = JSONObject(text)
            settings.driveAccessToken = json.optString("access_token")
            settings.driveTokenExpiry =
                System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000
            return true
        }
    }
}
