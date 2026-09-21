package com.openconverter.app.meta

import org.json.JSONArray
import org.json.JSONObject

object NeteaseLyricParser {
    private val LRC_TS = Regex("""^\[\d{2}:\d{2}""")

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
            val obj = JSONObject(text)
            val lrcNode = obj.opt("lrc") ?: return null
            val body = lyricText(lrcNode) ?: return null
            val translated = when (lrcNode) {
                is JSONObject -> lyricText(lrcNode.opt("tlyric"))
                else -> null
            } ?: lyricText(obj.opt("tlyric"))
            if (translated.isNullOrBlank()) body else "$body\n$translated"
        } catch (_: Exception) {
            null
        }
    }

    private fun lyricText(node: Any?): String? = when (node) {
        is JSONObject -> node.optString("lyric").takeIf { it.isNotBlank() }
        is String -> node.takeIf { it.isNotBlank() }
        else -> null
    }

    private fun looksLikeLrc(body: String): Boolean {
        val lines = body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.isEmpty()) return false
        if (lines.any { it.startsWith("{") }) return false
        return lines.any { LRC_TS.containsMatchIn(it) }
    }

    private fun convertBody(body: String): String? {
        val trimmed = body.trim()
        if (looksLikeLrc(trimmed)) return trimmed.replace("\r\n", "\n")
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
