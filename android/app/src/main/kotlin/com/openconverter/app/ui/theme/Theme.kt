package com.openconverter.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

private val SpotifyLight = lightColorScheme(
    background       = Color(0xFFF9F9F9),
    surface          = Color(0xFFFFFFFF),
    surfaceVariant   = Color(0xFFEEEEEE),
    primary          = Color(0xFF1DB954),
    onPrimary        = Color(0xFFFFFFFF),
    onBackground     = Color(0xFF121212),
    onSurface        = Color(0xFF121212),
    onSurfaceVariant = Color(0xFF5A5A5A),
    error            = Color(0xFFD32F2F),
    outline          = Color(0xFFCCCCCC),
)

@Composable
fun OpenConverterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SpotifyDark else SpotifyLight
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = OcTypography,
        content     = content,
    )
}
