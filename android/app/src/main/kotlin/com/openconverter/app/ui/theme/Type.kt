package com.openconverter.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Same five sizes as the desktop UI: page 24, section 16, body 14, note 13, meta 12.
 */
private val sans = FontFamily.Default

private fun style(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = sans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
)

private val page = style(24, 32, FontWeight.Bold)
private val section = style(16, 22, FontWeight.Bold)
private val body = style(14, 20, FontWeight.Normal)
private val note = style(13, 18, FontWeight.Normal)
private val meta = style(12, 16, FontWeight.Normal)
private val bodyBold = style(14, 20, FontWeight.Bold)

val OcTypography = Typography(
    displayMedium = page,
    headlineSmall = page,
    titleLarge = page,
    titleMedium = section,
    titleSmall = section,
    bodyLarge = body,
    bodyMedium = body,
    bodySmall = note,
    labelLarge = bodyBold,
    labelMedium = meta,
    labelSmall = meta,
)
