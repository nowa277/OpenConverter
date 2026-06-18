package com.openconverter.app.engine

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EngineInterfacesTest {
    @Test
    fun `EngineResult aggregates ok, fail, total`() {
        val r = EngineResult(ok = 2, fail = 1, total = 3)
        assertEquals(2, r.ok)
        assertEquals(1, r.fail)
        assertEquals(3, r.total)
    }

    @Test
    fun `Clock wallClock is not null and returns positive ms`() {
        val ms = Clock.wallClock.nowMs()
        assertNotNull(ms)
        // wall-clock now in 2025+ is well above zero; under
        // unitTests.isReturnDefaultValues this still returns the JVM clock.
        assert(ms > 0L)
    }
}
