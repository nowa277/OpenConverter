package com.openconverter.app.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openconverter.app.theme.StatusError
import com.openconverter.app.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val vm: HistoryViewModel = viewModel()
    val entries by vm.entries.collectAsState()
    val df = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("历史") }) }
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("还没有转换记录", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(entries) { entry -> HistoryRow(entry, df) }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, df: SimpleDateFormat) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = df.format(Date(entry.timestampMs)),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Text(
                text = entry.sourceName,
                modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            AssistChip(
                onClick = {},
                label = { Text(entry.targetFormat.uppercase()) },
            )
            Text(
                text = if (entry.status == "DONE") "✓" else "✗",
                color = if (entry.status == "DONE") MaterialTheme.colorScheme.primary else StatusError,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
