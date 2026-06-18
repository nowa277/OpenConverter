package com.openconverter.app.crash

import android.os.Build
import android.os.Process
import com.openconverter.app.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * One crash captured at process death. Pure: building the report does
 * no I/O, only composes a [body] string and a deterministic [fileName]
 * derived from the throwable's stack — so two crashes with the same
 * stack trace map to the same file (the crash list doesn't fill with
 * 50 duplicates of one bug).
 */
data class CrashReport(val fileName: String, val body: String) {
    companion object {
        private const val TIMESTAMP_FORMAT = "yyyyMMdd-HHmmss"

        /**
         * Build a crash report from a thrown [throwable] on [threadName],
         * with [recentLog] already snapshotted from
         * [com.openconverter.app.log.OCLog]. [nowMs] is injected so tests
         * can pin the timestamp.
         */
        fun build(
            throwable: Throwable,
            threadName: String,
            recentLog: List<String>,
            nowMs: Long,
        ): CrashReport {
            val ts = formatTimestamp(nowMs)
            val hash = hash6(throwable)
            val fileName = "crash-$ts-$hash.txt"

            val body = buildString {
                appendLine("=== OpenConverter Crash Report ===")
                appendLine("请在分享前确认下方内容不含敏感信息(本机文件名/账号等),")
                appendLine("不放心可以删掉对应行再发。")
                appendLine()
                appendLine("App: ${BuildConfig.VERSION_NAME} (versionCode ${BuildConfig.VERSION_CODE})")
                appendLine("Git: ${BuildConfig.GIT_HASH} (${BuildConfig.GIT_CLEAN})")
                appendLine("Built: ${BuildConfig.BUILD_TIME}")
                appendLine()
                appendLine("Device: ${Build.MODEL} (${Build.DEVICE})")
                appendLine("Manufacturer: ${Build.MANUFACTURER}")
                appendLine("Brand: ${Build.BRAND}")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("ABI: ${Build.SUPPORTED_ABIS?.joinToString(",") ?: "unknown"}")
                appendLine()
                appendLine("Time: ${isoWithOffset(nowMs)}")
                appendLine("Thread: $threadName")
                appendLine("Process: pid=${Process.myPid()}")
                appendLine()
                appendLine("=== Stack Trace ===")
                appendLine(stringify(throwable))
                if (recentLog.isNotEmpty()) {
                    appendLine()
                    appendLine("=== Recent OCLog (last ${recentLog.size} lines) ===")
                    recentLog.forEach { appendLine(it) }
                }
            }

            return CrashReport(fileName, body)
        }

        private fun formatTimestamp(ms: Long): String {
            val fmt = SimpleDateFormat(TIMESTAMP_FORMAT, Locale.US)
            fmt.timeZone = TimeZone.getDefault()
            return fmt.format(Date(ms))
        }

        private fun isoWithOffset(ms: Long): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            fmt.timeZone = TimeZone.getDefault()
            return fmt.format(Date(ms))
        }

        private fun stringify(t: Throwable): String {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            return sw.toString()
        }

        private fun hash6(t: Throwable): String {
            // Hash on class + message + the FRAME signatures (class.method),
            // not on the full printStackTrace output (which includes line
            // numbers that vary across builds). Two crashes that throw the
            // same exception with the same call stack collapse to one file.
            val sig = buildString {
                append(t.javaClass.name).append('|').append(t.message ?: "")
                t.stackTrace.forEach { f ->
                    append('|').append(f.className).append('.').append(f.methodName)
                }
                var cause: Throwable? = t.cause
                while (cause != null && cause !== t) {
                    append("||").append(cause.javaClass.name).append('|').append(cause.message ?: "")
                    cause.stackTrace.forEach { f ->
                        append('|').append(f.className).append('.').append(f.methodName)
                    }
                    cause = cause.cause
                }
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(sig.toByteArray(Charsets.UTF_8))
            val hex = digest.joinToString("") { "%02x".format(it) }
            return hex.substring(0, 6)
        }
    }
}
