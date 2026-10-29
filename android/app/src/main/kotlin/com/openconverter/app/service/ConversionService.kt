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
import com.openconverter.app.OpenConverterApp
import com.openconverter.app.decoders.DefaultDecoders
import com.openconverter.app.engine.AndroidFileSystemPort
import com.openconverter.app.engine.ConversionEngine
import com.openconverter.app.engine.ConversionRequest
import com.openconverter.app.engine.FileResult
import com.openconverter.app.engine.FileCancellationException
import com.openconverter.app.engine.HistoryPort
import com.openconverter.app.engine.HistoryRecord
import com.openconverter.app.engine.HistoryStatus
import com.openconverter.app.engine.JsonHistoryStore
import com.openconverter.app.engine.BatchProgress
import com.openconverter.app.engine.RealClock
import com.openconverter.app.saf.SafAdapter
import android.net.Uri
import android.os.Environment
import com.openconverter.app.ffmpeg.FfmpegKitRunner
import com.openconverter.app.engine.NeteaseLyricResolver
import com.openconverter.app.engine.PublicStorageLyricScanner
import com.openconverter.app.meta.NeteaseLyricHttp
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var currentJob: Job? = null
    private var activeFiles: List<Deferred<FileResult>> = emptyList()
    private var latestStartId: Int = 0
    private val taskLock = Any()

    fun cancelBatch() {
        synchronized(taskLock) { currentJob?.cancel() }
    }

    fun cancelFile(index: Int): Boolean = synchronized(taskLock) {
        val task = activeFiles.getOrNull(index) ?: return@synchronized false
        if (task.isCompleted) return@synchronized false
        task.cancel(FileCancellationException())
        true
    }

    private val _progress = MutableStateFlow<BatchProgress?>(null)
    val progress: StateFlow<BatchProgress?> = _progress.asStateFlow()

    private val binder = LocalBinder(this)

    inner class LocalBinder(val service: ConversionService) : android.os.Binder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent?.action == ACTION_CANCEL) {
            startForegroundCompat(buildNotification(0, 0, "Cancelling…", null))
            val running = synchronized(taskLock) { currentJob?.isCompleted == false }
            cancelBatch()
            if (!running) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }

        // Parse extras → request
        val inputs = intent?.getStringArrayListExtra(EXTRA_INPUTS) ?: emptyList()
        val names = intent?.getStringArrayListExtra(EXTRA_NAMES) ?: emptyList()
        val folder = intent?.getStringExtra(EXTRA_FOLDER).orEmpty()
        val target = intent?.getStringExtra(EXTRA_TARGET) ?: "mp3"
        val bitrate = intent?.getStringExtra(EXTRA_BITRATE) // null = lossless / codec default
        val batchId = requireNotNull(intent?.getStringExtra(EXTRA_BATCH_ID))

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

        val previous = synchronized(taskLock) { currentJob }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val owner = currentCoroutineContext()[Job]
            val sink = ServiceProgressSink(this@ConversionService, _progress, inputs.size, batchId)
            var cancelled = false
            try {
                previous?.cancelAndJoin()
                currentCoroutineContext().ensureActive()
                _progress.value = BatchProgress(batchId)
                withContext(Dispatchers.IO) {
                val fs = AndroidFileSystemPort(applicationContext).also { it.clearStaleCache() }
                val ffmpeg = FfmpegKitRunner()
                val kggKeys = (application as OpenConverterApp).kggKeyStore
                val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
                val lyricsEnabled = prefs.getBoolean("ncm_lyrics_enabled", true)
                val extraRoots = mutableListOf<File>()
                if (lyricsEnabled) {
                    val standardDirs = listOf(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                        File("/sdcard/Download"),
                        File("/sdcard/Music"),
                        File("/sdcard/下载"),
                    )
                    extraRoots += PublicStorageLyricScanner.scanRoots(standardDirs)
                    val privateFiles = File("/data/data/com.netease.cloudmusic/files")
                    if (privateFiles.canRead()) extraRoots += privateFiles
                }
                val lyricResolver = NeteaseLyricResolver(
                    enabled = lyricsEnabled,
                    extraRoots = extraRoots,
                    fetchJson = { NeteaseLyricHttp.fetchJson(it) },
                    open = { root, rel -> File(root, rel).takeIf { it.isFile }?.readBytes() },
                )
                val engine = ConversionEngine(
                    DefaultDecoders.registry(kggKeys), ffmpeg, fs, sink, RealClock(),
                    lyricResolver = lyricResolver,
                    syncMissingKggKeys = {
                        val keys = com.openconverter.app.decoders.kgg.KugouKeySyncManager.syncKeys()
                        kggKeys.mergeKeys(keys)
                        true
                    },
                )
                val history: HistoryPort = JsonHistoryStore(applicationContext.filesDir)
                val results = engine.convertAll(
                    req,
                    onTasksReady = { tasks -> synchronized(taskLock) { if (currentJob === owner) activeFiles = tasks } },
                    onFileCancelled = { sink.onFileCancelled(it) },
                )
                results.filterNot { it.cancelled }.forEach { r ->
                    history.append(toRecord(r.index, r, names, target))
                }
                }
            } catch (ce: CancellationException) {
                cancelled = true
                throw ce
            } catch (error: Exception) {
                for (i in inputs.indices) sink.onFileError(i, error.message ?: error.javaClass.simpleName)
            } finally {
                withContext(NonCancellable) { previous?.join() }
                val ownsService = synchronized(taskLock) {
                    if (currentJob === owner) {
                        currentJob = null
                        activeFiles = emptyList()
                        true
                    } else false
                }
                if (ownsService) {
                    if (_progress.value?.batchId != batchId) _progress.value = BatchProgress(batchId)
                    sink.onBatchDone(cancelled)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(latestStartId)
                }
            }
        }
        synchronized(taskLock) {
            currentJob = job
            activeFiles = emptyList()
        }
        job.start()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
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
        const val EXTRA_BATCH_ID = "batch_id"

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
            batchId: String,
        ): Intent = Intent(ctx, ConversionService::class.java).apply {
            putStringArrayListExtra(EXTRA_INPUTS, ArrayList(inputs))
            putStringArrayListExtra(EXTRA_NAMES, ArrayList(names))
            putExtra(EXTRA_FOLDER, folder)
            putExtra(EXTRA_TARGET, target)
            putExtra(EXTRA_BITRATE, bitrate)
            putExtra(EXTRA_BATCH_ID, batchId)
        }
    }
}
