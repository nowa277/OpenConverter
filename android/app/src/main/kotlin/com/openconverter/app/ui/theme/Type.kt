package com.openconverter.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Binary type weight (bold/regular) — mirrors Spotify's CircularSp ladder
 * using Roboto (system default; CircularSp is proprietary and cannot be
 * shipped). All headlines/titles use Bold; body uses Normal.
 */
private val sans = FontFamily.Default

val OcTypography = Typography(
    displayMedium  = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold,   fontSize = 36.sp, lineHeight = 44.sp),
    headlineSmall  = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold,   fontSize = 24.sp, lineHeight = 32.sp),
    titleMedium    = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold,   fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall     = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold,   fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge      = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium     = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall      = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge     = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold,   fontSize = 14.sp, lineHeight = 20.sp),
)
