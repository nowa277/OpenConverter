package com.openconverter.app.log

import android.util.Log
import java.util.ArrayDeque

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
 * A process-level ringbuffer retains the last [ringCapacity] formatted
 * records so the CrashReporter can stamp the last log lines into each
 * crash file. Capacity is intentionally small to keep RSS bounded.
 *
 * Logcat command to capture everything:
 *   adb logcat -c && adb logcat | grep OpenConverter
 */
object OCLog {
    /** Override the sink. Pass AndroidLogSink() in production; pass a fake in tests. */
    @Volatile
    private var sink: LogSink = AndroidLogSink()

    /** Max records retained in the in-memory ring for crash reports. */
    const val ringCapacity: Int = 200

    /** Process-level ring. Synchronized because callers may live on any thread. */
    private val ring: ArrayDeque<String> = ArrayDeque(ringCapacity)

    fun setSink(s: LogSink) { sink = s }

    fun v(msg: String) = emit(LogLevel.VERBOSE, msg, null)
    fun d(msg: String) = emit(LogLevel.DEBUG, msg, null)
    fun i(msg: String) = emit(LogLevel.INFO, msg, null)
    fun w(msg: String, t: Throwable? = null) = emit(LogLevel.WARN, msg, t)
    fun e(msg: String, t: Throwable? = null) = emit(LogLevel.ERROR, msg, t)

    /**
     * Log a structured event with key=value context pairs.
     *   OCLog.i("convert", "uri" to "content://x", "format" to "mp3")
     *   → tag=OpenConverter, msg="convert uri=content://x format=mp3"
     */
    fun i(event: String, vararg kv: Pair<String, Any?>) =
        emit(LogLevel.INFO, formatEvent(event, kv), null)
    fun w(event: String, t: Throwable? = null, vararg kv: Pair<String, Any?>) =
        emit(LogLevel.WARN, formatEvent(event, kv), t)
    fun e(event: String, t: Throwable? = null, vararg kv: Pair<String, Any?>) =
        emit(LogLevel.ERROR, formatEvent(event, kv), t)

    private fun emit(level: LogLevel, msg: String, throwable: Throwable?) {
        sink.log(level, TAG, msg, throwable)
        val line = buildString {
            append(level.name).append(' ').append(msg)
            if (throwable != null) append(" err=").append(throwable.message)
        }
        synchronized(ring) {
            if (ring.size >= ringCapacity) ring.pollFirst()
            ring.addLast(line)
        }
    }

    /**
     * Immutable snapshot of the last [ringCapacity] records, oldest first.
     * Safe to iterate after the app continues logging — the returned list
     * is a copy.
     */
    fun snapshot(): List<String> = synchronized(ring) { ArrayList(ring) }

    /** Test hook: empty the ring (for clean per-test state). */
    fun clearRing() = synchronized(ring) { ring.clear() }

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
