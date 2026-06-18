package com.openconverter.app.ui.home

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openconverter.app.R
import com.openconverter.app.saf.SafAdapter
import com.openconverter.app.ui.components.FileCard
import com.openconverter.app.ui.components.FileState
import com.openconverter.app.ui.components.FormatChip
import com.openconverter.app.ui.components.GreenCta
import com.openconverter.app.ui.components.PillButton

private val FORMATS = listOf("mp3", "flac", "wav", "m4a", "ogg")
private val BITRATES = listOf("128k", "192k", "320k", null) // null = lossless / codec default

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current

    DisposableEffect(Unit) {
        viewModel.bind(ctx)
        onDispose { viewModel.unbind(ctx) }
    }

    val pickFiles = rememberLauncherForActivityResult(SafAdapter.openMultipleAudioFilesContract()) { uris ->
        if (uris.isNotEmpty()) viewModel.setFiles(uris)
    }
    val pickFolder = rememberLauncherForActivityResult(SafAdapter.openOutputFolderContract()) { uri ->
        if (uri != null) viewModel.setOutputFolder(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(
                    text = "+ ${stringResource(R.string.home_pick_files)}",
                    onClick = { pickFiles.launch(arrayOf("audio/*", "*/*")) },
                )
                PillButton(
                    text = stringResource(R.string.home_pick_folder),
                    onClick = { pickFolder.launch(null) },
                )
            }

            ChipRow(
                label = stringResource(R.string.home_target_format),
                options = FORMATS,
                selected = state.targetFormat,
                toLabel = { it.uppercase() },
                onSelect = viewModel::setTargetFormat,
            )

            ChipRow(
                label = stringResource(R.string.home_bitrate),
                options = BITRATES,
                selected = state.bitrate,
                toLabel = { it ?: "Lossless" },
                onSelect = viewModel::setBitrate,
            )

            if (state.files.isEmpty()) {
                Text(
                    stringResource(R.string.home_no_files),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    items(state.files, key = { it.uri }) { f ->
                        FileCard(
                            name = f.displayName,
                            state = f.state,
                            percent = f.percent,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            val canStart = state.files.isNotEmpty() && state.outputFolderUri != null && !state.running
            GreenCta(
                text = if (state.running) "Cancel" else stringResource(R.string.home_start),
                enabled = canStart || state.running,
                onClick = {
                    if (state.running) viewModel.cancel(ctx) else viewModel.start(ctx)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun <T> ChipRow(
    label: String,
    options: List<T>,
    selected: T,
    toLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row {
            options.forEach { opt ->
                FormatChip(
                    label = toLabel(opt),
                    selected = opt == selected,
                    onClick = { onSelect(opt) },
                )
            }
        }
    }
}
