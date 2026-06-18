package com.openconverter.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Decision #11: dark only — locked, no system follow
private val DarkColors = darkColorScheme(
    primary = GreenBase,
    onPrimary = androidx.compose.ui.graphics.Color.Black,
    primaryContainer = GreenDark,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.Black,
    background = BackgroundBase,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceButton,
    onSurfaceVariant = TextSecondary,
    outline = Divider,
    error = StatusError,
    onError = TextPrimary,
)

@Composable
fun OpenConverterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = OpenConverterTypography,
        content = content,
    )
}
