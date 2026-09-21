package com.openconverter.app.ffmpeg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildArgsTest {
    @Test fun mp3_with_bitrate() {
        val a = FfmpegArgs.build(input = "/in/a.flac", output = "/out/a.mp3", format = "mp3", bitrate = "320k")
        assertEquals(
            listOf(
                "-y", "-i", "/in/a.flac",
                "-map", "0:a:0", "-map", "0:v?", "-c:v", "copy", "-disposition:v:0", "attached_pic",
                "-codec:a", "libmp3lame", "-b:a", "320k",
                "-id3v2_version", "3",
                "-map_metadata", "0",
                "/out/a.mp3",
            ),
            a,
        )
    }

    @Test fun flac_lossless_no_bitrate() {
        val a = FfmpegArgs.build("/in/a.wav", "/out/a.flac", "flac", bitrate = null)
        assertEquals(
            listOf(
                "-y", "-i", "/in/a.wav",
                "-map", "0:a:0", "-map", "0:v?", "-c:v", "copy", "-disposition:v:0", "attached_pic",
                "-codec:a", "flac",
                "-map_metadata", "0",
                "/out/a.flac",
            ),
            a,
        )
    }

    @Test fun wav_pcm_s16le() {
        val a = FfmpegArgs.build("/in/a.mp3", "/out/a.wav", "wav", bitrate = null)
        assertEquals(
            listOf(
                "-y", "-i", "/in/a.mp3",
                "-vn",
                "-codec:a", "pcm_s16le",
                "-map_metadata", "0",
                "/out/a.wav",
            ),
            a,
        )
    }

    @Test fun m4a_aac() {
        val a = FfmpegArgs.build("/in/a.flac", "/out/a.m4a", "m4a", bitrate = "256k")
        assertEquals(
            listOf(
                "-y", "-i", "/in/a.flac",
                "-map", "0:a:0", "-map", "0:v?", "-c:v", "copy", "-disposition:v:0", "attached_pic",
                "-codec:a", "aac", "-b:a", "256k",
                "-map_metadata", "0",
                "/out/a.m4a",
            ),
            a,
        )
    }

    @Test fun ogg_vorbis() {
        val a = FfmpegArgs.build("/in/a.mp3", "/out/a.ogg", "ogg", bitrate = "192k")
        assertEquals(
            listOf(
                "-y", "-i", "/in/a.mp3",
                "-vn",
                "-codec:a", "libvorbis", "-b:a", "192k",
                "-map_metadata", "0",
                "/out/a.ogg",
            ),
            a,
        )
    }

    @Test fun unknown_format_throws() {
        var threw = false
        try { FfmpegArgs.build("/in/a.mp3", "/out/a.xyz", "xyz", null) }
        catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw)
    }

    @Test fun mp3_copy_with_tags_and_cover() {
        val a = FfmpegArgs.build(
            input = "/in/a.mp3",
            output = "/out/a.mp3",
            format = "mp3",
            bitrate = null,
            metadata = mapOf("title" to "Hello", "artist" to "A / B", "album" to "X"),
            coverPath = "/cache/cover.jpg",
            copyAudio = true,
        )
        assertEquals(
            listOf(
                "-y", "-i", "/in/a.mp3", "-i", "/cache/cover.jpg",
                "-map", "0:a:0", "-map", "1:v:0", "-c:v", "copy", "-disposition:v:0", "attached_pic",
                "-c:a", "copy",
                "-id3v2_version", "3",
                "-map_metadata", "0",
                "-metadata", "title=Hello",
                "-metadata", "artist=A / B",
                "-metadata", "album=X",
                "/out/a.mp3",
            ),
            a,
        )
    }

    @Test fun wav_strips_video_and_ignores_cover() {
        val a = FfmpegArgs.build(
            "/in/a.mp3", "/out/a.wav", "wav", null,
            metadata = mapOf("title" to "Hello"),
            coverPath = "/cache/cover.jpg",
            copyAudio = false,
        )
        assertTrue(a.contains("-vn"))
        assertTrue("-i" !in a.drop(3)) // only the audio input
        assertTrue(a.containsAll(listOf("-metadata", "title=Hello")))
    }

    @Test fun transcode_mp3_keeps_optional_input_cover_when_no_external() {
        val a = FfmpegArgs.build("/in/a.flac", "/out/a.mp3", "mp3", "320k")
        assertTrue(a.containsAll(listOf("-map", "0:a:0", "-map", "0:v?", "-c:v", "copy", "-disposition:v:0", "attached_pic")))
        assertTrue(a.containsAll(listOf("-id3v2_version", "3", "-map_metadata", "0")))
    }
}
