package com.openconverter.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openconverter.app.R
import com.openconverter.app.saf.SafAdapter
import com.openconverter.app.ui.components.FileCard
import com.openconverter.app.ui.components.FormatChip
import com.openconverter.app.ui.components.GreenCta
import com.openconverter.app.ui.components.PillButton

private val FORMATS = listOf("mp3", "flac", "wav", "m4a", "ogg")
private val BITRATES = listOf("128k", "192k", "320k", null)

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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.ic_logo),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(22.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                    }
                },
                actions = {
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.settings_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        bottomBar = {
            val canStart = state.files.isNotEmpty() && state.outputFolderUri != null && !state.running
            GreenCta(
                text = if (state.running) stringResource(R.string.notif_cancel) else stringResource(R.string.home_start),
                enabled = canStart || state.running,
                onClick = { if (state.running) viewModel.cancel(ctx) else viewModel.start(ctx) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            PillButton(
                text = "+ ${stringResource(R.string.home_pick_files)}",
                onClick = { pickFiles.launch(arrayOf("audio/*", "*/*")) },
            )
            // Output folder row
            SummaryRow(
                label = stringResource(R.string.home_pick_folder),
                value = state.outputFolderName ?: stringResource(R.string.home_no_folder),
                muted = state.outputFolderUri == null,
                onClick = { pickFolder.launch(null) },
            )
            // Format / bitrate row -> opens the bottom sheet
            val fmtLabel = state.targetFormat.uppercase()
            val brLabel = state.bitrate ?: stringResource(R.string.home_bitrate_lossless)
            SummaryRow(
                label = stringResource(R.string.home_target_format),
                value = "$fmtLabel · $brLabel",
                muted = false,
                onClick = { viewModel.openControlsSheet() },
            )
            Spacer(Modifier.height(4.dp))

            if (state.files.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.home_no_files),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(state.files, key = { it.uri }) { f ->
                        FileCard(
                            name = f.displayName,
                            sizeBytes = f.sizeBytes,
                            state = f.state,
                            percent = f.percent,
                            error = f.error,
                        )
                    }
                }
            }
        }
    }

    if (state.showControlsSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeControlsSheet() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.home_target_format),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Row {
                    FORMATS.forEach { fmt ->
                        FormatChip(
                            label = fmt.uppercase(),
                            selected = fmt == state.targetFormat,
                            onClick = { viewModel.setTargetFormat(fmt) },
                        )
                    }
                }
                Text(
                    stringResource(R.string.home_bitrate),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Row {
                    BITRATES.forEach { b ->
                        FormatChip(
                            label = b ?: stringResource(R.string.home_bitrate_lossless),
                            selected = b == state.bitrate,
                            onClick = { viewModel.setBitrate(b) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, muted: Boolean, onClick: () -> Unit) {
    val valueColor = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
