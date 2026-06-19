package com.openconverter.app.ffmpeg

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.Level
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.arthenica.ffmpegkit.StatisticsCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Production [FfmpegRunner]. Each `execute` call:
 *   1. Runs `FFmpegKit.executeAsync` with the args from [FfmpegArgs.build].
 *   2. The stats callback turns ffmpeg's `time=` log into a 0..99 progress
 *      when [totalDurationMs] > 0, else a 50% heartbeat.
 *   3. Resumes the coroutine with success/failure based on the return code.
 *   4. `invokeOnCancellation` cancels the ffmpeg session.
 */
class FfmpegKitRunner : FfmpegRunner {

    override suspend fun probeDurationMs(path: String): Long = try {
        FFprobeKit.getMediaInformation(path).mediaInformation
            ?.duration?.toDoubleOrNull()?.times(1000)?.toLong() ?: 0L
    } catch (t: Throwable) {
        0L
    }

    override suspend fun execute(
        input: String,
        output: String,
        format: String,
        bitrate: String?,
        totalDurationMs: Long,
        onProgress: (percent: Int) -> Unit,
    ): Result<Unit> = suspendCancellableCoroutine { cont ->
        val args = FfmpegArgs.build(input, output, format, bitrate).joinToString(" ") {
            if (it.contains(' ')) "\"$it\"" else it
        }

        val statsCallback = StatisticsCallback { stats: Statistics ->
            val pct = if (totalDurationMs > 0) {
                (stats.time * 100L / totalDurationMs).toInt().coerceIn(0, 99)
            } else {
                50
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