package com.openconverter.app.ui.history

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openconverter.app.R
import com.openconverter.app.engine.HistoryStatus
import com.openconverter.app.ui.theme.OcBackground
import com.openconverter.app.ui.theme.OcError
import com.openconverter.app.ui.theme.OcOnSurfaceVariant
import com.openconverter.app.ui.theme.OcPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    vm: HistoryViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { confirmClear = true }) {
                        Text(stringResource(R.string.history_clear))
                    }
                },
            )
        },
        containerColor = OcBackground,
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            if (state.records.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.history_empty),
                        color = OcOnSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.records, key = { it.ts }) { r ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    r.inputName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "→ ${r.targetFormat}" + (r.error?.let { "  ($it)" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OcOnSurfaceVariant,
                                )
                            }
                            val chipColor = when (r.status) {
                                HistoryStatus.SUCCESS -> OcPrimary
                                HistoryStatus.FAILED  -> OcError
                            }
                            val chipText = when (r.status) {
                                HistoryStatus.SUCCESS -> stringResource(R.string.history_status_success)
                                HistoryStatus.FAILED  -> stringResource(R.string.history_status_failed)
                            }
                            Text(
                                chipText,
                                color = chipColor,
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                DateUtils.getRelativeTimeSpanString(
                                    r.ts, System.currentTimeMillis(),
                                    DateUtils.MINUTE_IN_MILLIS,
                                ).toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = OcOnSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text  = { Text(stringResource(R.string.history_clear_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clear()
                }) { Text(stringResource(R.string.history_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel_action)) }
            },
        )
    }
}