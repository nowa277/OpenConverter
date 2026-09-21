package com.openconverter.app.meta

import org.json.JSONArray
import org.json.JSONObject

object NeteaseLyricParser {
    fun toLrc(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val text = bytes.toString(Charsets.UTF_8).trim()
        if (text.isEmpty()) return null
        val lrcField = extractLrcField(text)
        val body = lrcField ?: text
        val converted = convertBody(body)
        return converted?.takeIf { it.isNotBlank() }
    }

    private fun extractLrcField(text: String): String? {
        if (!text.startsWith("{")) return null
        return try {
            JSONObject(text).optString("lrc").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun convertBody(body: String): String? {
        val trimmed = body.trim()
        if (trimmed.contains(Regex("""\[\d{2}:\d{2}"""))) return trimmed.replace("\r\n", "\n")
        val lines = trimmed.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.isEmpty()) return null
        val out = ArrayList<String>(lines.size)
        for (line in lines) {
            val parsed = parseNestedLine(line) ?: return if (out.isEmpty()) null else out.joinToString("\n")
            out += parsed
        }
        return out.joinToString("\n")
    }

    private fun parseNestedLine(line: String): String? {
        if (!line.startsWith("{")) return null
        return try {
            val obj = JSONObject(line)
            val t = obj.optLong("t", -1L)
            if (t < 0L) return null
            val c = obj.optJSONArray("c") ?: JSONArray()
            val text = buildString {
                for (i in 0 until c.length()) {
                    val part = c.optJSONObject(i) ?: continue
                    append(part.optString("tx"))
                }
            }
            "${formatTs(t)}$text"
        } catch (_: Exception) {
            null
        }
    }

    internal fun formatTs(millis: Long): String {
        val totalCs = (millis / 10L).coerceAtLeast(0L)
        val minutes = totalCs / 6000L
        val seconds = (totalCs % 6000L) / 100L
        val cs = totalCs % 100L
        return "[%02d:%02d.%02d]".format(minutes, seconds, cs)
    }
}
