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

    private val _outputFolderUri = MutableStateFlow<Uri?>(null)
    val outputFolderUri: StateFlow<Uri?> = _outputFolderUri

    private val _outputBaseName = MutableStateFlow<String>("")
    val outputBaseName: StateFlow<String> = _outputBaseName

    fun setOutputFolder(uri: Uri, firstFileName: String? = null) {
        _outputFolderUri.value = uri
        // Auto-derive base name from first file if not set yet.
        if (_outputBaseName.value.isEmpty() && firstFileName != null) {
            _outputBaseName.value = stripExtension(firstFileName)
        }
    }

    fun setOutputBaseName(name: String) {
        _outputBaseName.value = name
    }

    /**
     * If the user changes output format, clear the derived base name so it
     * re-derives on next click. The folder itself stays.
     */
    fun clearOutputIfFormatChanged(@Suppress("UNUSED_PARAMETER") newFormat: String) {
        if (_outputBaseName.value.isNotEmpty()) {
            _outputBaseName.value = ""
        }
    }

    /** Compose "baseName.targetFormat" using the current base name. */
    fun derivedOutputName(targetFormat: String): String {
        val base = _outputBaseName.value.ifEmpty { "output" }
        return "$base.$targetFormat"
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

    private fun stripExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot > 0) fileName.substring(0, lastDot) else fileName
    }
}
