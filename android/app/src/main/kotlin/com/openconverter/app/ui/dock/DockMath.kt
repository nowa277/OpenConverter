package com.openconverter.app.ui.dock

import kotlin.math.PI
import kotlin.math.cos

/**
 * Glyph magnification for the bottom dock.
 *
 * f = max(0, (1 + cos(180° * d / 3)) / 2) inside a reach of 3 item-widths.
 * Past that reach the factor stays 0. Scale is 1 + 0.32f, lift is 8dp * f.
 */
object DockMath {
    const val REACH = 3f

    fun factor(distance: Float): Float {
        if (distance >= REACH) return 0f
        val raw = (1.0 + cos(PI * distance / REACH)) / 2.0
        return raw.toFloat().coerceAtLeast(0f)
    }

    fun scale(factor: Float): Float = 1f + 0.32f * factor

    fun lift(factor: Float, eightPx: Float): Float = eightPx * factor
}
