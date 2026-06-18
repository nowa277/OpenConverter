package com.openconverter.app.crash

import java.io.File

/**
 * Writes a [CrashReport] to a directory. Returns the written [File] on
 * success or `null` on I/O failure — the crash handler must never throw
 * (it would mask the original exception).
 */
object CrashReportWriter {
    fun write(report: CrashReport, dir: File): File? {
        try {
            if (!dir.exists() && !dir.mkdirs()) return null
            if (!dir.isDirectory) return null
            val out = File(dir, report.fileName)
            out.writeText(report.body, Charsets.UTF_8)
            return out
        } catch (e: Exception) {
            return null
        }
    }
}
