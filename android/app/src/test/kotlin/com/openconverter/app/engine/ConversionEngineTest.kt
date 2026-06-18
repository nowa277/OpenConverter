package com.openconverter.app.engine

import com.openconverter.app.decoders.Decoder
import com.openconverter.app.decoders.DecoderRegistry
import com.openconverter.app.decoders.DecryptResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val PLAIN_EXTS = setOf(".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg", ".opus")

private val ID3 = byteArrayOf(0x49, 0x44, 0x33, 0x00, 0x42)
private val FLAC = byteArrayOf(0x66, 0x4c, 0x61, 0x43, 0x42)
private val WAV = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x42)

class ConversionEngineTest {

    /** Test 1: plain mp3 → mp3 with no bitrate ⇒ direct copy, no ffmpeg. */
    @Test fun plain_same_format_direct_copy() = runTest {
        val fs = FakeFileSystemPort(reads = mapOf("uri:a" to ID3))
        val ffmpeg = FakeFfmpegRunner(fs)
        val sink = RecordingProgressSink()
        val engine = ConversionEngine(DecoderRegistry(emptyList()), ffmpeg, fs, sink)

        val req = ConversionRequest(
            listOf("uri:a"), listOf("song.mp3"),
            "mp3", "tree:out", null, PLAIN_EXTS,
        )
        val results = engine.convertAll(req)

        assertEquals(1, results.size)
        assertNull(results[0].error)
        assertTrue("ffmpeg must NOT be called", ffmpeg.calls.isEmpty())
        assertEquals(1, fs.writes.size)
        assertEquals("song.mp3", fs.writes[0].second)
        assertTrue(fs.writes[0].third.contentEquals(ID3))
    }

    /** Test 2: plain flac → mp3 320k ⇒ ffmpeg called once with libmp3lame -b:a 320k. */
    @Test fun plain_transcode_with_bitrate() = runTest {
        val fs = FakeFileSystemPort(reads = mapOf("uri:b" to FLAC))
        val ffmpeg = FakeFfmpegRunner(fs)
        val sink = RecordingProgressSink()
        val engine = ConversionEngine(DecoderRegistry(emptyList()), ffmpeg, fs, sink)

        val req = ConversionRequest(
            listOf("uri:b"), listOf("song.flac"),
            "mp3", "tree:out", "320k", PLAIN_EXTS,
        )
        val results = engine.convertAll(req)

        assertEquals(1, results.size)
        assertNull(results[0].error)
        assertEquals(1, ffmpeg.calls.size)
        assertEquals("mp3", ffmpeg.calls[0].format)
        assertEquals("320k", ffmpeg.calls[0].bitrate)
        assertEquals("/cache/in_0_plain.flac", ffmpeg.calls[0].input)
    }

    /** Test 3: encrypted .ncm → mp3 (sniffed mp3) ⇒ no ffmpeg. */
    @Test fun encrypted_target_matches_sniffed_no_ffmpeg() = runTest {
        val cipher = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val fakeNcm = object : Decoder {
            override val supportedExtensions = setOf(".ncm")
            override fun decrypt(input: ByteArray) = DecryptResult(audio = ID3, format = "mp3")
        }
        val fs = FakeFileSystemPort(reads = mapOf("uri:c" to cipher))
        val ffmpeg = FakeFfmpegRunner(fs)
        val sink = RecordingProgressSink()
        val engine = ConversionEngine(DecoderRegistry(listOf(fakeNcm)), ffmpeg, fs, sink)

        val req = ConversionRequest(
            listOf("uri:c"), listOf("song.ncm"),
            "mp3", "tree:out", null, PLAIN_EXTS,
        )
        val results = engine.convertAll(req)

        assertNull(results[0].error)
        assertTrue(ffmpeg.calls.isEmpty())
        assertEquals(1, fs.writes.size)
        assertTrue(fs.writes[0].third.contentEquals(ID3))
        assertEquals("song.mp3", fs.writes[0].second)
    }

    /** Test 4: encrypted .ncm → flac (sniffed mp3) ⇒ ffmpeg called with format=flac. */
    @Test fun encrypted_then_transcode() = runTest {
        val cipher = byteArrayOf(0xFF.toByte())
        val fakeNcm = object : Decoder {
            override val supportedExtensions = setOf(".ncm")
            override fun decrypt(input: ByteArray) = DecryptResult(audio = ID3, format = "mp3")
        }
        val fs = FakeFileSystemPort(reads = mapOf("uri:d" to cipher))
        val ffmpeg = FakeFfmpegRunner(fs, outputBytes = FLAC)
        val sink = RecordingProgressSink()
        val engine = ConversionEngine(DecoderRegistry(listOf(fakeNcm)), ffmpeg, fs, sink)

        val req = ConversionRequest(
            listOf("uri:d"), listOf("song.ncm"),
            "flac", "tree:out", null, PLAIN_EXTS,
        )
        val results = engine.convertAll(req)

        assertNull(results[0].error)
        assertEquals(1, ffmpeg.calls.size)
        assertEquals("flac", ffmpeg.calls[0].format)
        assertNull(ffmpeg.calls[0].bitrate)
        assertEquals("song.flac", fs.writes[0].second)
    }

    /** Test 5: per-file failure does not abort the batch. */
    @Test fun per_file_failure_continues() = runTest {
        val fs = FakeFileSystemPort(
            reads = mapOf("uri:ok" to ID3),
            readErrors = mapOf("uri:bad" to RuntimeException("boom")),
        )
        val ffmpeg = FakeFfmpegRunner(fs)
        val sink = RecordingProgressSink()
        val engine = ConversionEngine(DecoderRegistry(emptyList()), ffmpeg, fs, sink)

        val req = ConversionRequest(
            listOf("uri:ok", "uri:bad"), listOf("a.mp3", "b.mp3"),
            "mp3", "tree:out", null, PLAIN_EXTS,
        )
        val results = engine.convertAll(req)

        assertEquals(2, results.size)
        assertNull(results[0].error)
        assertNotNull(results[1].error)
        assertTrue(results[1].error!!.contains("boom"))
        // First file must have written; second must not.
        assertEquals(1, fs.writes.size)
    }

    /** Test 6: cancellation mid-file cleans up in-flight temps. */
    @Test fun cancel_cleans_temp_files() = runTest {
        val fs = FakeFileSystemPort(reads = mapOf("uri:cancel" to FLAC))
        val ffmpeg = FakeFfmpegRunner(fs, behavior = { _, _, _, _ ->
            // Simulate slow ffmpeg so the cancel can land.
            delay(10_000); Result.success(Unit)
        })
        val sink = RecordingProgressSink()
        val engine = ConversionEngine(DecoderRegistry(emptyList()), ffmpeg, fs, sink)

        val req = ConversionRequest(
            listOf("uri:cancel"), listOf("a.flac"),
            "mp3", "tree:out", "320k", PLAIN_EXTS,
        )
        var threw = false
        // supervisorScope absorbs the child failure so the test scope itself doesn't fail.
        supervisorScope {
            val job = launch {
                try { engine.convertAll(req) }
                catch (ce: CancellationException) { threw = true; throw ce }
            }
            // Wait until the engine has called ffmpeg (which means the cache file is written).
            while (ffmpeg.calls.isEmpty()) delay(1)
            job.cancel()
            job.join()
        }
        assertTrue("CancellationException must propagate", threw)
        // /cache/in_0_plain.flac must have been registered for cleanup.
        assertTrue("at least one cleanup call expected", fs.cleanups.isNotEmpty())
    }
}
