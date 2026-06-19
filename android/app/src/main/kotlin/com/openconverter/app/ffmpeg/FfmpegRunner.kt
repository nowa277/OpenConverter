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
    fun build(input: String, output: String, format: String, bitrate: String?): List<String> {
        require(input != output) { "ffmpeg refuses input==output; engine must use a temp output path" }
        val codec = codecFor(format)
        val args = mutableListOf("-y", "-i", input, "-codec:a", codec)
        if (bitrate != null && format != "wav" && format != "flac") {
            args += listOf("-b:a", bitrate)
        }
        args += output
        return args
    }

    private fun codecFor(format: String): String = when (format.lowercase()) {
        "mp3"  -> "libmp3lame"
        "flac" -> "flac"
        "wav"  -> "pcm_s16le"
        "m4a"  -> "aac"
        "ogg"  -> "libvorbis"
        else   -> throw IllegalArgumentException("Unsupported target format: $format")
    }
}