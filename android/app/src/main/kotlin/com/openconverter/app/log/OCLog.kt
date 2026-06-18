package com.openconverter.app.log

import android.util.Log

/**
 * Single funnel for all OpenConverter log records.
 *
 * Production default is [AndroidLogSink] (wraps android.util.Log).
 * Tests inject a fake via [setSink] to capture records.
 *
 * Tag is always "OpenConverter" — child components add their context
 * via the `msg` argument or `contextual` varargs (key/value pairs
 * formatted as "k1=v1 k2=v2").
 *
 * Logcat command to capture everything:
 *   adb logcat -c && adb logcat | grep OpenConverter
 */
object OCLog {
    /** Override the sink. Pass AndroidLogSink() in production; pass a fake in tests. */
    @Volatile
    private var sink: LogSink = AndroidLogSink()

    fun setSink(s: LogSink) { sink = s }

    fun v(msg: String) = sink.log(LogLevel.VERBOSE, TAG, msg, null)
    fun d(msg: String) = sink.log(LogLevel.DEBUG, TAG, msg, null)
    fun i(msg: String) = sink.log(LogLevel.INFO, TAG, msg, null)
    fun w(msg: String, t: Throwable? = null) = sink.log(LogLevel.WARN, TAG, msg, t)
    fun e(msg: String, t: Throwable? = null) = sink.log(LogLevel.ERROR, TAG, msg, t)

    /**
     * Log a structured event with key=value context pairs.
     *   OCLog.i("convert", "uri" to "content://x", "format" to "mp3")
     *   → tag=OpenConverter, msg="convert uri=content://x format=mp3"
     */
    fun i(event: String, vararg kv: Pair<String, Any?>) =
        sink.log(LogLevel.INFO, TAG, formatEvent(event, kv), null)
    fun w(event: String, t: Throwable? = null, vararg kv: Pair<String, Any?>) =
        sink.log(LogLevel.WARN, TAG, formatEvent(event, kv), t)
    fun e(event: String, t: Throwable? = null, vararg kv: Pair<String, Any?>) =
        sink.log(LogLevel.ERROR, TAG, formatEvent(event, kv), t)

    private fun formatEvent(event: String, kv: Array<out Pair<String, Any?>>): String =
        if (kv.isEmpty()) event
        else buildString {
            append(event)
            kv.forEach { (k, v) -> append(' ').append(k).append('=').append(v) }
        }

    private const val TAG = "OpenConverter"
}

/** Default sink: routes to android.util.Log. */
class AndroidLogSink : LogSink {
    override fun log(level: LogLevel, tag: String, msg: String, throwable: Throwable?) {
        when (level) {
            LogLevel.VERBOSE -> if (throwable != null) Log.v(tag, msg, throwable) else Log.v(tag, msg)
            LogLevel.DEBUG   -> if (throwable != null) Log.d(tag, msg, throwable) else Log.d(tag, msg)
            LogLevel.INFO    -> if (throwable != null) Log.i(tag, msg, throwable) else Log.i(tag, msg)
            LogLevel.WARN    -> if (throwable != null) Log.w(tag, msg, throwable) else Log.w(tag, msg)
            LogLevel.ERROR   -> if (throwable != null) Log.e(tag, msg, throwable) else Log.e(tag, msg)
        }
    }
}
