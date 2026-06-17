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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openconverter.app.BuildConfig
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
    val context = LocalContext.current

    val files by fileListVm.files.collectAsState()
    val outputFolder by fileListVm.outputFolderUri.collectAsState()
    val outputBaseName by fileListVm.outputBaseName.collectAsState()

    var targetFormat by rememberSaveable { mutableStateOf("mp3") }

    val pickFiles = safAdapter.openDocumentsContract()
    val openTree = safAdapter.openDocumentTreeContract()

    val pickFilesLauncher = rememberLauncherForActivityResult(pickFiles) { uris ->
        if (uris.isNotEmpty()) {
            // Persist READ access across activity/process death.
            // Without this, Huawei HarmonyOS / vivo OriginOS reclaim the grant
            // and subsequent ContentResolver.openInputStream throws SecurityException.
            val persisted = safAdapter.persistReadAccess(context.contentResolver, uris)
            if (persisted.isNotEmpty()) fileListVm.addUris(persisted)
        }
    }
    val openTreeLauncher = rememberLauncherForActivityResult(openTree) { uri ->
        if (uri != null) {
            // Persist WRITE access to the tree so DocumentsContract.createDocument
            // works from the background service after process death.
            safAdapter.persistTreeWriteAccess(context.contentResolver, uri)
            val firstName = files.firstOrNull()?.displayName
            fileListVm.setOutputFolder(uri, firstName)
        }
    }

    fun startConversion() {
        if (files.isEmpty()) return
        val folderUri = fileListVm.outputFolderUri.value
        if (folderUri == null) {
            // No output folder yet — prompt user to pick one.
            openTreeLauncher.launch(null)
            return
        }
        val baseName = fileListVm.outputBaseName.value.ifEmpty { "output" }
        conversionVm.startConversion(
            files.map { it.uri },
            targetFormat,
            folderUri,
            baseName,
        )
    }

    val derivedName = remember(outputBaseName, targetFormat) {
        fileListVm.derivedOutputName(targetFormat)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("OpenConverter v${BuildConfig.VERSION_NAME}")
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

        // Output folder preview + edit base name (v0.3.1 hotfix)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "输出: " + (outputFolder?.let {
                    "${it.lastPathSegment ?: it.toString()}/$derivedName"
                } ?: "未设置输出文件夹"),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            OutlinedButton(
                onClick = { openTreeLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (outputFolder == null) "选择输出文件夹" else "更改文件夹")
            }
            if (outputFolder != null) {
                val isMulti = files.size > 1
                OutlinedTextField(
                    value = outputBaseName,
                    onValueChange = { fileListVm.setOutputBaseName(it) },
                    enabled = !isMulti,
                    label = { Text("输出文件名 (不含后缀)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(
                            if (isMulti) "多文件时使用各自源文件名 (已禁用编辑)"
                            else "将自动加上 .${targetFormat} 后缀"
                        )
                    },
                )
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

private fun stripExt(name: String): String {
    val lastDot = name.lastIndexOf('.')
    return if (lastDot > 0) name.substring(0, lastDot) else name
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
