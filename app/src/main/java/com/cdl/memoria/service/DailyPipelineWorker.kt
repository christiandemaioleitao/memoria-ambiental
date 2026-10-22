package com.cdl.memoria.service

import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cdl.memoria.api.AnalysisClient
import com.cdl.memoria.api.TranscriptionClient
import com.cdl.memoria.drive.DriveClient
import com.cdl.memoria.util.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Daily job: transcribes each audio block of the day, writes the raw text,
 * asks the LLM for segmentation, writes the treated text, uploads both to Drive.
 */
class DailyPipelineWorker(ctx: Context, params: WorkerParameters) :
    CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = Settings(applicationContext)
        if (!settings.isDriveConnected() || settings.transcriptionApiKey.isBlank()) {
            return@withContext Result.failure()
        }
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "memoria_ambiental/gravações/$date"
        )
        val blocks = dir.listFiles { f -> f.extension == "m4a" }?.sortedBy { it.name }
        if (blocks.isNullOrEmpty()) return@withContext Result.success()

        val transcription = TranscriptionClient(settings)
        val analysis = AnalysisClient(settings)
        val sb = StringBuilder()
        for (block in blocks) {
            try {
                sb.append("## ").append(block.name).append("\n")
                    .append(transcription.transcribe(block)).append("\n\n")
            } catch (e: Exception) {
                sb.append("## ").append(block.name).append(" [erro: ").append(e.message).append("]\n\n")
            }
        }
        val rawText = sb.toString()
        val rawFile = File(dir, "$date-bruta.txt")
        rawFile.writeText(rawText, Charsets.UTF_8)

        val outFile = File(dir, "$date-tratada.txt")
        try {
            val segments = analysis.segment(rawText)
            outFile.writeText(formatTreated(segments), Charsets.UTF_8)
        } catch (e: Exception) {
            outFile.writeText("Erro na segmentação: ${e.message}\n\n$rawText", Charsets.UTF_8)
        }

        try {
            val drive = DriveClient(applicationContext, settings)
            drive.ensureRefreshed()
            val folderId = drive.ensureFolder("Memória Ambiental")
            drive.upload(rawFile, "text/plain", folderId)
            drive.upload(outFile, "text/plain", folderId)
        } catch (e: Exception) {
            return@withContext Result.retry()
        }
        Result.success()
    }

    private fun formatTreated(json: String): String {
        val out = StringBuilder()
        try {
            val arr = if (json.trimStart().startsWith("[")) JSONArray(json)
            else JSONObject(json).optJSONArray("segmentos") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.append("[${o.optString("tipo")} | ${o.optString("inicio")}–${o.optString("fim")}] ")
                    .append(o.optString("tema")).append("\n")
                    .append(o.optString("resumo")).append("\n\n")
            }
        } catch (e: Exception) {
            out.append(json)
        }
        return out.toString()
    }
}
