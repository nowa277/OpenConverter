package com.openconverter.app.decoders

import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals

class KwmDecoderTest {

    @Test
    fun decrypts_synthetic_kwm_to_byte_equivalent_of_desktop() {
        val enc = File("src/test/resources/test-kwm/synthetic-kwm.kwm").readBytes()
        val expectedSha = loadExpectedSha("synthetic-kwm.kwm")
        val audio = KwmDecoder.decrypt(enc)
        val actual = MessageDigest.getInstance("SHA-256").digest(audio.bytes).joinToString("") {
            "%02x".format(it)
        }
        assertEquals(expectedSha, actual, "sha256 mismatch for synthetic-kwm.kwm")
        assertEquals("mp3", audio.format, "format hint wrong for synthetic-kwm.kwm")
    }

    private fun loadExpectedSha(filename: String): String {
        val json = File("src/test/resources/test-kwm/expected-sha256.json").readText()
        val regex = Regex("\"$filename\"\\s*:\\s*\"([a-f0-9]{64})\"")
        return regex.find(json)?.groupValues?.get(1)
            ?: error("Expected sha256 for $filename not in expected-sha256.json")
    }
}