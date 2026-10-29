package com.openconverter.app.ffmpeg

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class FfmpegKitRunner(
    private val startSession: (Array<String>, (Result<Unit>) -> Unit, (Double) -> Unit) -> Long = { args, done, stats ->
        FFmpegKit.executeWithArgumentsAsync(args, { session ->
            val code = session.returnCode
            done(when {
                ReturnCode.isSuccess(code) -> Result.success(Unit)
                ReturnCode.isCancel(code) -> Result.failure(InterruptedException("ffmpeg cancelled"))
                else -> Result.failure(IllegalStateException("ffmpeg rc=$code\n${session.allLogsAsString.takeLast(2000)}"))
            })
        }, null, { stats(it.time) }).sessionId
    },
    private val cancelSession: (Long) -> Unit = { FFmpegKit.cancel(it) },
) : FfmpegRunner {
    override suspend fun probeDurationMs(path: String): Long {
        currentCoroutineContext().ensureActive()
        val duration = FFprobeKit.getMediaInformation(path).mediaInformation
            ?.duration?.toDoubleOrNull()?.times(1000)?.toLong() ?: 0L
        currentCoroutineContext().ensureActive()
        return duration
    }

    override suspend fun execute(
        input: String,
        output: String,
        format: String,
        bitrate: String?,
        totalDurationMs: Long,
        onProgress: (percent: Int) -> Unit,
        metadata: Map<String, String>,
        coverPath: String?,
        copyAudio: Boolean,
        metadataFile: String?,
    ): Result<Unit> {
        currentCoroutineContext().ensureActive()
        val args = FfmpegArgs.build(
            input, output, format, bitrate,
            metadata = metadata, coverPath = coverPath, copyAudio = copyAudio,
            metadataFile = metadataFile,
        ).toTypedArray()
        val completed = CompletableDeferred<Unit>()
        val finished = AtomicBoolean(false)
        try {
            return suspendCancellableCoroutine { cont ->
                val sessionId = startSession(args, { outcome ->
                    if (!finished.compareAndSet(false, true)) return@startSession
                    try {
                        if (cont.isActive) {
                            if (outcome.isSuccess) onProgress(100)
                            cont.resume(outcome)
                        }
                    } finally {
                        completed.complete(Unit)
                    }
                }, { time ->
                    if (cont.isActive && !finished.get()) {
                        val percent = if (totalDurationMs > 0) {
                            (time * 100 / totalDurationMs).toInt().coerceIn(0, 99)
                        } else 50
                        onProgress(percent)
                    }
                })
                cont.invokeOnCancellation {
                    if (!finished.get()) cancelSession(sessionId)
                }
            }
        } catch (cancelled: CancellationException) {
            // cancel(sessionId) returns before native code closes its input/output.
            withContext(NonCancellable) { completed.await() }
            throw cancelled
        }
    }
}
