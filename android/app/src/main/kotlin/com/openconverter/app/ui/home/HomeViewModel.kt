package com.openconverter.app.ui.home

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openconverter.app.engine.ProgressEvent
import com.openconverter.app.saf.SafAdapter
import com.openconverter.app.service.ConversionService
import com.openconverter.app.ui.components.FileState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FileEntry(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long = -1L,
    val state: FileState = FileState.Pending,
    val percent: Int = 0,
    val error: String? = null,
)

data class HomeUiState(
    val files: List<FileEntry> = emptyList(),
    val outputFolderUri: String? = null,
    val outputFolderName: String? = null,
    val targetFormat: String = "mp3",
    val bitrate: String? = "320k",
    val running: Boolean = false,
    val showControlsSheet: Boolean = false,
    /** Set when the user picks a folder that Android 14 SAF rejects as
     *  "can't use this folder" (e.g. emulator's /sdcard/Music/ or any
     *  private-dir on Android 14). When non-null, Start is blocked and the
     *  Output-folder row surfaces this message. */
    val folderError: String? = null,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var binder: ConversionService.LocalBinder? = null
    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val b = service as? ConversionService.LocalBinder ?: return
            binder = b
            viewModelScope.launch {
                b.service.progress.collect(::onEvent)
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) { binder = null }
    }

    fun bind(context: Context) {
        context.bindService(
            Intent(context, ConversionService::class.java),
            conn, Context.BIND_AUTO_CREATE,
        )
    }
    fun unbind(context: Context) {
        runCatching { context.unbindService(conn) }
        binder = null
    }

    fun setFiles(uris: List<Uri>) {
        val ctx = getApplication<Application>()
        val entries = uris.map { uri ->
            FileEntry(
                uri = uri.toString(),
                displayName = SafAdapter.queryDisplayName(ctx, uri),
                sizeBytes = mapSize(SafAdapter.querySize(ctx, uri)),
            )
        }
        _state.update { it.copy(files = entries) }
    }
    fun setOutputFolder(uri: Uri) {
        val ctx = getApplication<Application>()
        val takeResult = runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        if (takeResult.isFailure) {
            _state.update { it.copy(
                outputFolderUri = null,
                outputFolderName = null,
                folderError = "Android refused permission for this folder. Pick another (try Downloads or create a new folder in My Files).",
            ) }
            return
        }
        val name = runCatching {
            DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':').ifBlank { uri.lastPathSegment }
        }.getOrNull() ?: "folder"
        // Write-probe: try creating + deleting a 0-byte file via the SAF tree.
        // Catches the Android 14 "can't use this folder" case where the
        // tree-URI persists but the underlying provider refuses creates.
        val writeProbe = runCatching {
            val treeDocUri = DocumentsContract.buildDocumentUriUsingTree(
                uri, DocumentsContract.getTreeDocumentId(uri),
            )
            val probeName = ".oc-write-probe-${System.currentTimeMillis()}"
            val probeUri = DocumentsContract.createDocument(
                ctx.contentResolver, treeDocUri, "application/octet-stream", probeName,
            )
            if (probeUri == null) error("createDocument returned null")
            DocumentsContract.deleteDocument(ctx.contentResolver, probeUri)
        }
        if (writeProbe.isFailure) {
            // Roll back the permission so the user can re-pick freely.
            runCatching {
                ctx.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            _state.update { it.copy(
                outputFolderUri = null,
                outputFolderName = null,
                folderError = "Can't write to '$name'. Pick a folder you can create files in (Downloads, Documents, or a new folder).",
            ) }
            return
        }
        _state.update { it.copy(
            outputFolderUri = uri.toString(),
            outputFolderName = name,
            folderError = null,
        ) }
    }
    fun setTargetFormat(fmt: String) = _state.update { it.copy(targetFormat = fmt) }
    fun setBitrate(b: String?) = _state.update { it.copy(bitrate = b) }

    fun openControlsSheet() = _state.update { it.copy(showControlsSheet = true) }
    fun closeControlsSheet() = _state.update { it.copy(showControlsSheet = false) }

    fun start(context: Context) {
        val s = _state.value
        if (s.files.isEmpty() || s.outputFolderUri == null || s.running) return
        if (s.folderError != null) return
        _state.update { it.copy(running = true, files = it.files.map { f -> f.copy(state = FileState.Pending, percent = 0, error = null) }) }
        val intent = ConversionService.makeIntent(
            context,
            inputs = s.files.map { it.uri },
            names  = s.files.map { it.displayName },
            folder = s.outputFolderUri,
            target = s.targetFormat,
            bitrate = s.bitrate,
        )
        ContextCompat.startForegroundService(context, intent)
    }

    fun cancel(context: Context) {
        val cancel = Intent(context, ConversionService::class.java).setAction(ConversionService.ACTION_CANCEL)
        ContextCompat.startForegroundService(context, cancel)
    }

    fun clearFiles() {
        _state.update {
            it.copy(
                files = emptyList(),
                running = false,
                showControlsSheet = false,
            )
        }
    }

    private fun onEvent(ev: ProgressEvent) {
        _state.update { s ->
            val files = s.files.toMutableList()
            when (ev) {
                is ProgressEvent.Start    -> if (ev.index in files.indices) files[ev.index] = files[ev.index].copy(state = FileState.Running, percent = 0)
                is ProgressEvent.Progress -> if (ev.index in files.indices) files[ev.index] = files[ev.index].copy(state = FileState.Running, percent = ev.percent)
                is ProgressEvent.Done     -> if (ev.index in files.indices) files[ev.index] = files[ev.index].copy(state = FileState.Done, percent = 100)
                is ProgressEvent.Failed   -> if (ev.index in files.indices) files[ev.index] = files[ev.index].copy(state = FileState.Failed, error = ev.message)
                ProgressEvent.BatchDone   -> return@update s.copy(running = false, files = files)
            }
            s.copy(files = files)
        }
    }

    companion object { fun mapSize(raw: Long): Long = if (raw > 0) raw else -1L }
}
