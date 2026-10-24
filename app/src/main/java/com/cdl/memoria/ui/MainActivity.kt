package com.cdl.memoria.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.cdl.memoria.drive.OAuthHelper
import com.cdl.memoria.service.DailyPipelineWorker
import com.cdl.memoria.service.RecordingService
import com.cdl.memoria.util.Settings
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Configuration screen built programmatically (no XML layouts). */
class MainActivity : AppCompatActivity() {

    private lateinit var settings: Settings
    private lateinit var timeStart: EditText
    private lateinit var timeEnd: EditText
    private lateinit var spTranscription: Spinner
    private lateinit var spAnalysis: Spinner
    private lateinit var etTranscriptionKey: EditText
    private lateinit var etAnalysisKey: EditText
    private lateinit var etClientId: EditText
    private lateinit var swRecord: Switch
    private lateinit var btnConnect: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        timeStart = field(root, "Início da janela")
        timeEnd = field(root, "Fim da janela")
        timeStart.setOnClickListener { pickTime(true) }
        timeEnd.setOnClickListener { pickTime(false) }

        spTranscription = spinner(root, "Provedor de transcrição", listOf("groq", "openai"))
        spAnalysis = spinner(root, "Provedor de análise", listOf("groq", "openai"))
        etTranscriptionKey = field(root, "Chave da API de transcrição")
        etAnalysisKey = field(root, "Chave da API de análise")
        etClientId = field(root, "Google OAuth Client ID")

        btnConnect = Button(this).apply {
            text = "Conectar Google Drive"
            root.addView(this)
            setOnClickListener { startOAuth() }
        }

        swRecord = Switch(this).apply {
            text = "Gravação ativa"
            root.addView(this)
            setPadding(0, dp(24), 0, 0)
        }
        setContentView(root)
        loadSettings()

        swRecord.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (!hasMicPermission()) {
                    swRecord.isChecked = false
                    requestMicPermission()
                } else {
                    RecordingService.start(this)
                }
            } else {
                RecordingService.stop(this)
            }
        }
        scheduleDailyPipeline()
    }

    private fun scheduleDailyPipeline() {
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_pipeline",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyPipelineWorker>(24, TimeUnit.HOURS).build()
        )
    }

    private fun loadSettings() {
        timeStart.setText(fmt(settings.windowStartMinutes))
        timeEnd.setText(fmt(settings.windowEndMinutes))
        spTranscription.setSelection(if (settings.transcriptionProvider == "openai") 1 else 0)
        spAnalysis.setSelection(if (settings.analysisProvider == "openai") 1 else 0)
        etTranscriptionKey.setText(settings.transcriptionApiKey)
        etAnalysisKey.setText(settings.analysisApiKey)
        etClientId.setText(settings.googleClientId)
        swRecord.isChecked = false
    }

    private fun pickTime(isStart: Boolean) {
        val current = if (isStart) settings.windowStartMinutes else settings.windowEndMinutes
        TimePickerDialog(this, { _, h, m ->
            val minutes = h * 60 + m
            if (isStart) settings.windowStartMinutes = minutes else settings.windowEndMinutes = minutes
            (if (isStart) timeStart else timeEnd).setText(fmt(minutes))
        }, current / 60, current % 60, true).show()
    }

    private fun startOAuth() {
        saveSettings()
        if (settings.googleClientId.isBlank()) {
            Toast.makeText(this, "Informe o Client ID do Google", Toast.LENGTH_SHORT).show()
            return
        }
        val verifier = OAuthHelper.generateCodeVerifier()
        settings.codeVerifier = verifier
        val url = OAuthHelper.buildAuthUrl(
            settings.googleClientId, OAuthHelper.codeChallenge(verifier)
        )
        try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "Navegador indisponível", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSettings() {
        settings.transcriptionProvider = spTranscription.selectedItem?.toString() ?: "groq"
        settings.transcriptionApiKey = etTranscriptionKey.text.toString().trim()
        settings.analysisProvider = spAnalysis.selectedItem?.toString() ?: "groq"
        settings.analysisApiKey = etAnalysisKey.text.toString().trim()
        settings.googleClientId = etClientId.text.toString().trim()
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestMicPermission() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 100)
    }

    private fun fmt(minutes: Int): String =
        String.format("%02d:%02d", minutes / 60, minutes % 60)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun field(root: LinearLayout, hint: String): EditText {
        val label = TextView(this).apply {
            text = hint
            setPadding(0, dp(16), 0, dp(4))
        }
        val edit = EditText(this).apply {
            this.hint = hint
            setSingleLine(true)
        }
        root.addView(label)
        root.addView(edit)
        return edit
    }

    private fun spinner(root: LinearLayout, label: String, items: List<String>): Spinner {
        val tv = TextView(this).apply {
            text = label
            setPadding(0, dp(16), 0, dp(4))
        }
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, items
        )
        root.addView(tv)
        root.addView(sp)
        return sp
    }
}
