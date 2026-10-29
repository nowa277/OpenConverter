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
import com.openconverter.app.engine.BatchProgress
import com.openconverter.app.engine.ProgressEvent
import com.openconverter.app.saf.SafAdapter
import com.openconverter.app.service.ConversionService
import com.openconverter.app.ui.components.FileState
import kotlinx.coroutines.Job
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
    val batchId: String? = null,
    val showControlsSheet: Boolean = false,
    /** Set when the user picks a folder that Android 14 SAF rejects as
     *  "can't use this folder" (e.g. emulator's /sdcard/Music/ or any
     *  private-dir on Android 14). When non-null, Start is blocked and the
     *  Output-folder row surfaces this message. */
    val folderError: String? = null,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        run {
            val savedFolderUri = prefs.getString("output_folder_uri", null)
            val savedFolderName = prefs.getString("output_folder_name", null)
            val savedFormat = prefs.getString("target_format", "mp3") ?: "mp3"
            val savedBitrate = prefs.getString("bitrate", "320k")

            var restoredUri: String? = null
            var restoredName: String? = null
            if (savedFolderUri != null) {
                val uri = Uri.parse(savedFolderUri)
                val hasPerm = app.contentResolver.persistedUriPermissions.any {
                    it.uri == uri && it.isWritePermission
                }
                if (hasPerm) {
                    restoredUri = savedFolderUri
                    restoredName = savedFolderName ?: "folder"
                }
            }

            HomeUiState(
                outputFolderUri = restoredUri,
                outputFolderName = restoredName,
                targetFormat = savedFormat,
                bitrate = savedBitrate,
            )
        }
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var binder: ConversionService.LocalBinder? = null
    private var progressCollector: Job? = null
    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val b = service as? ConversionService.LocalBinder ?: return
            binder = b
            progressCollector?.cancel()
            progressCollector = viewModelScope.launch {
                b.service.progress.collect { snapshot ->
                    if (snapshot != null) _state.update { applySnapshot(it, snapshot) }
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            progressCollector?.cancel()
            progressCollector = null
            binder = null
        }
    }

    fun bind(context: Context) {
        context.bindService(
            Intent(context, ConversionService::class.java),
            conn, Context.BIND_AUTO_CREATE,
        )
    }
    fun unbind(context: Context) {
        progressCollector?.cancel()
        progressCollector = null
        runCatching { context.unbindService(conn) }
        binder = null
    }

    fun getLastFolderUri(): Uri? {
        val lastInput = prefs.getString("last_input_folder_uri", null)
        if (lastInput != null) {
            val parsed = runCatching { Uri.parse(lastInput) }.getOrNull()
            if (parsed != null) return parsed
        }
        return _state.value.outputFolderUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
    }

    fun setFiles(uris: List<Uri>) {
        val ctx = getApplication<Application>()
        uris.firstOrNull()?.let { firstUri ->
            prefs.edit().putString("last_input_folder_uri", firstUri.toString()).apply()
        }
        if (_state.value.running) return
        val entries = uris.map { uri ->
            FileEntry(
                uri = uri.toString(),
                displayName = SafAdapter.queryDisplayName(ctx, uri),
                sizeBytes = mapSize(SafAdapter.querySize(ctx, uri)),
            )
        }
        _state.update { it.copy(files = entries, batchId = null) }
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
            prefs.edit().remove("output_folder_uri").remove("output_folder_name").apply()
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
            prefs.edit().remove("output_folder_uri").remove("output_folder_name").apply()
            _state.update { it.copy(
                outputFolderUri = null,
                outputFolderName = null,
                folderError = "Can't write to '$name'. Pick a folder you can create files in (Downloads, Documents, or a new folder).",
            ) }
            return
        }
        prefs.edit()
            .putString("output_folder_uri", uri.toString())
            .putString("output_folder_name", name)
            .apply()
        _state.update { it.copy(
            outputFolderUri = uri.toString(),
            outputFolderName = name,
            folderError = null,
        ) }
    }
    fun setTargetFormat(fmt: String) {
        prefs.edit().putString("target_format", fmt).apply()
        _state.update { it.copy(targetFormat = fmt) }
    }
    fun setBitrate(b: String?) {
        prefs.edit().putString("bitrate", b).apply()
        _state.update { it.copy(bitrate = b) }
    }

    fun openControlsSheet() = _state.update { it.copy(showControlsSheet = true) }
    fun closeControlsSheet() = _state.update { it.copy(showControlsSheet = false) }

    fun start(context: Context) {
        val s = _state.value
        if (s.files.isEmpty() || s.outputFolderUri == null || s.running) return
        if (s.folderError != null) return
        val batchId = java.util.UUID.randomUUID().toString()
        _state.update { it.copy(running = true, batchId = batchId, files = it.files.map { f -> f.copy(state = FileState.Pending, percent = 0, error = null) }) }
        val intent = ConversionService.makeIntent(
            context,
            inputs = s.files.map { it.uri },
            names  = s.files.map { it.displayName },
            folder = s.outputFolderUri,
            target = s.targetFormat,
            bitrate = s.bitrate,
            batchId = batchId,
        )
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (error: IllegalStateException) {
            _state.update { current -> current.copy(running = false, files = current.files.map { it.copy(state = FileState.Failed, error = error.message) }) }
        } catch (error: SecurityException) {
            _state.update { current -> current.copy(running = false, files = current.files.map { it.copy(state = FileState.Failed, error = error.message) }) }
        }
    }

    fun retryFailed(context: Context) {
        if (_state.value.running) return
        val retries = retryEntries(_state.value.files)
        if (retries.isEmpty()) return
        _state.update { it.copy(files = retries) }
        start(context)
    }

    fun cancel() {
        val batchId = _state.value.batchId ?: return
        val service = binder?.service
        if (service != null) service.cancelBatch(batchId)
        else getApplication<Application>().startService(
            Intent(getApplication(), ConversionService::class.java).setAction(ConversionService.ACTION_CANCEL)
                .putExtra(ConversionService.EXTRA_BATCH_ID, batchId),
        )
    }

    fun removeFile(uri: String): Boolean {
        val current = _state.value
        val target = current.files.find { it.uri == uri } ?: return false
        if (current.running) return false
        if (target.state == FileState.Running) return false
        _state.update { it.copy(files = it.files.filterNot { file -> file.uri == uri }) }
        return true
    }

    fun clearFiles() {
        if (_state.value.running) return
        _state.update {
            it.copy(
                files = emptyList(),
                running = false,
                batchId = null,
                showControlsSheet = false,
            )
        }
    }

    companion object {
        fun applySnapshot(state: HomeUiState, snapshot: BatchProgress): HomeUiState {
            if (state.batchId != snapshot.batchId) return state
            val updated = snapshot.files.values.fold(state, ::applyProgress)
            return snapshot.terminal?.let { applyProgress(updated, it) } ?: updated
        }

        fun applyProgress(state: HomeUiState, event: ProgressEvent): HomeUiState {
            if (state.batchId != event.batchId || !state.running) return state
            val files = state.files.toMutableList()
            fun update(index: Int, change: (FileEntry) -> FileEntry) {
                if (index in files.indices && files[index].state in setOf(FileState.Pending, FileState.Running)) {
                    files[index] = change(files[index])
                }
            }
            when (event) {
                is ProgressEvent.Start -> update(event.index) { it.copy(state = FileState.Running, percent = 0) }
                is ProgressEvent.Progress -> update(event.index) { it.copy(state = FileState.Running, percent = event.percent) }
                is ProgressEvent.Done -> update(event.index) { it.copy(state = FileState.Done, percent = 100) }
                is ProgressEvent.Failed -> update(event.index) { it.copy(state = FileState.Failed, error = event.message) }
                is ProgressEvent.FileCancelled -> update(event.index) { it.copy(state = FileState.Cancelled) }
                is ProgressEvent.BatchDone -> return state.copy(running = false, files = files)
                is ProgressEvent.BatchCancelled -> return state.copy(
                    running = false,
                    files = files.map { if (it.state in setOf(FileState.Pending, FileState.Running)) it.copy(state = FileState.Cancelled) else it },
                )
            }
            return state.copy(files = files)
        }

        fun mapSize(raw: Long): Long = if (raw > 0) raw else -1L

        fun retryEntries(files: List<FileEntry>): List<FileEntry> = files
            .filter { it.state == FileState.Failed || it.state == FileState.Cancelled }
            .map { it.copy(state = FileState.Pending, percent = 0, error = null) }
    }
}
