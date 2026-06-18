package com.openconverter.app.ui.home

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openconverter.app.BuildConfig
import com.openconverter.app.R
import com.openconverter.app.components.OpenConverterLogo
import com.openconverter.app.saf.SafAdapter
import com.openconverter.app.ui.FileEntry
import com.openconverter.app.ui.vm.ConversionViewModel
import com.openconverter.app.ui.vm.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val safAdapter = remember { SafAdapter() }
    val homeVm: HomeViewModel = viewModel()
    val conversionVm: ConversionViewModel = viewModel()
    val context = LocalContext.current

    val files by homeVm.files.collectAsState()
    val outputFolder by homeVm.outputFolderUri.collectAsState()
    val currentFormat by homeVm.currentFormat.collectAsState()

    val pickFiles = safAdapter.openDocumentsContract()
    val openTree = safAdapter.openDocumentTreeContract()

    val pickFilesLauncher = rememberLauncherForActivityResult(pickFiles) { uris ->
        if (uris.isNotEmpty()) {
            val persisted = safAdapter.persistReadAccess(context.contentResolver, uris)
            if (persisted.isNotEmpty()) homeVm.addUris(persisted)
        }
    }
    val openTreeLauncher = rememberLauncherForActivityResult(openTree) { uri ->
        if (uri != null) {
            safAdapter.persistTreeWriteAccess(context.contentResolver, uri)
            homeVm.setOutputFolder(uri)
        }
    }

    fun startConversion() {
        if (files.isEmpty()) return
        val folderUri = homeVm.outputFolderUri.value ?: run {
            openTreeLauncher.launch(null)
            return
        }
        conversionVm.startConversion(
            uris = files.map { it.uri },
            targetFormat = currentFormat,
            folderUri = folderUri,
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OpenConverterLogo()
                        Spacer(Modifier.width(8.dp))
                        Text("OpenConverter v${BuildConfig.VERSION_NAME}")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { startConversion() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text("开始转换", style = MaterialTheme.typography.labelLarge)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Output folder row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("输出文件夹", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = outputFolder?.lastPathSegment ?: "未选择",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(onClick = { openTreeLauncher.launch(null) }) {
                    Text(if (outputFolder == null) "选择" else "更改")
                }
            }

            // Format chips
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("mp3", "flac", "wav", "m4a", "ogg").forEach { fmt ->
                    FilterChip(
                        selected = currentFormat == fmt,
                        onClick = { homeVm.setCurrentFormat(fmt) },
                        label = { Text(fmt.uppercase()) },
                    )
                }
            }

            // File count + total
            Text(
                text = if (files.isEmpty()) "未选择文件"
                       else "已选 ${files.size} 个文件 (${formatTotalSize(files)})",
                style = MaterialTheme.typography.bodyMedium,
            )

            // File list
            if (files.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(files) { entry -> FileCard(entry) }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            // Add files button
            OutlinedButton(
                onClick = { pickFilesLauncher.launch(arrayOf("audio/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (files.isEmpty()) "选择音频文件" else "+ 添加更多文件")
            }
        }
    }
}

@Composable
private fun FileCard(entry: FileEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (entry.readable) CardDefaults.cardColors()
                 else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = entry.displayName,
                modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            AssistChip(
                onClick = {},
                label = { Text(entry.sourceFormat?.uppercase() ?: "未识别") },
            )
            Text(
                text = formatSize(entry.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

private fun formatTotalSize(files: List<FileEntry>): String = formatSize(files.sumOf { it.sizeBytes })
