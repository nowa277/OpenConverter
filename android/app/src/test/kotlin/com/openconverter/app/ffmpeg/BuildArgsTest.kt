package com.openconverter.app.ffmpeg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildArgsTest {
    @Test fun mp3_with_bitrate() {
        val a = FfmpegArgs.build(input = "/in/a.flac", output = "/out/a.mp3", format = "mp3", bitrate = "320k")
        assertEquals(listOf("-y", "-i", "/in/a.flac", "-codec:a", "libmp3lame", "-b:a", "320k", "/out/a.mp3"), a)
    }

    @Test fun flac_lossless_no_bitrate() {
        val a = FfmpegArgs.build("/in/a.wav", "/out/a.flac", "flac", bitrate = null)
        assertEquals(listOf("-y", "-i", "/in/a.wav", "-codec:a", "flac", "/out/a.flac"), a)
    }

    @Test fun wav_pcm_s16le() {
        val a = FfmpegArgs.build("/in/a.mp3", "/out/a.wav", "wav", bitrate = null)
        assertEquals(listOf("-y", "-i", "/in/a.mp3", "-codec:a", "pcm_s16le", "/out/a.wav"), a)
    }

    @Test fun m4a_aac() {
        val a = FfmpegArgs.build("/in/a.flac", "/out/a.m4a", "m4a", bitrate = "256k")
        assertEquals(listOf("-y", "-i", "/in/a.flac", "-codec:a", "aac", "-b:a", "256k", "/out/a.m4a"), a)
    }

    @Test fun ogg_vorbis() {
        val a = FfmpegArgs.build("/in/a.mp3", "/out/a.ogg", "ogg", bitrate = "192k")
        assertEquals(listOf("-y", "-i", "/in/a.mp3", "-codec:a", "libvorbis", "-b:a", "192k", "/out/a.ogg"), a)
    }

    @Test fun unknown_format_throws() {
        var threw = false
        try { FfmpegArgs.build("/in/a.mp3", "/out/a.xyz", "xyz", null) }
        catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw)
    }
}
