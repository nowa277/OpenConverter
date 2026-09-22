package com.openconverter.app.ui.dock

import org.junit.Assert.assertEquals
import org.junit.Test

class DockMathTest {
    @Test
    fun factorIsOneUnderTheFingerAndZeroAtTheReach() {
        assertEquals(1f, DockMath.factor(0f), 0.001f)
        assertEquals(0.5f, DockMath.factor(1.5f), 0.001f)
        assertEquals(0f, DockMath.factor(3f), 0.001f)
        assertEquals(0f, DockMath.factor(6f), 0.001f)
    }

    @Test
    fun scaleAndLiftFollowTheGlyphOnly() {
        assertEquals(1f, DockMath.scale(0f), 0.001f)
        assertEquals(1.32f, DockMath.scale(1f), 0.001f)
        assertEquals(0f, DockMath.lift(0f, 8f), 0.001f)
        assertEquals(8f, DockMath.lift(1f, 8f), 0.001f)
    }
}
