package com.openconverter.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Tonal premium button — for secondary actions like "Add files". */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            contentColor   = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier.height(48.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
    }
}

/** Solid green premium button — the single accent CTA. Used ONLY for the primary action. */
@Composable
fun GreenCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor   = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier.height(56.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
    }
}
