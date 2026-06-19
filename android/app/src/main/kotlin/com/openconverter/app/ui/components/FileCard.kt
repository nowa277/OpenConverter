package com.openconverter.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class FileState { Pending, Running, Done, Failed }

@Composable
fun FileCard(
    name: String,
    sizeBytes: Long,
    state: FileState,
    percent: Int,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                val sizeText = formatBytes(sizeBytes)
                if (sizeText.isNotEmpty()) {
                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = labelFor(state, percent),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = colorFor(state),
                )
            }
            if (state == FileState.Running) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (percent.coerceIn(0, 100)) / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            if (state == FileState.Failed && !error.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun labelFor(state: FileState, percent: Int) = when (state) {
    FileState.Pending -> "Pending"
    FileState.Running -> "$percent%"
    FileState.Done    -> "Done"
    FileState.Failed  -> "Failed"
}

@Composable
private fun colorFor(state: FileState) = when (state) {
    FileState.Done    -> MaterialTheme.colorScheme.primary
    FileState.Failed  -> MaterialTheme.colorScheme.error
    FileState.Running -> MaterialTheme.colorScheme.primary
    FileState.Pending -> MaterialTheme.colorScheme.onSurfaceVariant
}
