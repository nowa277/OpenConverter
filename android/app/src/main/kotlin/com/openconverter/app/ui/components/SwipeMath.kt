package com.openconverter.app.ui.components

/** Same swipe curve as the desktop queue. Distances are in pixels. */
object SwipeMath {
    fun rubberOffset(dx: Float, actionWidth: Float, rubber: Float = 0.55f): Float {
        if (dx >= 0f) {
            val resisted = (dx * actionWidth * rubber) / (actionWidth + rubber * dx) * -1f
            return if (resisted == 0f) 0f else dx * 0.25f
        }
        if (dx >= -actionWidth) return dx
        val over = minOf(0f, dx + actionWidth)
        return -actionWidth + (over * actionWidth * rubber) / (actionWidth + rubber * kotlin.math.abs(over))
    }

    fun snapTarget(x: Float, actionWidth: Float): Float =
        if (x <= -actionWidth / 2f) -actionWidth else 0f

    fun shouldCollapse(x: Float, width: Float): Boolean = x <= -width * 0.72f
}
