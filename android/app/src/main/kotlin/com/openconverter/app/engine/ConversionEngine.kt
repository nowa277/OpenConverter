package com.openconverter.app.engine

import android.net.Uri
import com.openconverter.app.log.OCLog
import com.openconverter.app.service.ConversionService.Progress
import com.openconverter.app.ui.history.HistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Pure-Kotlin per-file conversion loop. The Service hands in everything
 * the loop needs (resolver facade, orchestrator adapter, ekey, history,
 * failure log, clock) so this class is unit-testable in plain JUnit. It
 * does not touch android.app.Service, android.content.Context, or any
 * Android lifecycle API directly.
 */
class ConversionEngine(
    private val resolver: ContentResolverFacade,
    private val orchestrator: Orchestrator,
    private val ekey: EkeyProvider,
    private val failureSink: FailureSink,
    private val historySink: HistorySink,
    private val clock: Clock,
) {
    /**
     * Process [uris] sequentially. For each uri: read → orchestrator →
     * createDocument → write. Records every step in [historySink] and
     * [failureSink], emits progress to [progressSink]. Returns aggregate
     * counts.
     */
    fun convertAll(
        uris: List<Uri>,
        targetFormat: String,
        folderUri: Uri?,
        progressSink: MutableStateFlow<Progress>,
    ): EngineResult {
        progressSink.value = Progress.Idle
        var ok = 0
        var fail = 0

        uris.forEachIndexed { i, uri ->
            val pathSegment = uri.lastPathSegment ?: "file_$i"
            OCLog.i("engine.file.start", "i" to i, "total" to uris.size, "uri" to uri.toString())
            progressSink.value = Progress.Processing(i, uris.size, pathSegment)

            val input = runCatching { resolver.read(uri) }.getOrNull()
            if (input == null) {
                recordFailure(uri, pathSegment, "读取失败", targetFormat, null); fail++
                return@forEachIndexed
            }

            val displayName = resolver.queryDisplayName(uri)
            // Prefer the SAF display name (what the user sees in their picker)
            // over the URI path segment for History/Failure records and for
            // the output stem. Falls back to the path segment when SAF didn't
            // expose a display name.
            val filename = displayName ?: pathSegment
            val result = runCatching {
                orchestrator.convertOneInMemory(
                    input = input,
                    fileName = displayName,
                    ekey = ekey.getEkey(),
                    targetFormat = targetFormat,
                    bitrateKbps = 256,
                )
            }
            if (result.isFailure) {
                val err = result.exceptionOrNull()?.message ?: "未知错误"
                recordFailure(uri, filename, err, targetFormat, result.exceptionOrNull()); fail++
                return@forEachIndexed
            }

            val sourceStem = displayName?.substringBeforeLast('.', displayName) ?: "output_$i"
            val outName = "$sourceStem.$targetFormat"
            val outUri = if (folderUri != null) {
                runCatching { resolver.createDocument(folderUri, outName, mimeFor(targetFormat)) }.getOrNull()
            } else null
            if (outUri == null) {
                recordFailure(uri, filename, "无法创建输出文件", targetFormat, null); fail++
                return@forEachIndexed
            }

            val writeResult = runCatching { resolver.write(outUri, result.getOrThrow().encoded) }
            if (writeResult.isFailure) {
                val err = "保存失败: ${writeResult.exceptionOrNull()?.message}"
                recordFailure(uri, filename, err, targetFormat, null); fail++
                return@forEachIndexed
            }

            ok++
            historySink.record(
                HistoryEntry(
                    timestampMs = clock.nowMs(),
                    sourceName = filename,
                    targetFormat = targetFormat,
                    status = "DONE",
                    outputUri = outUri,
                    sourceUri = uri,
                )
            )
            progressSink.value = Progress.Done(i + 1, uris.size, filename)
        }

        OCLog.i("engine.complete",
            "total" to uris.size, "ok" to ok, "fail" to fail)
        return EngineResult(ok = ok, fail = fail, total = uris.size)
    }

    private fun recordFailure(
        uri: Uri,
        filename: String,
        error: String,
        targetFormat: String,
        cause: Throwable?,
    ) {
        failureSink.record(uri.toString(), filename, error)
        historySink.record(
            HistoryEntry(
                timestampMs = clock.nowMs(),
                sourceName = filename,
                targetFormat = targetFormat,
                status = "FAILED",
                errorMessage = error,
                sourceUri = uri,
            )
        )
        OCLog.e("engine.file.fail", cause, "uri" to uri.toString(), "err" to error)
    }

    private fun mimeFor(format: String): String = when (format) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        else -> "application/octet-stream"
    }
}
