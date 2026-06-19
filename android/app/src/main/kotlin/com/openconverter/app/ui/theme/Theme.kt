package com.openconverter.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SpotifyDark = darkColorScheme(
    background       = OcBackground,
    surface          = OcSurface,
    surfaceVariant   = OcSurfaceVariant,
    primary          = OcPrimary,
    onPrimary        = OcOnPrimary,
    onBackground     = OcOnBackground,
    onSurface        = OcOnSurface,
    onSurfaceVariant = OcOnSurfaceVariant,
    error            = OcError,
    outline          = OcOutline,
)

@Composable
fun OpenConverterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpotifyDark,
        typography  = OcTypography,
        content     = content,
    )
}
