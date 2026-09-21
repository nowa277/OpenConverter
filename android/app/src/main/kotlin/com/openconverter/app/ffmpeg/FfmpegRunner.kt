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
        metadataFile: String? = null,
    ): Result<Unit>
}
