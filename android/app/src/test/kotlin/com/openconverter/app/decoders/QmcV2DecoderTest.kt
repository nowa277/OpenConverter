package com.openconverter.app.decoders

import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QmcV2DecoderTest {

    // Synthetic ekey used to encrypt the .mflac / .mgg fixtures. In production
    // this is a base64 string extracted from the QQ Music client's local DB.
    private val testEkey = "Y2lwaGVydGV4dHN0cmluZw=="

    @Test
    fun decrypts_synthetic_mflac_to_byte_equivalent_of_desktop() {
        val enc = File("src/test/resources/test-qmc-v2/synthetic-mflac.mflac").readBytes()
        val expectedSha = loadExpectedSha("synthetic-mflac.mflac")
        val audio = QmcV2Decoder.decrypt(enc, testEkey)
        val actual = MessageDigest.getInstance("SHA-256").digest(audio.bytes).joinToString("") {
            "%02x".format(it)
        }
        assertEquals(expectedSha, actual, "sha256 mismatch for synthetic-mflac.mflac")
        assertEquals("flac", audio.format, "format hint wrong for synthetic-mflac.mflac")
    }

    @Test
    fun decrypts_synthetic_mgg_to_byte_equivalent_of_desktop() {
        val enc = File("src/test/resources/test-qmc-v2/synthetic-mgg.mgg").readBytes()
        val expectedSha = loadExpectedSha("synthetic-mgg.mgg")
        val audio = QmcV2Decoder.decrypt(enc, testEkey)
        val actual = MessageDigest.getInstance("SHA-256").digest(audio.bytes).joinToString("") {
            "%02x".format(it)
        }
        assertEquals(expectedSha, actual, "sha256 mismatch for synthetic-mgg.mgg")
        assertEquals("ogg", audio.format, "format hint wrong for synthetic-mgg.mgg")
    }

    @Test
    fun throws_MissingEkeyException_when_ekey_is_null() {
        val enc = File("src/test/resources/test-qmc-v2/synthetic-mflac.mflac").readBytes()
        assertFailsWith<MissingEkeyException> {
            QmcV2Decoder.decrypt(enc, null)
        }
    }

    @Test
    fun throws_MissingEkeyException_when_ekey_is_blank() {
        val enc = File("src/test/resources/test-qmc-v2/synthetic-mflac.mflac").readBytes()
        assertFailsWith<MissingEkeyException> {
            QmcV2Decoder.decrypt(enc, "   ")
        }
    }

    private fun loadExpectedSha(filename: String): String {
        val json = File("src/test/resources/test-qmc-v2/expected-sha256.json").readText()
        val regex = Regex("\"$filename\"\\s*:\\s*\"([a-f0-9]{64})\"")
        return regex.find(json)?.groupValues?.get(1)
            ?: error("Expected sha256 for $filename not in expected-sha256.json")
    }
}
