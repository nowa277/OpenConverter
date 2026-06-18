package com.openconverter.app

import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuildConfigTest {
    @Test
    fun `GIT_HASH is non-empty short hash or 'unknown'`() {
        val h = BuildConfig.GIT_HASH
        assertNotNull(h)
        assertTrue(
            h == "unknown" || h.matches(Regex("^[0-9a-f]{7,40}$")),
            "GIT_HASH looks invalid: '$h'",
        )
    }

    @Test
    fun `GIT_CLEAN is 'clean' or 'dirty'`() {
        assertTrue(BuildConfig.GIT_CLEAN in setOf("clean", "dirty"),
            "GIT_CLEAN must be one of clean/dirty, was: ${BuildConfig.GIT_CLEAN}")
    }
}
