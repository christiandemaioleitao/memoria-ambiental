package com.cdl.memoria.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import com.cdl.memoria.ui.MainActivity
import com.cdl.memoria.util.Settings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Foreground service that records microphone audio in fixed-length blocks
 * (default 5 minutes) into /gravações/<yyyy-MM-dd>/bloco_XXX.m4a.
 * Stops itself when the configured recording window ends.
 */
class RecordingService : Service() {

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var blockIndex = 0
    private var windowEndMillis = 0L
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var settings: Settings

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRecording()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!hasMicPermission()) {
            Log.e(TAG, "RECORD_AUDIO not granted; stopping service")
            stopSelf()
            return START_NOT_STICKY
        }
        createChannelAndStartForeground()
        // Window end: today's configured end time (if already past, record until end of day).
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, settings.windowEndMinutes / 60)
        cal.set(Calendar.MINUTE, settings.windowEndMinutes % 60)
        cal.set(Calendar.SECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        windowEndMillis = cal.timeInMillis
        startBlock()
        return START_STICKY
    }

    private fun hasMicPermission(): Boolean =
        checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun dayDir(): File {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "memoria_ambiental/gravações/$date"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun startBlock() {
        if (System.currentTimeMillis() >= windowEndMillis) {
            stopRecording(); stopSelf(); return
        }
        val file = File(dayDir(), String.format(Locale.US, "bloco_%03d.m4a", blockIndex))
        currentFile = file
        val s = settings
        recorder = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else
            @Suppress("DEPRECATION") MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setMaxDuration(s.blockMinutes * 60 * 1000)
            setOutputFile(file.absolutePath)
            setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    blockIndex++
                    startBlock()
                }
            }
            try {
                prepare()
                start()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start block recording", e)
                handler.postDelayed({ if (recorder != null) startBlock() }, 5_000)
            }
        }
        // Safety net: also rotate slightly after block length in case listener misses.
        handler.postDelayed({
            if (recorder != null && currentFile == file && System.currentTimeMillis() < windowEndMillis) {
                blockIndex++
                startBlock()
            }
        }, (s.blockMinutes * 60_000 + 2_000).toLong())
    }

    private fun stopRecording() {
        handler.removeCallbacksAndMessages(null)
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        recorder?.release()
        recorder = null
    }

    private fun createChannelAndStartForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Gravação", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Memória Ambiental — gravando")
                .setContentText("Captura de áudio em blocos em andamento")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pi)
                .addAction(Notification.Action.Builder(null, "Parar", stopPi).build())
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Memória Ambiental — gravando")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build()
        }
        startForeground(NOTIF_ID, n)
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "RecordingService"
        private const val CHANNEL_ID = "recording"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "com.cdl.memoria.action.STOP_RECORDING"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, RecordingService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, RecordingService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
