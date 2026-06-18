package com.openconverter.app.ui.home

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
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
    val state: FileState = FileState.Pending,
    val percent: Int = 0,
    val error: String? = null,
)

data class HomeUiState(
    val files: List<FileEntry> = emptyList(),
    val outputFolderUri: String? = null,
    val targetFormat: String = "mp3",
    val bitrate: String? = "320k",
    val running: Boolean = false,
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
            FileEntry(uri = uri.toString(), displayName = SafAdapter.queryDisplayName(ctx, uri))
        }
        _state.update { it.copy(files = entries) }
    }
    fun setOutputFolder(uri: Uri) {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        _state.update { it.copy(outputFolderUri = uri.toString()) }
    }
    fun setTargetFormat(fmt: String) = _state.update { it.copy(targetFormat = fmt) }
    fun setBitrate(b: String?) = _state.update { it.copy(bitrate = b) }

    fun start(context: Context) {
        val s = _state.value
        if (s.files.isEmpty() || s.outputFolderUri == null || s.running) return
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
}
