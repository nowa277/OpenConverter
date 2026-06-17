package com.openconverter.app.decoders

import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals

class KgmDecoderTest {

    @Test
    fun decrypts_synthetic_kgm_to_byte_equivalent_of_desktop() {
        val enc = File("src/test/resources/test-kgm/synthetic-kgm.kgm").readBytes()
        val expectedSha = loadExpectedSha("synthetic-kgm.kgm")
        val audio = KgmDecoder.decrypt(enc, variant = "kgm")
        val actual = MessageDigest.getInstance("SHA-256").digest(audio.bytes).joinToString("") {
            "%02x".format(it)
        }
        assertEquals(expectedSha, actual, "sha256 mismatch for synthetic-kgm.kgm")
        assertEquals("mp3", audio.format, "format hint wrong for synthetic-kgm.kgm")
    }

    @Test
    fun decrypts_synthetic_kgma_to_byte_equivalent_of_desktop() {
        val enc = File("src/test/resources/test-kgm/synthetic-kgma.kgma").readBytes()
        val expectedSha = loadExpectedSha("synthetic-kgma.kgma")
        val audio = KgmDecoder.decrypt(enc, variant = "kgma")
        val actual = MessageDigest.getInstance("SHA-256").digest(audio.bytes).joinToString("") {
            "%02x".format(it)
        }
        assertEquals(expectedSha, actual, "sha256 mismatch for synthetic-kgma.kgma")
        assertEquals("flac", audio.format, "format hint wrong for synthetic-kgma.kgma")
    }

    @Test
    fun decrypts_synthetic_vpr_to_byte_equivalent_of_desktop() {
        val enc = File("src/test/resources/test-kgm/synthetic-vpr.vpr").readBytes()
        val expectedSha = loadExpectedSha("synthetic-vpr.vpr")
        val audio = KgmDecoder.decrypt(enc, variant = "vpr")
        val actual = MessageDigest.getInstance("SHA-256").digest(audio.bytes).joinToString("") {
            "%02x".format(it)
        }
        assertEquals(expectedSha, actual, "sha256 mismatch for synthetic-vpr.vpr")
        assertEquals("mp3", audio.format, "format hint wrong for synthetic-vpr.vpr")
    }

    private fun loadExpectedSha(filename: String): String {
        val json = File("src/test/resources/test-kgm/expected-sha256.json").readText()
        val regex = Regex("\"$filename\"\\s*:\\s*\"([a-f0-9]{64})\"")
        return regex.find(json)?.groupValues?.get(1)
            ?: error("Expected sha256 for $filename not in expected-sha256.json")
    }
}
