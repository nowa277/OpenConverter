package com.openconverter.app.decoders

import com.openconverter.app.meta.NcmTrackMeta
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class NcmStreamingMetaTest {
    @Test fun streaming_result_carries_tags_cover_and_musicId() {
        val cipher = SyntheticNcm.build()
        for (chunkSize in listOf(1, 7, 256, 8192)) {
            val audio = ByteArrayOutputStream()
            val result = NcmDecoder.decryptStreaming(ByteArrayInputStream(cipher), audio, chunkSize)
            assertEquals("wav", result.format)
            assertEquals(mapOf("title" to "Synthetic tone", "artist" to "Synth / Wave", "album" to "Regression fixtures"), result.tags)
            assertEquals("123456789", result.musicId)
            assertArrayEquals(SyntheticNcm.cover, result.cover)
            assertEquals(SyntheticNcm.META, result.rawMeta)
            assertArrayEquals(SyntheticNcm.audio, audio.toByteArray())
        }
    }

    @Test fun byte_array_path_matches_streaming_audio_and_metadata() {
        val cipher = SyntheticNcm.build()
        val buffered = NcmDecoder.decrypt(cipher)
        val audio = ByteArrayOutputStream()
        val streamed = NcmDecoder.decryptStreaming(ByteArrayInputStream(cipher), audio, 13)
        assertArrayEquals(buffered.audio, audio.toByteArray())
        assertEquals(buffered.format, streamed.format)
        assertEquals(buffered.meta, streamed.rawMeta)
        val tags = NcmTrackMeta.fromJson(buffered.meta)
        assertEquals(tags.tags, streamed.tags)
        assertEquals(tags.musicId, streamed.musicId)
    }

    @Test fun metadata_free_fixture_keeps_audio_and_reports_absent_fields() {
        val cipher = SyntheticNcm.build(meta = null, image = byteArrayOf())
        val audio = ByteArrayOutputStream()
        val result = NcmDecoder.decryptStreaming(ByteArrayInputStream(cipher), audio, 7)
        assertEquals(emptyMap<String, String>(), result.tags)
        assertNull(result.cover)
        assertNull(result.musicId)
        assertNull(result.rawMeta)
        assertArrayEquals(SyntheticNcm.audio, audio.toByteArray())
    }

    @Test fun metadata_free_legacy_generator_sample_has_no_title() {
        val cipher = javaClass.classLoader!!.getResourceAsStream("test-ncm/sample.ncm")!!.use { it.readBytes() }
        val result = NcmDecoder.decryptStreaming(ByteArrayInputStream(cipher), ByteArrayOutputStream())
        assertEquals("mp3", result.format)
        assertEquals(emptyMap<String, String>(), result.tags)
        assertNull(result.musicId)
        assertNull(result.cover)
    }

    @Test fun altered_magic_is_rejected_before_decryption() {
        val cipher = SyntheticNcm.build()
        cipher[0] = 'X'.code.toByte()
        val error = assertThrows(IllegalArgumentException::class.java) { NcmDecoder.decrypt(cipher) }
        assertEquals("NCM: bad magic", error.message)
    }
}
