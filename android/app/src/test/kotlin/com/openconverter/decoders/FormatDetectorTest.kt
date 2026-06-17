package com.openconverter.decoders

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatDetectorTest {
    @Test
    fun detects_ncm_by_magic() {
        val bytes = "CTENFDAM".toByteArray(Charsets.US_ASCII) + ByteArray(8)
        assertEquals("ncm", FormatDetector.detect(bytes))
    }

    @Test
    fun detects_qmc0_by_magic() {
        val bytes = "QTag".toByteArray(Charsets.US_ASCII) + ByteArray(12)
        assertEquals("qmc0", FormatDetector.detect(bytes))
    }

    @Test
    fun detects_kgma_by_magic() {
        val bytes = "KGMA".toByteArray(Charsets.US_ASCII) + ByteArray(12)
        assertEquals("kgma", FormatDetector.detect(bytes))
    }

    @Test
    fun returns_null_for_unknown() {
        val bytes = "UNKNOWN1234567".toByteArray(Charsets.US_ASCII)
        assertNull(FormatDetector.detect(bytes))
    }

    @Test
    fun returns_null_for_empty() {
        assertNull(FormatDetector.detect(ByteArray(0)))
    }

    @Test
    fun returns_null_for_too_short() {
        assertNull(FormatDetector.detect(ByteArray(4)))
    }
}