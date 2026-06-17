package com.openconverter.app.decoders

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class NcmDecoderTest(private val sampleName: String, private val expectedSha256: String) {

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun data(): List<Array<Any>> {
            val samplesDir = File("src/test/resources/test-ncm")
            val samples = samplesDir.listFiles { f -> f.extension == "ncm" }
                ?: error("No NCM samples in ${samplesDir.absolutePath}")
            val expectedFile = File("src/test/resources/test-ncm/expected-sha256.json")
            val expectedJson = expectedFile.readText()
            // Parse JSON: {"filename.ncm": "sha256hex", ...}
            // No external JSON lib to keep deps minimal.
            val expectedMap = mutableMapOf<String, String>()
            val regex = Regex("\"([^\"]+\\.ncm)\"\\s*:\\s*\"([a-f0-9]{64})\"")
            regex.findAll(expectedJson).forEach { m ->
                expectedMap[m.groupValues[1]] = m.groupValues[2]
            }
            return samples.filter { expectedMap.containsKey(it.name) }
                .sortedBy { it.name }
                .map { arrayOf<Any>(it.name, expectedMap[it.name]!!) }
        }
    }

    @Test
    fun decrypts_ncm_to_byte_equivalent_of_desktop() {
        val sample = File("src/test/resources/test-ncm/$sampleName")
        val input = sample.readBytes()
        val audio = NcmDecoder.decrypt(input)
        val actual = MessageDigest.getInstance("SHA-256").digest(audio.bytes).joinToString("") {
            "%02x".format(it)
        }
        assertEquals(expectedSha256, actual, "sha256 mismatch for $sampleName")
        assertEquals("mp3", audio.format, "NCM should decrypt to MP3")
    }
}
