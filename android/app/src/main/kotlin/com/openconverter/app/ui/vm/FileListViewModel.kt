package com.openconverter.app.ui.vm

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openconverter.app.decoders.FormatDetector
import com.openconverter.app.ui.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the list of selected files and runs FormatDetector on each.
 * v0.3.0 only does static display — no progress, no cancel.
 */
class FileListViewModel(app: Application) : AndroidViewModel(app) {
    private val _files = MutableStateFlow<List<FileEntry>>(emptyList())
    val files: StateFlow<List<FileEntry>> = _files

    private val _outputUri = MutableStateFlow<Uri?>(null)
    val outputUri: StateFlow<Uri?> = _outputUri

    fun setOutputUri(uri: Uri) {
        _outputUri.value = uri
    }

    /**
     * If the user changes output format, invalidate any pre-set output path
     * whose file extension no longer matches the new format.
     */
    fun clearOutputIfFormatChanged(newFormat: String) {
        val current = _outputUri.value ?: return
        val ext = current.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
        if (ext != newFormat) {
            _outputUri.value = null
        }
    }

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val newEntries = withContext(Dispatchers.IO) {
                uris.map { uri -> readEntry(uri) }
            }
            _files.update { current ->
                (current + newEntries).distinctBy { it.uri }
            }
        }
    }

    fun clear() {
        _files.value = emptyList()
    }

    fun remove(uri: Uri) {
        _files.update { current -> current.filter { it.uri != uri } }
    }

    private fun readEntry(uri: Uri): FileEntry {
        val ctx = getApplication<Application>()
        var displayName = uri.lastPathSegment ?: "unknown"
        var sizeBytes = 0L
        try {
            ctx.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && !c.isNull(nameIdx)) displayName = c.getString(nameIdx)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !c.isNull(sizeIdx)) sizeBytes = c.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {}

        val readable = try {
            ctx.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (_: Exception) {
            false
        }

        var sourceFormat: String? = null
        if (readable) {
            try {
                ctx.contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readNBytes(16)
                    sourceFormat = FormatDetector.detect(bytes, displayName)
                }
            } catch (_: Exception) {}
        }

        return FileEntry(
            uri = uri,
            displayName = displayName,
            sizeBytes = sizeBytes,
            sourceFormat = sourceFormat,
            readable = readable,
        )
    }
}
