package com.openconverter.app.decoders

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class QmcKeyDiscoveryTest {
    @Test fun unknown_header_without_embedded_key_fails_instead_of_returning_mp3() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            QmcDecoder.decrypt(ByteArray(256) { 0x42 })
        }
        assertTrue(error.message!!.contains("key", ignoreCase = true))
    }

    @Test fun plain_audio_with_qmc_extension_is_preserved() {
        val result = QmcDecoder.decrypt(SyntheticNcm.audio)
        assertEquals("wav", result.format)
        assertArrayEquals(SyntheticNcm.audio, result.audio)
    }

    @Test fun external_key_layouts_report_missing_key_for_buffered_and_streaming_paths() {
        for (tail in listOf("STag", "musicex\u0000")) {
            val cipher = ByteArray(100) { 0x42 } + tail.toByteArray()
            val buffered = assertThrows(IllegalArgumentException::class.java) { QmcDecoder.decrypt(cipher) }
            val streaming = assertThrows(IllegalArgumentException::class.java) {
                QmcDecoder.decrypt(ByteArrayInputStream(cipher), ByteArrayOutputStream(), 13)
            }
            assertEquals(buffered.message, streaming.message)
            assertTrue(buffered.message!!.contains("key", ignoreCase = true))
        }
    }
}
