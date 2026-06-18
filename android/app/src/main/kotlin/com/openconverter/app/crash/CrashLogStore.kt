package com.openconverter.app.crash

import java.io.File

/**
 * Reads, lists, and clears the crash directory. The directory layout
 * is the producer's responsibility (see [CrashReport.fileName]); the
 * store treats every file inside as a crash report.
 */
class CrashLogStore(private val dir: File) {

    data class Entry(val fileName: String, val modifiedMs: Long, val sizeBytes: Long)

    fun list(): List<Entry> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles().orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { Entry(it.name, it.lastModified(), it.length()) }
    }

    fun read(fileName: String): String = File(dir, fileName).readText(Charsets.UTF_8)

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
