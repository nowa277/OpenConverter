package com.openconverter.app.decoders

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class QmcDecoderTest(
    private val sampleName: String,
    private val expectedSha256: String,
    private val expectedFormat: String,
) {

    companion object {
        // synthetic-{variant}.qmc{0,flac,ogg} → expected plaintext format
        private val FORMAT_BY_SAMPLE = mapOf(
            "synthetic-qmc0.qmc0" to "mp3",
            "synthetic-qmcflac.qmcflac" to "flac",
            "synthetic-qmcogg.qmcogg" to "ogg",
        )

        @JvmStatic
        @Parameters(name = "{0}")
        fun data(): List<Array<Any>> {
            val samplesDir = File("src/test/resources/test-qmc")
            val samples = samplesDir.listFiles { f ->
                f.name.startsWith("synthetic-") &&
                    (f.extension == "qmc0" || f.extension == "qmcflac" || f.extension == "qmcogg")
            } ?: error("No synthetic QMC samples in ${samplesDir.absolutePath}")
            val expectedFile = File("src/test/resources/test-qmc/expected-sha256.json")
            val expectedJson = expectedFile.readText()
            val expectedMap = mutableMapOf<String, String>()
            // Match {"synthetic-X.Y": "sha"} where Y is qmc0|qmcflac|qmcogg
            val regex = Regex("\"([^\"]+\\.(qmc0|qmcflac|qmcogg))\"\\s*:\\s*\"([a-f0-9]{64})\"")
            regex.findAll(expectedJson).forEach { m ->
                expectedMap[m.groupValues[1]] = m.groupValues[3]
            }
            return samples
                .filter { expectedMap.containsKey(it.name) && FORMAT_BY_SAMPLE.containsKey(it.name) }
                .sortedBy { it.name }
                .map { f ->
                    arrayOf<Any>(
                        f.name as Any,
                        expectedMap[f.name]!! as Any,
                        FORMAT_BY_SAMPLE[f.name]!! as Any,
                    )
                }
        }
    }

    @Test
    fun decrypts_synthetic_qmc_to_byte_equivalent_of_desktop() {
        val sample = File("src/test/resources/test-qmc/$sampleName")
        val input = sample.readBytes()
        val audio = QmcDecoder.decrypt(input)
        val actual = MessageDigest.getInstance("SHA-256").digest(audio.bytes).joinToString("") {
            "%02x".format(it)
        }
        assertEquals(expectedSha256, actual, "sha256 mismatch for $sampleName")
        assertEquals(expectedFormat, audio.format, "format hint wrong for $sampleName")
    }
}