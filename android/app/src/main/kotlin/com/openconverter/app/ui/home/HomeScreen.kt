package com.openconverter.app.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openconverter.app.R
import com.openconverter.app.saf.SafAdapter
import com.openconverter.app.ui.components.FileCard
import com.openconverter.app.ui.components.FileState
import com.openconverter.app.ui.components.FormatChip
import com.openconverter.app.ui.components.GreenCta
import com.openconverter.app.ui.components.SwipeRow

private val FORMATS = listOf("mp3", "flac", "wav", "m4a")
private val BITRATES = listOf("128k", "192k", "320k")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
) {
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
                    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                    androidx.compose.foundation.Image(
                        painter = painterResource(if (dark) R.drawable.brand_wordmark else R.drawable.brand_wordmark_light),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.height(28.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        bottomBar = {
            val s = state
            if (s.files.isNotEmpty()) {
                // Single-button tri-state CTA: Start → Cancel → Clear
                // Hide entirely when no files queued (the empty-state message handles that).
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    when {
                        s.running -> {
                            GreenCta(
                                text = stringResource(R.string.cancel_conversion),
                                onClick = { viewModel.cancel(ctx) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        s.files.any { it.state == FileState.Pending } -> {
                            GreenCta(
                                text = stringResource(R.string.start_conversion),
                                onClick = { viewModel.start(ctx) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        s.files.any { it.state == FileState.Failed } -> {
                            GreenCta(
                                text = stringResource(R.string.retry_failed),
                                onClick = { viewModel.retryFailed(ctx) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        else -> {
                            // All done → single Clear button
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.clearFiles() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                androidx.compose.material3.Text(stringResource(R.string.clear_queue))
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            val folderValue = state.folderError
                ?: state.outputFolderName
                ?: stringResource(R.string.home_no_folder)
            QuietRow(
                label = stringResource(R.string.home_pick_folder),
                value = folderValue,
                muted = state.outputFolderUri == null && state.folderError == null,
                onClick = { pickFolder.launch(state.outputFolderUri?.let { Uri.parse(it) }) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            val fmtLabel = state.targetFormat.uppercase()
            val brLabel = state.bitrate ?: stringResource(R.string.home_bitrate_lossless)
            QuietRow(
                label = stringResource(R.string.home_target_format),
                value = "$fmtLabel · $brLabel",
                muted = false,
                onClick = { viewModel.openControlsSheet() },
            )
            state.folderError?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            if (state.files.isEmpty()) {
                val dash = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 28.dp)
                        .clickable { pickFiles.launch(viewModel.getLastFolderUri()) }
                        .drawBehind {
                            drawRoundRect(
                                color = dash,
                                style = Stroke(
                                    width = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                                ),
                                cornerRadius = CornerRadius(16.dp.toPx()),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.home_pick_files),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { pickFiles.launch(viewModel.getLastFolderUri()) }) {
                        Text(
                            stringResource(R.string.home_add),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    itemsIndexed(state.files, key = { _, f -> f.uri }) { index, f ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        }
                        SwipeRow(onRemove = { viewModel.removeFile(f.uri) }) {
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
    }

    if (state.showControlsSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeControlsSheet() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // --- Target format section ---
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.home_target_format),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FORMATS.forEach { fmt ->
                            FormatChip(
                                label = fmt.uppercase(),
                                selected = fmt == state.targetFormat,
                                onClick = { viewModel.setTargetFormat(fmt) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                // --- Bitrate section (fixed options: 128k / 192k / 320k) ---
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.home_bitrate),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        BITRATES.forEach { b ->
                            FormatChip(
                                label = b,
                                selected = b == state.bitrate,
                                onClick = { viewModel.setBitrate(b) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun QuietRow(
    label: String,
    value: String,
    muted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
