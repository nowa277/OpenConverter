package com.openconverter.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openconverter.app.R
import com.openconverter.app.saf.SafAdapter
import com.openconverter.app.ui.vm.ConversionViewModel
import com.openconverter.app.ui.vm.FileListViewModel

@Composable
fun FileListScreen(
    onOpenSettings: () -> Unit = {},
) {
    val safAdapter = remember { SafAdapter() }
    val conversionVm: ConversionViewModel = viewModel()
    val fileListVm: FileListViewModel = viewModel()

    val files by fileListVm.files.collectAsState()

    var targetFormat by rememberSaveable { mutableStateOf("mp3") }
    var pendingFormat by remember { mutableStateOf<String?>(null) }
    var pendingUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val pickFiles = safAdapter.openDocumentsContract()
    val createDoc = remember(targetFormat) {
        safAdapter.createDocumentContract(getMimeForFormat(targetFormat))
    }

    val pickFilesLauncher = rememberLauncherForActivityResult(pickFiles) { uris ->
        if (uris.isNotEmpty()) fileListVm.addUris(uris)
    }
    val createDocLauncher = rememberLauncherForActivityResult(createDoc) { uri ->
        if (uri != null) {
            fileListVm.setOutputUri(uri)
            if (pendingFormat != null) {
                conversionVm.startConversion(pendingUris, pendingFormat!!, uri)
            }
        }
        pendingFormat = null
        pendingUris = emptyList()
    }

    fun startConversion() {
        if (files.isEmpty()) return
        val outUri = fileListVm.outputUri.value
        if (outUri == null) {
            // No output path set: open SAF picker first
            pendingFormat = targetFormat
            pendingUris = files.map { it.uri }
            createDocLauncher.launch("output.${targetFormat}")
        } else {
            // Output path set: start conversion directly
            conversionVm.startConversion(
                files.map { it.uri },
                targetFormat,
                outUri
            )
        }
    }

    fun pickOutputPath() {
        pendingFormat = targetFormat
        pendingUris = files.map { it.uri }
        createDocLauncher.launch("output.${targetFormat}")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("OpenConverter v0.3.0")
        Text("FormatDetector 补完 + 真转码")

        // Target format selector
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("输出格式: ", style = MaterialTheme.typography.bodyMedium)
            listOf("mp3", "flac", "wav", "m4a", "ogg").forEach { fmt ->
                FilterChip(
                    selected = targetFormat == fmt,
                    onClick = {
                        targetFormat = fmt
                        fileListVm.clearOutputIfFormatChanged(fmt)
                    },
                    label = { Text(fmt.uppercase()) },
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }

        // Output path display + edit
        val outputUri by fileListVm.outputUri.collectAsState()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val uri = outputUri
            Text(
                text = if (uri == null) "输出路径: 未设置"
                       else "输出路径: ${uri.lastPathSegment ?: uri.toString()}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            OutlinedButton(onClick = { pickOutputPath() }) {
                Text(if (uri == null) "选择输出路径" else "更改")
            }
        }

        // File count + total size
        Text(
            text = if (files.isEmpty()) "未选择文件"
                   else "已选 ${files.size} 个文件 (${formatTotalSize(files)})",
            style = MaterialTheme.typography.bodyMedium
        )

        // File list LazyColumn
        if (files.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(files) { entry -> FileRow(entry) }
            }
        }

        // Buttons row 1: file picker + clear
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { pickFilesLauncher.launch(arrayOf("audio/*")) }) {
                Text(if (files.isEmpty()) stringResource(R.string.pick_files) else "+ 添加文件")
            }
            if (files.isNotEmpty()) {
                OutlinedButton(onClick = { fileListVm.clear() }) {
                    Text("清空列表")
                }
            }
        }

        // Buttons row 2: start conversion
        Button(
            onClick = { startConversion() },
            enabled = files.isNotEmpty(),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.start_conversion))
        }

        // Settings
        OutlinedButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.settings))
        }
    }
}

@Composable
private fun FileRow(entry: FileEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (entry.readable) {
            CardDefaults.cardColors()
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = entry.displayName,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (entry.sourceFormat != null) {
                AssistChip(
                    onClick = {},
                    label = { Text(entry.sourceFormat.uppercase()) },
                )
            } else {
                AssistChip(
                    onClick = {},
                    label = { Text("未识别") },
                )
            }
            Text(
                text = formatSize(entry.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "?"
    bytes < 1024 -> "${bytes} B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}

private fun formatTotalSize(files: List<FileEntry>): String {
    val total = files.sumOf { it.sizeBytes }
    return formatSize(total)
}

private fun getMimeForFormat(format: String): String = when (format) {
    "mp3" -> "audio/mpeg"
    "flac" -> "audio/flac"
    "wav" -> "audio/wav"
    "m4a" -> "audio/mp4"
    "ogg" -> "audio/ogg"
    else -> "*/*"
}

private fun getMimeForFormatShort(format: String): String = when (format) {
    "mp3" -> "MP3"
    "flac" -> "FLAC"
    "wav" -> "WAV"
    "m4a" -> "M4A (AAC)"
    "ogg" -> "OGG (Vorbis)"
    else -> format.uppercase()
}
