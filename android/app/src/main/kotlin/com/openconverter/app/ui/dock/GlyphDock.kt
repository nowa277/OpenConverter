package com.openconverter.app.ui.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openconverter.app.R

private data class DockItem(val id: String, val icon: ImageVector, val label: Int)

@Composable
fun GlyphDock(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        DockItem("convert", DockIcons.convert, R.string.nav_convert),
        DockItem("history", DockIcons.history, R.string.nav_history),
        DockItem("settings", DockIcons.settings, R.string.nav_settings),
        DockItem("about", DockIcons.about, R.string.nav_about),
    )
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                drawLine(outline, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            }
            .navigationBarsPadding()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelect(item.id) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = stringResource(item.label),
                    tint = if (item.id == selected) MaterialTheme.colorScheme.primary else muted,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
