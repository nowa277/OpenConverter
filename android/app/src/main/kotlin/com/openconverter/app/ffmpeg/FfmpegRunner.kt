package com.openconverter.app.ffmpeg

interface FfmpegRunner {
    suspend fun probeDurationMs(path: String): Long
    suspend fun execute(
        input: String,
        output: String,
        format: String,
        bitrate: String?,
        totalDurationMs: Long = 0L,
        onProgress: (percent: Int) -> Unit = {},
        metadata: Map<String, String> = emptyMap(),
        coverPath: String? = null,
        copyAudio: Boolean = false,
    ): Result<Unit>
}

/**
 * Pure command-line argv builder. Mirrors src/main/ffmpeg.js flag selection so
 * Android and desktop transcode behavior stay aligned.
 *
 * Codec choices are explicit (no auto-detect) for reproducibility: the same
 * input + format always yields the same codec and the same args (modulo bitrate).
 */
object FfmpegArgs {
    private val COVER_CAPABLE = setOf("mp3", "flac", "m4a")

    fun build(
        input: String,
        output: String,
        format: String,
        bitrate: String?,
        metadata: Map<String, String> = emptyMap(),
        coverPath: String? = null,
        copyAudio: Boolean = false,
    ): List<String> {
        require(input != output) { "ffmpeg refuses input==output; engine must use a temp output path" }
        val fmt = format.lowercase()
        val wantCover = fmt in COVER_CAPABLE
        val hasExternalCover = wantCover && !coverPath.isNullOrBlank()
        val args = mutableListOf("-y", "-i", input)
        if (hasExternalCover) args += listOf("-i", coverPath!!)

        when {
            hasExternalCover -> args += listOf("-map", "0:a:0", "-map", "1:v:0", "-c:v", "copy", "-disposition:v:0", "attached_pic")
            wantCover -> args += listOf("-map", "0:a:0", "-map", "0:v?", "-c:v", "copy", "-disposition:v:0", "attached_pic")
            else -> args += "-vn"
        }

        if (copyAudio) {
            args += listOf("-c:a", "copy")
        } else {
            val codec = codecFor(fmt)
            args += listOf("-codec:a", codec)
            if (bitrate != null && fmt != "wav" && fmt != "flac") {
                args += listOf("-b:a", bitrate)
            }
        }

        if (fmt == "mp3") args += listOf("-id3v2_version", "3")
        args += listOf("-map_metadata", "0")
        for ((k, v) in metadata) {
            if (v.isNotEmpty()) args += listOf("-metadata", "$k=$v")
        }
        args += output
        return args
    }

    private fun codecFor(format: String): String = when (format) {
        "mp3"  -> "libmp3lame"
        "flac" -> "flac"
        "wav"  -> "pcm_s16le"
        "m4a"  -> "aac"
        "ogg"  -> "libvorbis"
        else   -> throw IllegalArgumentException("Unsupported target format: $format")
    }
}
