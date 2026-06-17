package com.openconverter.app.failures

import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists conversion failures to cacheDir/failures-<timestamp>.log
 * for user to share via Settings → 失败日志 → 系统分享.
 *
 * Format: JSON line per failure
 *   {"uri": "...", "filename": "...", "error": "...", "ts": "..."}
 */
class FailureLog(context: Context) {
    private val cacheDir = context.cacheDir
    private val currentFile: File
        get() = File(cacheDir, "failures-${today()}.log")

    private fun today(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    fun record(uri: String, filename: String, error: String) {
        val entry = JSONObject().apply {
            put("uri", uri)
            put("filename", filename)
            put("error", error)
            put("ts", System.currentTimeMillis())
        }
        currentFile.appendText(entry.toString() + "\n")
    }

    fun readAll(): String = currentFile.readText()

    fun shareIntent(): Intent {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, readAll())
            putExtra(Intent.EXTRA_SUBJECT, "OpenConverter 失败日志")
        }
        return Intent.createChooser(sendIntent, "分享失败日志")
    }

    fun cleanup(olderThanDays: Int = 7) {
        val cutoff = System.currentTimeMillis() - olderThanDays * 24L * 3600L * 1000L
        cacheDir.listFiles { f -> f.name.startsWith("failures-") && f.name.endsWith(".log") }
            ?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }
}
