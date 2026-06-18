package com.openconverter.app.ffmpeg

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.arthenica.ffmpegkit.StatisticsCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Production [FfmpegRunner]. Each `execute` call:
 *   1. Installs a process-global [StatisticsCallback] that turns ffmpeg's
 *      `time=` log into a 0..100 progress (best-effort against
 *      [setTotalDurationMs]; without a duration, reports a 50% heartbeat
 *      until completion).
 *   2. Runs `FFmpegKit.executeAsync` with the args from [FfmpegArgs.build].
 *   3. Resumes the coroutine with success/failure based on the return code.
 *   4. `invokeOnCancellation` cancels the ffmpeg session.
 */
class FfmpegKitRunner : FfmpegRunner {

    @Volatile private var totalDurationMs: Long = 0L

    /** Called by the engine BEFORE execute() if it knows the duration. */
    fun setTotalDurationMs(ms: Long) { totalDurationMs = ms.coerceAtLeast(0L) }

    override suspend fun execute(
        input: String,
        output: String,
        format: String,
        bitrate: String?,
        onProgress: (percent: Int) -> Unit,
    ): Result<Unit> = suspendCancellableCoroutine { cont ->
        val args = FfmpegArgs.build(input, output, format, bitrate).joinToString(" ") {
            // ffmpeg-kit takes a single command string; quote args with spaces.
            if (it.contains(' ')) "\"$it\"" else it
        }

        val statsCallback = StatisticsCallback { stats: Statistics ->
            val total = totalDurationMs
            val pct = if (total > 0) {
                (stats.time * 100L / total).toInt().coerceIn(0, 99)
            } else {
                50 // heartbeat
            }
            onProgress(pct)
        }
        FFmpegKitConfig.enableStatisticsCallback(statsCallback)

        val session = FFmpegKit.executeAsync(args) { sess ->
            FFmpegKitConfig.enableStatisticsCallback(null)
            val rc = sess.returnCode
            when {
                ReturnCode.isSuccess(rc) -> {
                    onProgress(100)
                    cont.resume(Result.success(Unit))
                }
                ReturnCode.isCancel(rc) -> {
                    cont.resume(Result.failure(InterruptedException("ffmpeg cancelled")))
                }
                else -> {
                    cont.resume(
                        Result.failure(RuntimeException("ffmpeg rc=$rc\n${sess.allLogsAsString.takeLast(2000)}"))
                    )
                }
            }
        }

        cont.invokeOnCancellation {
            FFmpegKit.cancel(session.sessionId)
            FFmpegKitConfig.enableStatisticsCallback(null)
        }
    }
}
