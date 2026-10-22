package com.cdl.memoria.api

import com.cdl.memoria.util.Settings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Transcribes audio blocks via Groq or OpenAI speech-to-text (whisper). */
class TranscriptionClient(private val settings: Settings) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun transcribe(file: File): String {
        val provider = settings.transcriptionProvider
        val key = settings.transcriptionApiKey
        val (url, model) = if (provider == "groq") {
            "https://api.groq.com/openai/v1/audio/transcriptions" to "whisper-large-v3-turbo"
        } else {
            "https://api.openai.com/v1/audio/transcriptions" to "whisper-1"
        }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", file.name,
                file.asRequestBody("audio/mp4".toMediaType())
            )
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "json")
            .addFormDataPart("language", "pt")
            .build()
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $key")
            .post(body)
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw IllegalStateException("Transcription HTTP ${resp.code}: ${text.take(300)}")
            }
            return JSONObject(text).optString("text", "")
        }
    }
}
