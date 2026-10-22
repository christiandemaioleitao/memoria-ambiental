package com.cdl.memoria.drive

import android.content.Context
import com.cdl.memoria.util.Settings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Minimal Google Drive REST v3 client (folder create + simple upload). */
class DriveClient(private val context: Context, private val settings: Settings) {

    private val ok = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    /** Refreshes the access token if expired (or tries once when blank). */
    fun ensureRefreshed(): Boolean {
        val valid = settings.driveAccessToken.isNotBlank() &&
            System.currentTimeMillis() < settings.driveTokenExpiry - 60_000
        return valid || OAuthHelper.refresh(settings)
    }

    fun ensureFolder(name: String): String {
        // Search first to avoid duplicates.
        val search = Request.Builder()
            .url(
                "https://www.googleapis.com/drive/v3/files" +
                    "?q=${java.net.URLEncoder.encode(
                        "mimeType='application/vnd.google-apps.folder' and name='$name' and trashed=false",
                        "UTF-8"
                    )}&fields=files(id)&pageSize=1"
            )
            .header("Authorization", "Bearer ${settings.driveAccessToken}")
            .get()
            .build()
        ok.newCall(search).execute().use { resp ->
            if (resp.isSuccessful) {
                val files = JSONObject(resp.body?.string() ?: "{}")
                    .optJSONArray("files")
                if (files != null && files.length() > 0) return files.getJSONObject(0).getString("id")
            }
        }
        val payload = JSONObject()
            .put("name", name)
            .put("mimeType", "application/vnd.google-apps.folder")
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?fields=id")
            .header("Authorization", "Bearer ${settings.driveAccessToken}")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        ok.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            check(resp.isSuccessful) { "Drive folder create HTTP ${resp.code}: ${text.take(300)}" }
            return JSONObject(text).getString("id")
        }
    }

    /** Simple multipart upload of a file into the given folder. */
    fun upload(file: File, mime: String, folderId: String): String {
        val metadata = JSONObject()
            .put("name", file.name)
            .put("parents", org.json.JSONArray().put(folderId))
        val body = MultipartBody.Builder()
            .setType(MultipartBody.MIXED)
            .addPart(
                metadata.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
            )
            .addPart(file.asRequestBody(mime.toMediaType()))
            .build()
        val finalBody = body
        val req = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
            .header("Authorization", "Bearer ${settings.driveAccessToken}")
            .post(finalBody)
            .build()
        ok.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            check(resp.isSuccessful) { "Drive upload HTTP ${resp.code}: ${text.take(300)}" }
            return JSONObject(text).optString("id", "")
        }
    }
}
