package com.openconverter.app.engine

import com.openconverter.app.ffmpeg.FfmpegRunner

/** Records every fs call so tests can assert on the per-file pipeline. */
class FakeFileSystemPort(
    private val reads: Map<String, ByteArray> = emptyMap(),
    private val readErrors: Map<String, Throwable> = emptyMap(),
) : FileSystemPort {
    val cache: MutableMap<String, ByteArray> = mutableMapOf()
    val writes: MutableList<Triple<String, String, ByteArray>> = mutableListOf() // (folder, displayName, bytes)
    val cleanups: MutableList<String> = mutableListOf()

    override fun readBytes(uri: String): ByteArray {
        readErrors[uri]?.let { throw it }
        return reads[uri] ?: throw NoSuchElementException("FakeFileSystemPort: no read for $uri")
    }
    override fun cacheFile(name: String, bytes: ByteArray): String {
        val path = "/cache/$name"
        cache[path] = bytes
        return path
    }
    override fun cachePath(name: String): String = "/cache/$name"
    override fun readCache(path: String): ByteArray =
        cache[path] ?: throw NoSuchElementException("FakeFileSystemPort: cache miss $path")
    override fun writeOutput(folderUri: String, displayName: String, mime: String, bytes: ByteArray): String {
        writes += Triple(folderUri, displayName, bytes)
        return "$folderUri/$displayName"
    }
    override fun cleanup(path: String) {
        cleanups += path
        cache.remove(path)
    }
}

/**
 * Records every ffmpeg call. The behavior closure decides success/failure;
 * on success we materialize `outputBytes` into the shared [FakeFileSystemPort.cache]
 * so the engine's downstream `readCache(outPath)` works.
 */
class FakeFfmpegRunner(
    private val fs: FakeFileSystemPort? = null,
    private val outputBytes: ByteArray = byteArrayOf(0x49, 0x44, 0x33),
    private val behavior: suspend (String, String, String, String?) -> Result<Unit> = { _, _, _, _ -> Result.success(Unit) },
    var probeDurationMsReturn: Long = 0L,
    var lastExecutedTotalDurationMs: Long? = null,
    private val executeResult: Result<Unit> = Result.success(Unit),
) : FfmpegRunner {
    data class Call(val input: String, val output: String, val format: String, val bitrate: String?)
    val calls: MutableList<Call> = mutableListOf()

    override suspend fun probeDurationMs(path: String): Long = probeDurationMsReturn
    override suspend fun execute(
        input: String,
        output: String,
        format: String,
        bitrate: String?,
        totalDurationMs: Long,
        onProgress: (percent: Int) -> Unit,
    ): Result<Unit> {
        lastExecutedTotalDurationMs = totalDurationMs
        calls += Call(input, output, format, bitrate)
        onProgress(50)
        val r = if (executeResult != Result.success(Unit)) executeResult else behavior(input, output, format, bitrate)
        if (r.isSuccess) {
            fs?.cache?.set(output, outputBytes)
            onProgress(100)
        }
        return r
    }
}

/** Records every progress event. */
class RecordingProgressSink : ProgressSink {
    val events: MutableList<String> = mutableListOf()
    override fun onFileStart(index: Int, total: Int, name: String) { events += "start $index/$total $name" }
    override fun onFileProgress(index: Int, percent: Int) { events += "prog $index $percent" }
    override fun onFileDone(index: Int, outputPath: String) { events += "done $index $outputPath" }
    override fun onFileError(index: Int, message: String) { events += "err $index $message" }
}
