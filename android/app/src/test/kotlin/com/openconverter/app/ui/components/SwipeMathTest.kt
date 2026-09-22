package com.openconverter.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeMathTest {
    @Test
    fun leftDragTracksUntilTheActionThenResists() {
        assertEquals(-40f, SwipeMath.rubberOffset(-40f, 88f), 0.01f)
        assertEquals(-88f, SwipeMath.rubberOffset(-88f, 88f), 0.01f)
        assertTrue(SwipeMath.rubberOffset(-200f, 88f) < -88f)
        assertTrue(SwipeMath.rubberOffset(-200f, 88f) > -160f)
    }

    @Test
    fun rightDragIsSoftAndSnapFollowsTheHalfwayPoint() {
        assertEquals(0f, SwipeMath.rubberOffset(0f, 88f), 0.01f)
        assertEquals(10f, SwipeMath.rubberOffset(40f, 88f), 0.01f)
        assertEquals(0f, SwipeMath.snapTarget(-40f, 88f), 0.01f)
        assertEquals(-88f, SwipeMath.snapTarget(-44f, 88f), 0.01f)
    }

    @Test
    fun aLongFlickCollapsesTheRow() {
        assertFalse(SwipeMath.shouldCollapse(-100f, 300f))
        assertTrue(SwipeMath.shouldCollapse(-220f, 300f))
    }
}
