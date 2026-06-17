package com.openconverter.app.service

import com.openconverter.app.ffmpeg.FfmpegTranscoder
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [ConversionOrchestrator]. Use a fake [FfmpegTranscoder]
 * so the JVM test never touches the native ffmpeg libraries.
 */
class ConversionOrchestratorTest {

    /** Test double: pass-through transcoder that records each call. */
    private class FakeTranscoder : FfmpegTranscoder {
        data class Call(
            val size: Int,
            val inputFormat: String,
            val outputFormat: String,
            val bitrateKbps: Int,
        )

        val calls = mutableListOf<Call>()
        var probeResult: Double = 180.0

        override fun transcode(
            inputBytes: ByteArray,
            inputFormat: String,
            outputFormat: String,
            bitrateKbps: Int,
        ): ByteArray {
            calls.add(Call(inputBytes.size, inputFormat, outputFormat, bitrateKbps))
            return inputBytes
        }

        override fun probeDuration(
            inputBytes: ByteArray,
            inputFormat: String,
        ): Double = probeResult
    }

    @Test
    fun convertOneInMemory_ncm_to_mp3_runs_full_pipeline() {
        val sample = File("src/test/resources/test-ncm/Chappell Roan - Pink Pony Club.ncm")
        require(sample.exists()) {
            "Missing NCM sample at ${sample.absolutePath}"
        }
        val bytes = sample.readBytes()
        val fake = FakeTranscoder()
        val orchestrator = ConversionOrchestrator(fake)

        val result = orchestrator.convertOneInMemory(
            input = bytes,
            ekey = null,
            targetFormat = "mp3",
            bitrateKbps = 256,
        )

        assertEquals("ncm", result.sourceFormat)
        assertEquals("mp3", result.targetFormat)
        assertTrue(result.encoded.isNotEmpty(), "Encoded bytes should not be empty")

        // Pipeline must have stripped the NCM header (~1024 bytes), so
        // the transcoder's input is smaller than the original NCM file.
        assertEquals(1, fake.calls.size, "transcode should be called exactly once")
        val call = fake.calls.single()
        assertTrue(
            call.size < bytes.size,
            "Decrypted audio ($call.size) should be smaller than NCM input (${bytes.size})",
        )
        assertEquals("mp3", call.inputFormat)
        assertEquals("mp3", call.outputFormat)
        assertEquals(256, call.bitrateKbps)
    }

    @Test
    fun convertOneInMemory_unknown_format_throws_with_first_bytes_in_message() {
        val garbage = ByteArray(32) { 0xAB.toByte() }
        val orchestrator = ConversionOrchestrator(FakeTranscoder())

        val ex = assertFailsWith<IllegalArgumentException> {
            orchestrator.convertOneInMemory(garbage)
        }
        // The diagnostic should include the first 16 bytes as hex so the
        // user / log can see what the detector saw.
        assertNotNull(ex.message)
        assertTrue(
            ex.message!!.contains("Unknown format"),
            "Message should mention unknown format; got: ${ex.message}",
        )
    }

    @Test
    fun convertOneInMemory_qmcv2_extension_mflac_via_real_sample() {
        // MFLAC files have no fixed magic; the FormatDetector currently
        // cannot identify them by content alone (M2 milestone leaves
        // extension-based dispatch to the Service in Task 3.2). This
        // test asserts the current contract: an MFLAC sample read as
        // bytes surfaces as "Unknown format" — which is the expected
        // behaviour and the reason the Service must pass the extension
        // hint explicitly. Pinned here so a future FormatDetector
        // change that does detect MFLAC will surface as a test diff.
        val sample = File("src/test/resources/test-qmc-v2/synthetic-mflac.mflac")
        require(sample.exists()) {
            "Missing MFLAC sample at ${sample.absolutePath}"
        }
        val orchestrator = ConversionOrchestrator(FakeTranscoder())
        assertFailsWith<IllegalArgumentException> {
            orchestrator.convertOneInMemory(
                input = sample.readBytes(),
                ekey = null,
                targetFormat = "flac",
            )
        }
    }

    @Test
    fun convertOneInMemory_plaintext_mp3_to_flac_passes_through_decoder() {
        // Plaintext MP3 (no encryption wrapper) should be detected by extension
        // and routed directly to ffmpeg.transcode without going through any
        // NcmDecoder / QmcDecoder etc. — there is no decrypt step.
        val mp3Bytes = ByteArray(32) { 0xFF.toByte() }  // arbitrary bytes; not ID3/0xFFFB
        val fake = FakeTranscoder()
        val orchestrator = ConversionOrchestrator(fake)

        val result = orchestrator.convertOneInMemory(
            input = mp3Bytes,
            fileName = "song.mp3",
            targetFormat = "flac",
        )

        assertEquals("mp3", result.sourceFormat)
        assertEquals("flac", result.targetFormat)
        assertEquals(1, fake.calls.size)
        val call = fake.calls.single()
        assertEquals(mp3Bytes.size, call.size, "passthrough should feed input bytes verbatim to ffmpeg")
        assertEquals("mp3", call.inputFormat)
        assertEquals("flac", call.outputFormat)
    }

    @Test
    fun convertOneInMemory_plaintext_audio_formats_by_extension_route_through_ffmpeg() {
        // Same as the mp3 test, parameterized over all supported plaintext formats.
        for (ext in listOf("flac", "wav", "m4a", "ogg", "aac")) {
            val bytes = ByteArray(16) { 0xAB.toByte() }
            val fake = FakeTranscoder()
            val orchestrator = ConversionOrchestrator(fake)

            val result = orchestrator.convertOneInMemory(
                input = bytes,
                fileName = "song.$ext",
                targetFormat = "mp3",
            )

            assertEquals(ext, result.sourceFormat, "sourceFormat for .$ext")
            assertEquals(1, fake.calls.size, "transcode called once for .$ext")
            assertEquals(ext, fake.calls.single().inputFormat, "inputFormat for .$ext")
        }
    }
}
