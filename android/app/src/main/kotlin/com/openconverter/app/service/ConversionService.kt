package com.openconverter.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.DocumentsContract
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.openconverter.app.ekey.EkeyStore
import com.openconverter.app.engine.Clock
import com.openconverter.app.engine.ContentResolverFacade
import com.openconverter.app.engine.ConversionEngine
import com.openconverter.app.engine.EkeyProvider
import com.openconverter.app.engine.FailureSink
import com.openconverter.app.engine.HistorySink
import com.openconverter.app.engine.Orchestrator
import com.openconverter.app.failures.FailureLog
import com.openconverter.app.ffmpeg.FfmpegBridge
import com.openconverter.app.log.OCLog
import com.openconverter.app.ui.history.HistoryEntry
import com.openconverter.app.ui.history.HistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConversionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ekeyStore by lazy { EkeyStore(applicationContext) }
    private val failureLog by lazy { FailureLog(applicationContext) }
    private val orchestrator by lazy { ConversionOrchestrator(FfmpegBridge) }

    private val _progress = MutableStateFlow<Progress>(Progress.Idle)
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uris = intent?.getStringArrayExtra(EXTRA_URIS)?.map(Uri::parse).orEmpty()
        val targetFormat = intent?.getStringExtra(EXTRA_TARGET_FORMAT) ?: "mp3"
        val folderUri = intent?.getStringExtra(EXTRA_FOLDER_URI)?.let(Uri::parse)
        OCLog.i("svc.onStart",
            "n" to uris.size, "fmt" to targetFormat,
            "folder" to (folderUri?.lastPathSegment ?: "null"))

        startForegroundCompat(
            ProgressNotification.build(this, "转换中…", 0, uris.size)
        )

        scope.launch {
            val serviceContext = this@ConversionService
            val ekeyValue = ekeyStore.getEkey()

            val engine = ConversionEngine(
                resolver = ServiceContentResolverFacade(serviceContext),
                orchestrator = Orchestrator { input, fileName, ek, fmt, br ->
                    orchestrator.convertOneInMemory(input, fileName, ek, fmt, br)
                },
                ekey = object : EkeyProvider { override fun getEkey(): String? = ekeyValue },
                failureSink = object : FailureSink {
                    override fun record(uri: String, filename: String, error: String) =
                        failureLog.record(uri, filename, error)
                },
                historySink = HistoryRepositorySink,
                clock = Clock.wallClock,
            )

            val result = engine.convertAll(
                uris = uris,
                targetFormat = targetFormat,
                folderUri = folderUri,
                progressSink = _progress,
            )

            val title = if (result.fail == 0) {
                "全部完成 (${result.ok})"
            } else {
                "完成 ${result.ok}/${result.total}（${result.fail} 失败）"
            }
            val finalNotification = ProgressNotification.build(serviceContext, title, result.total, result.total)
            NotificationManagerCompat.from(serviceContext)
                .notify(ProgressNotification.NOTIFICATION_ID, finalNotification)

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_NOT_STICKY  // process killed → no auto-retry
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {  // API 34
            // FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING = 0x00200000 (Android 14).
            // ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING was added at
            // API 34; the constant isn't visible to the compiler in this SDK
            // (compileSdk 34 manifest schema does not yet recognize the
            // "mediaProcessing" string attribute), so use the documented int
            // value. Manifest currently declares dataSync for compatibility with
            // compileSdk 34; the runtime type is what StrictMode actually checks
            // at startForeground time.
            startForeground(
                ProgressNotification.NOTIFICATION_ID,
                notification,
                0x00200000
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {  // API 29
            startForeground(
                ProgressNotification.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(ProgressNotification.NOTIFICATION_ID, notification)
        }
    }

    private fun queryDisplayNameInternal(uri: Uri): String? = try {
        contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && !c.isNull(idx)) c.getString(idx) else null
            } else null
        }
    } catch (e: Exception) {
        null
    }

    /** Adapter from [ConversionEngine]'s facade to the live ContentResolver. */
    private class ServiceContentResolverFacade(
        private val service: ConversionService,
    ) : ContentResolverFacade {
        override fun read(uri: Uri): ByteArray? = runCatching {
            service.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()

        override fun write(uri: Uri, bytes: ByteArray): Boolean = runCatching {
            service.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: return@runCatching false
            true
        }.getOrDefault(false)

        override fun createDocument(folderUri: Uri, name: String, mime: String): Uri? =
            DocumentsContract.createDocument(service.contentResolver, folderUri, mime, name)

        override fun queryDisplayName(uri: Uri): String? = service.queryDisplayNameInternal(uri)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    sealed class Progress {
        object Idle : Progress()
        data class Processing(val index: Int, val total: Int, val current: String) : Progress()
        data class Done(val completed: Int, val total: Int, val last: String) : Progress()
    }

    companion object {
        const val EXTRA_URIS = "uris"
        const val EXTRA_TARGET_FORMAT = "targetFormat"
        const val EXTRA_FOLDER_URI = "folderUri"

        fun start(
            context: Context,
            uris: List<Uri>,
            targetFormat: String,
            folderUri: Uri,
        ) {
            val intent = Intent(context, ConversionService::class.java).apply {
                putExtra(EXTRA_URIS, uris.map { it.toString() }.toTypedArray())
                putExtra(EXTRA_TARGET_FORMAT, targetFormat)
                putExtra(EXTRA_FOLDER_URI, folderUri.toString())
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

/** File-scope adapter so [ConversionEngine] can call HistoryRepository (an `object`). */
private object HistoryRepositorySink : HistorySink {
    override fun record(entry: HistoryEntry) {
        HistoryRepository.record(entry)
    }
}
