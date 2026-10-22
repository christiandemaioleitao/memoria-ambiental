package com.cdl.memoria.api

import com.cdl.memoria.util.Settings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Asks the LLM (Groq or OpenAI chat completions) to segment the daily transcript. */
class AnalysisClient(private val settings: Settings) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private val systemPrompt = """
        Você é um assistente que organiza transcrições de áudio de um dia de trabalho.
        Segmenta a transcrição em trechos temáticos e devolve SOMENTE um array JSON,
        sem texto extra, com objetos: {"tipo": "fato|observação|ocorrência|outro",
        "inicio": "HH:MM ou null", "fim": "HH:MM ou null", "tema": "título curto",
        "resumo": "1-3 frases"}. Responda em português.
    """.trimIndent()

    /** Returns the raw JSON string (array of segments). */
    fun segment(transcript: String): String {
        val provider = settings.analysisProvider
        val key = settings.analysisApiKey
        val (url, model) = if (provider == "groq") {
            "https://api.groq.com/openai/v1/chat/completions" to "llama-3.3-70b-versatile"
        } else {
            "https://api.openai.com/v1/chat/completions" to "gpt-4o-mini"
        }
        val payload = JSONObject().apply {
            put("model", model)
            put("temperature", 0.2)
            put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(JSONObject().put("role", "user").put(
                    "content",
                    "Transcrição do dia:\n\n" + transcript.take(48_000)
                )))
            put("response_format", JSONObject().put("type", "json_object"))
        }
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $key")
            .post(
                payload.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
            )
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw IllegalStateException("Analysis HTTP ${resp.code}: ${text.take(300)}")
            }
            val content = JSONObject(text)
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
            return content.trim().removePrefix("```json").removePrefix("```").trim()
                .removeSuffix("```").trim()
        }
    }
}
