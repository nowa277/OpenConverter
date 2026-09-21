package com.openconverter.app.decoders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class NcmStreamingMetaTest {
    private fun resourceExists(path: String): Boolean =
        javaClass.classLoader!!.getResource(path) != null

    private fun loadResource(path: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream(path)!!.use { it.readBytes() }

    @Test fun streaming_result_carries_tags_cover_and_musicId() {
        assumeTrue("test-ncm/sample.ncm not present", resourceExists("test-ncm/sample.ncm"))
        val cipher = loadResource("test-ncm/sample.ncm")
        val audio = ByteArrayOutputStream()
        val result = NcmDecoder.decryptStreaming(ByteArrayInputStream(cipher), audio)
        assertTrue(result.format == "mp3" || result.format == "flac")
        assertTrue(result.tags["title"].orEmpty().isNotBlank())
        assertTrue(result.tags["artist"].orEmpty().isNotBlank())
        assertNotNull(result.musicId)
        val cover = result.cover
        assertNotNull(cover)
        assertTrue(cover!!.size > 32)
        val jpeg = cover[0] == 0xFF.toByte() && cover[1] == 0xD8.toByte()
        val png = cover[0] == 0x89.toByte() && cover[1] == 0x50.toByte()
        assertTrue("cover must be jpeg or png", jpeg || png)
        assertFalse(result.rawMeta.isNullOrBlank())
    }

    @Test fun byte_array_path_still_matches_streaming_audio() {
        assumeTrue("test-ncm/sample.ncm not present", resourceExists("test-ncm/sample.ncm"))
        val cipher = loadResource("test-ncm/sample.ncm")
        val buffered = NcmDecoder.decrypt(cipher)
        val streamed = ByteArrayOutputStream()
        val result = NcmDecoder.decryptStreaming(ByteArrayInputStream(cipher), streamed)
        org.junit.Assert.assertArrayEquals(buffered.audio, streamed.toByteArray())
        assertEquals(buffered.format, result.format)
        assertEquals(buffered.meta, result.rawMeta)
    }
}
