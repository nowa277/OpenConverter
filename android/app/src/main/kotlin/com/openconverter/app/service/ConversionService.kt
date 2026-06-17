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
import com.openconverter.app.failures.FailureLog
import com.openconverter.app.ffmpeg.FfmpegBridge
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
        val baseName = intent?.getStringExtra(EXTRA_BASE_NAME).orEmpty()

        startForegroundCompat(
            ProgressNotification.build(this, "转换中…", 0, uris.size)
        )

        scope.launch {
            val serviceContext = this@ConversionService
            val ekey = ekeyStore.getEkey()
            val failures = mutableListOf<Pair<Uri, String>>()
            val successes = mutableListOf<Uri>()

            uris.forEachIndexed { i, uri ->
                val filename = uri.lastPathSegment ?: "file_$i"
                _progress.value = Progress.Processing(i, uris.size, filename)

                val input = runCatching {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
                if (input == null) {
                    failures.add(uri to "读取失败")
                    failureLog.record(uri.toString(), filename, "读取失败")
                    return@forEachIndexed
                }

                val displayName = queryDisplayName(uri)
                val result = runCatching {
                    orchestrator.convertOneInMemory(
                        input = input,
                        fileName = displayName,
                        ekey = ekey,
                        targetFormat = targetFormat,
                        bitrateKbps = 256,
                    )
                }
                if (result.isFailure) {
                    val err = result.exceptionOrNull()?.message ?: "未知错误"
                    failures.add(uri to err)
                    failureLog.record(uri.toString(), filename, err)
                    return@forEachIndexed
                }

                // Single-file: honor the user-edited baseName as the output stem.
                // Multi-file: keep each source's own stem to avoid collisions.
                val outName = if (uris.size == 1 && baseName.isNotBlank()) {
                    "$baseName.$targetFormat"
                } else {
                    val sourceStem = displayName?.substringBeforeLast('.', "output_$i") ?: "output_$i"
                    "$sourceStem.$targetFormat"
                }
                val outUri = if (folderUri != null) {
                    createDocumentInFolder(folderUri, outName, targetFormat)
                } else {
                    null
                }
                if (outUri == null) {
                    failures.add(uri to "无法创建输出文件")
                    failureLog.record(uri.toString(), filename, "无法创建输出文件")
                    return@forEachIndexed
                }

                val writeResult = runCatching {
                    contentResolver.openOutputStream(outUri)?.use {
                        it.write(result.getOrThrow().encoded)
                    }
                }
                if (writeResult.isFailure) {
                    val err = "保存失败: ${writeResult.exceptionOrNull()?.message}"
                    failures.add(uri to err)
                    failureLog.record(uri.toString(), filename, err)
                    return@forEachIndexed
                }

                successes.add(uri)
                _progress.value = Progress.Done(i + 1, uris.size, filename)
            }

            val title = if (failures.isEmpty()) {
                "全部完成 (${successes.size})"
            } else {
                "完成 ${successes.size}/${uris.size}（${failures.size} 失败）"
            }
            val finalNotification = ProgressNotification.build(serviceContext, title, uris.size, uris.size)
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
            // API 34; the constant isn't visible to the compiler in this SDK,
            // so use the documented int value.
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

    /**
     * Create a new document inside the user-picked folder tree using
     * DocumentsContract.createDocument, then return its URI for writing.
     */
    private fun createDocumentInFolder(
        folderUri: Uri,
        fileName: String,
        targetFormat: String,
    ): Uri? {
        val mime = mimeForFormat(targetFormat)
        val docUri = DocumentsContract.createDocument(
            contentResolver,
            folderUri,
            mime,
            fileName,
        ) ?: return null
        return docUri
    }

    private fun mimeForFormat(format: String): String = when (format) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        else -> "application/octet-stream"
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

    private fun queryDisplayName(uri: Uri): String? = try {
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

    companion object {
        const val EXTRA_URIS = "uris"
        const val EXTRA_TARGET_FORMAT = "targetFormat"
        const val EXTRA_FOLDER_URI = "folderUri"
        const val EXTRA_BASE_NAME = "baseName"

        fun start(
            context: Context,
            uris: List<Uri>,
            targetFormat: String,
            folderUri: Uri,
            baseName: String,
        ) {
            val intent = Intent(context, ConversionService::class.java).apply {
                putExtra(EXTRA_URIS, uris.map { it.toString() }.toTypedArray())
                putExtra(EXTRA_TARGET_FORMAT, targetFormat)
                putExtra(EXTRA_FOLDER_URI, folderUri.toString())
                putExtra(EXTRA_BASE_NAME, baseName)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
