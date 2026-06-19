package com.openconverter.app.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class JsonHistoryStore(
    private val filesDir: File,
    private val maxEntries: Int = 500,
) : HistoryPort {

    private val file: File = File(filesDir, "history.jsonl")

    override suspend fun append(record: HistoryRecord): Unit = withContext(Dispatchers.IO) {
        file.appendText(jsonLine(record) + "\n", Charsets.UTF_8)
        trimIfNeeded()
    }

    override suspend fun readAll(): List<HistoryRecord> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        file.readLines(Charsets.UTF_8)
            .asSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { parse(it) }.getOrNull() }
            .toList()
            .reversed()
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        if (file.exists()) file.delete()
    }

    private suspend fun trimIfNeeded(): Unit = withContext(Dispatchers.IO) {
        val lines = file.readLines()
        if (lines.size > maxEntries) {
            file.writeText(lines.takeLast(maxEntries).joinToString("\n") + "\n", Charsets.UTF_8)
        }
    }

    private fun jsonLine(r: HistoryRecord): String = JSONObject().apply {
        put("ts", r.ts)
        put("inputName", r.inputName)
        put("targetFormat", r.targetFormat)
        put("status", if (r.status == HistoryStatus.SUCCESS) "success" else "failed")
        r.outputName?.let { put("outputName", it) }
        r.durationMs?.let { put("durationMs", it) }
        r.error?.let { put("error", it) }
    }.toString()

    private fun parse(line: String): HistoryRecord {
        val o = JSONObject(line)
        return HistoryRecord(
            ts = o.getLong("ts"),
            inputName = o.getString("inputName"),
            targetFormat = o.getString("targetFormat"),
            status = if (o.getString("status") == "success") HistoryStatus.SUCCESS else HistoryStatus.FAILED,
            outputName = if (o.has("outputName")) o.getString("outputName") else null,
            durationMs = if (o.has("durationMs")) o.getLong("durationMs") else null,
            error = if (o.has("error")) o.getString("error") else null,
        )
    }
}