package com.openconverter.app.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.openconverter.app.R
import com.openconverter.app.decoders.DefaultDecoders
import com.openconverter.app.engine.AndroidFileSystemPort
import com.openconverter.app.engine.ConversionEngine
import com.openconverter.app.engine.ConversionRequest
import com.openconverter.app.engine.FileResult
import com.openconverter.app.engine.HistoryPort
import com.openconverter.app.engine.HistoryRecord
import com.openconverter.app.engine.HistoryStatus
import com.openconverter.app.engine.JsonHistoryStore
import com.openconverter.app.engine.ProgressEvent
import com.openconverter.app.engine.RealClock
import com.openconverter.app.saf.SafAdapter
import android.net.Uri
import com.openconverter.app.ffmpeg.FfmpegKitRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Foreground Service that runs a [ConversionEngine] off the main thread, posts
 * a sticky notification with progress + cancel, and stops itself when the batch
 * completes (or is cancelled).
 *
 * Lifecycle:
 *   1. UI calls [start] → ContextCompat.startForegroundService.
 *   2. onStartCommand parses extras, builds request, calls startForeground
 *      (within 5 s — required by Android), launches the engine on Dispatchers.IO.
 *   3. Each progress event updates the notification AND a SharedFlow the UI
 *      observes via the binder.
 *   4. Batch finishes / cancels → stopForeground(REMOVE) + stopSelf().
 *
 * FGS type is `specialUse` because compileSdk 34 lacks `mediaProcessing`.
 * See android-sdk34-fgs-workaround.md.
 */
class ConversionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null

    private val _progress = MutableSharedFlow<ProgressEvent>(replay = 32, extraBufferCapacity = 64)
    val progress: SharedFlow<ProgressEvent> get() = _progress.asSharedFlow()

    private val binder = LocalBinder(this)

    inner class LocalBinder(val service: ConversionService) : android.os.Binder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            currentJob?.cancel()
            return START_NOT_STICKY
        }

        // Parse extras → request
        val inputs = intent?.getStringArrayListExtra(EXTRA_INPUTS) ?: emptyList()
        val names = intent?.getStringArrayListExtra(EXTRA_NAMES) ?: emptyList()
        val folder = intent?.getStringExtra(EXTRA_FOLDER).orEmpty()
        val target = intent?.getStringExtra(EXTRA_TARGET) ?: "mp3"
        val bitrate = intent?.getStringExtra(EXTRA_BITRATE) // null = lossless / codec default

        val req = ConversionRequest(
            inputUris = inputs.toList(),
            inputDisplayNames = names.toList(),
            targetFormat = target,
            outputFolderUri = folder,
            bitrate = bitrate,
            plainInputExts = PLAIN_INPUT_EXTS,
        )

        // Promote to foreground BEFORE starting work (5-s rule).
        startForegroundCompat(buildNotification(0, inputs.size, "Starting…", null))

        val fs = AndroidFileSystemPort(applicationContext).also { it.clearStaleCache() }
        val ffmpeg = FfmpegKitRunner()
        val sink = ServiceProgressSink(this, _progress, inputs.size)
        val engine = ConversionEngine(DefaultDecoders.registry, ffmpeg, fs, sink, RealClock())

        val history: HistoryPort = JsonHistoryStore(applicationContext.filesDir)
        currentJob = scope.launch {
            try {
                val results: List<FileResult> = engine.convertAll(req)
                results.forEachIndexed { i, r ->
                    history.append(toRecord(i, r, names, target))
                }
                sink.onBatchDone()
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        currentJob?.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(getString(R.string.notif_channel_name))
            .setDescription(getString(R.string.notif_channel_desc))
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private fun startForegroundCompat(notif: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    /**
     * Build the per-progress notification. Called from [ServiceProgressSink].
     */
    fun buildNotification(index: Int, total: Int, currentName: String, percent: Int?): android.app.Notification {
        val title = if (total > 0) "${getString(R.string.notif_title)}  ${index + 1}/$total" else getString(R.string.notif_title)
        val cancelIntent = Intent(this, ConversionService::class.java).setAction(ACTION_CANCEL)
        val cancelPi = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(currentName)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.notif_cancel), cancelPi)
        if (percent != null) builder.setProgress(100, percent, percent < 0)
        return builder.build()
    }

    fun postNotification(notif: android.app.Notification) {
        NotificationManagerCompat.from(this).notify(NOTIF_ID, notif)
    }

    private fun toRecord(
        i: Int, r: FileResult,
        names: List<String>, target: String,
    ): HistoryRecord {
        val status = if (r.error == null) HistoryStatus.SUCCESS else HistoryStatus.FAILED
        val outputName = r.outputPath?.let { p ->
            runCatching { SafAdapter.queryDisplayName(applicationContext, Uri.parse(p)) }.getOrNull()
        }
        return HistoryRecord(
            ts = System.currentTimeMillis(),
            inputName = names.getOrNull(i) ?: "unknown",
            targetFormat = target,
            status = status,
            outputName = outputName,
            durationMs = null,
            error = r.error,
        )
    }

    companion object {
        const val CHANNEL_ID = "oc_convert"
        const val NOTIF_ID = 1
        const val ACTION_CANCEL = "com.openconverter.app.CANCEL"
        const val EXTRA_INPUTS = "inputs"
        const val EXTRA_NAMES = "names"
        const val EXTRA_FOLDER = "folder"
        const val EXTRA_TARGET = "target"
        const val EXTRA_BITRATE = "bitrate"

        private val PLAIN_INPUT_EXTS = setOf(
            ".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg", ".opus",
        )

        fun makeIntent(
            ctx: Context,
            inputs: List<String>,
            names: List<String>,
            folder: String,
            target: String,
            bitrate: String?,
        ): Intent = Intent(ctx, ConversionService::class.java).apply {
            putStringArrayListExtra(EXTRA_INPUTS, ArrayList(inputs))
            putStringArrayListExtra(EXTRA_NAMES, ArrayList(names))
            putExtra(EXTRA_FOLDER, folder)
            putExtra(EXTRA_TARGET, target)
            putExtra(EXTRA_BITRATE, bitrate)
        }
    }
}
