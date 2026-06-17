package com.openconverter.app.decoders

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatDetectorTest {
    @Test
    fun detects_ncm_by_magic() {
        val bytes = "CTENFDAM".toByteArray(Charsets.US_ASCII) + ByteArray(8)
        assertEquals("ncm", FormatDetector.detect(bytes))
    }

    @Test
    fun detects_vpr_by_magic() {
        val vprMagic = byteArrayOf(
            0x05, 0x28, 0xBC.toByte(), 0x96.toByte(),
            0xE9.toByte(), 0xE4.toByte(), 0x5A, 0x43,
            0x91.toByte(), 0xAA.toByte(), 0xBD.toByte(), 0xD0.toByte(),
            0x7A, 0xF5.toByte(), 0x36, 0x31
        )
        assertEquals("vpr", FormatDetector.detect(vprMagic))
    }

    @Test
    fun detects_kwm_by_magic() {
        val bytes = "yeelion-kuwo".toByteArray(Charsets.US_ASCII) + ByteArray(4)
        assertEquals("kwm", FormatDetector.detect(bytes))
    }

    @Test
    fun detects_kgm_magic_disambiguated_by_extension() {
        val kgmMagic = byteArrayOf(
            0x7C, 0xD5.toByte(), 0x32, 0xEB.toByte(),
            0x86.toByte(), 0x02, 0x7F, 0x4B,
            0xA8.toByte(), 0xAF.toByte(), 0xA6.toByte(), 0x8E.toByte(),
            0x0F, 0xFF.toByte(), 0x99.toByte(), 0x14
        )
        assertEquals("kgm", FormatDetector.detect(kgmMagic, "song.kgm"))
        assertEquals("kgma", FormatDetector.detect(kgmMagic, "song.kgma"))
    }

    @Test
    fun kgm_magic_without_extension_defaults_to_kgma() {
        val kgmMagic = byteArrayOf(
            0x7C, 0xD5.toByte(), 0x32, 0xEB.toByte(),
            0x86.toByte(), 0x02, 0x7F, 0x4B,
            0xA8.toByte(), 0xAF.toByte(), 0xA6.toByte(), 0x8E.toByte(),
            0x0F, 0xFF.toByte(), 0x99.toByte(), 0x14
        )
        assertEquals("kgma", FormatDetector.detect(kgmMagic, null))
    }

    @Test
    fun detects_qmc0_by_extension() {
        // headerless: no magic, must use extension
        val bytes = ByteArray(16) { 0xFF.toByte() }  // noise
        assertEquals("qmc0", FormatDetector.detect(bytes, "song.qmc0"))
        assertEquals("qmc0", FormatDetector.detect(null, "song.qmc0"))
    }

    @Test
    fun detects_qmc3_by_extension() {
        assertEquals("qmc3", FormatDetector.detect(null, "song.qmc3"))
    }

    @Test
    fun detects_qmcflac_by_extension() {
        assertEquals("qmcflac", FormatDetector.detect(null, "song.qmcflac"))
    }

    @Test
    fun detects_qmcogg_by_extension() {
        assertEquals("qmcogg", FormatDetector.detect(null, "song.qmcogg"))
    }

    @Test
    fun detects_mflac_by_extension() {
        assertEquals("mflac", FormatDetector.detect(null, "song.mflac"))
    }

    @Test
    fun detects_mflac0_by_extension() {
        assertEquals("mflac0", FormatDetector.detect(null, "song.mflac0"))
    }

    @Test
    fun detects_mgg_by_extension() {
        assertEquals("mgg", FormatDetector.detect(null, "song.mgg"))
    }

    @Test
    fun detects_mgg1_by_extension() {
        assertEquals("mgg1", FormatDetector.detect(null, "song.mgg1"))
    }

    @Test
    fun detects_bkc_by_extension() {
        assertEquals("bkc", FormatDetector.detect(null, "song.bkc"))
    }

    @Test
    fun detects_bkc_variants_by_extension() {
        for (ext in listOf("bkcmp3", "bkcflac", "bkcogg",
                          "bkcm4a", "bkcwav", "bkcwma", "bkcape")) {
            assertEquals(ext, FormatDetector.detect(null, "song.$ext"),
                "Failed for ext=$ext")
        }
    }

    @Test
    fun extension_is_case_insensitive() {
        assertEquals("qmc0", FormatDetector.detect(null, "song.QMC0"))
        assertEquals("mflac", FormatDetector.detect(null, "song.MFLAC"))
    }

    @Test
    fun returns_null_for_unknown_bytes_and_no_extension() {
        val bytes = "UNKNOWN1234567".toByteArray(Charsets.US_ASCII)
        assertNull(FormatDetector.detect(bytes, null))
    }

    @Test
    fun returns_null_for_unknown_extension() {
        // Truly unknown extension (not even plaintext audio) — should still return null.
        // Plaintext audio (mp3/flac/wav/m4a/ogg/aac) is now detected as passthrough
        // so FormatDetector does not gate it; ConversionOrchestrator routes plaintext
        // bytes through ffmpeg directly without a decrypt step.
        assertNull(FormatDetector.detect(null, "song.xyz"))
        assertNull(FormatDetector.detect(null, "song.bin"))
    }

    @Test
    fun detects_mp3_by_extension_as_passthrough() {
        assertEquals("mp3", FormatDetector.detect(null, "song.mp3"))
        assertEquals("mp3", FormatDetector.detect(ByteArray(32) { 0xFF.toByte() }, "song.mp3"))
    }

    @Test
    fun detects_plaintext_audio_formats_by_extension_as_passthrough() {
        for (ext in listOf("flac", "wav", "m4a", "ogg", "aac")) {
            assertEquals(
                ext,
                FormatDetector.detect(null, "song.$ext"),
                "Failed for ext=$ext",
            )
        }
    }

    @Test
    fun plaintext_audio_extension_is_case_insensitive() {
        assertEquals("mp3", FormatDetector.detect(null, "song.MP3"))
        assertEquals("flac", FormatDetector.detect(null, "song.FLAC"))
    }

    @Test
    fun returns_null_for_empty() {
        assertNull(FormatDetector.detect(ByteArray(0)))
        assertNull(FormatDetector.detect(null, null))
    }

    @Test
    fun returns_null_for_too_short() {
        assertNull(FormatDetector.detect(ByteArray(4)))
    }

    @Test
    fun handles_filename_without_extension() {
        // "song" with no dot — should fall through to magic-only check
        val ncmBytes = "CTENFDAM".toByteArray(Charsets.US_ASCII) + ByteArray(8)
        assertEquals("ncm", FormatDetector.detect(ncmBytes, "song"))
    }
}
