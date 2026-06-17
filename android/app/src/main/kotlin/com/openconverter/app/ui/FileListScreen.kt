package com.openconverter.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openconverter.app.R
import com.openconverter.app.saf.SafAdapter
import com.openconverter.app.ui.vm.ConversionViewModel

@Composable
fun FileListScreen(
    onOpenSettings: () -> Unit = {},
) {
    val safAdapter = remember { SafAdapter() }
    val conversionVm: ConversionViewModel = viewModel()

    var selectedUris by rememberSaveable { mutableStateOf<List<Uri>>(emptyList()) }
    var targetFormat by rememberSaveable { mutableStateOf("mp3") }
    var pendingFormat by remember { mutableStateOf<String?>(null) }
    var pendingUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val pickFiles = safAdapter.openDocumentsContract()
    val createDoc = safAdapter.createDocumentContract()

    val pickFilesLauncher = rememberLauncherForActivityResult(pickFiles) { uris ->
        if (uris.isNotEmpty()) selectedUris = uris
    }
    val createDocLauncher = rememberLauncherForActivityResult(createDoc) { uri ->
        if (uri != null && pendingFormat != null) {
            conversionVm.startConversion(pendingUris, pendingFormat!!, uri)
        }
        pendingFormat = null
        pendingUris = emptyList()
    }

    fun startConversion() {
        if (selectedUris.isEmpty()) return
        pendingFormat = targetFormat
        pendingUris = selectedUris
        createDocLauncher.launch("output.${targetFormat}")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("OpenConverter v0.2.2")
        Text("M3: Service + SAF 已上线")

        // Target format selector
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("输出格式: ", style = MaterialTheme.typography.bodyMedium)
            listOf("mp3", "flac", "wav", "m4a", "ogg").forEach { fmt ->
                FilterChip(
                    selected = targetFormat == fmt,
                    onClick = { targetFormat = fmt },
                    label = { Text(fmt.uppercase()) },
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }

        // Selected files summary
        Text(
            text = if (selectedUris.isEmpty()) "未选择文件" else "已选 ${selectedUris.size} 个文件",
            style = MaterialTheme.typography.bodyMedium
        )

        // Buttons
        Button(onClick = { pickFilesLauncher.launch(arrayOf("audio/*")) }) {
            Text(stringResource(R.string.pick_files))
        }
        Button(
            onClick = { startConversion() },
            enabled = selectedUris.isNotEmpty(),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.start_conversion))
        }
        OutlinedButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.settings))
        }
    }
}
