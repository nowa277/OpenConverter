package com.openconverter.app.meta

import org.json.JSONArray
import org.json.JSONObject

object NeteaseLyricParser {
    private val LRC_TS = Regex("""^\[\d{2}:\d{2}""")

    private val PURE_MUSIC_PATTERNS = listOf(
        Regex("""纯音乐[，,\s]*请欣赏""", RegexOption.IGNORE_CASE),
        Regex("""本歌曲为纯音乐[，,\s]*无歌词""", RegexOption.IGNORE_CASE),
        Regex("""该歌曲为纯音乐""", RegexOption.IGNORE_CASE),
        Regex("""暂无歌词""", RegexOption.IGNORE_CASE),
        Regex("""没有填词""", RegexOption.IGNORE_CASE),
        Regex("""没有歌词""", RegexOption.IGNORE_CASE),
        Regex("""纯音乐""", RegexOption.IGNORE_CASE),
    )

    private data class LrcInfo(
        val body: String,
        val translated: String?,
        val noLyric: Boolean = false,
    )

    private data class LrcEntry(
        val raw: String,
        val ts: Long?,
        val isTrans: Boolean,
        val order: Int,
    )

    fun toLrc(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val text = bytes.toString(Charsets.UTF_8).trim()
        if (text.isEmpty()) return null

        val lrcInfo = extractLrcInfo(text)
        if (lrcInfo?.noLyric == true) return null

        val body = lrcInfo?.body ?: text
        val converted = convertBody(body) ?: return null
        if (converted.isBlank()) return null

        var finalLrc = converted
        if (!lrcInfo?.translated.isNullOrBlank()) {
            val convertedTrans = convertBody(lrcInfo!!.translated!!)
            finalLrc = mergeLrcWithTranslation(finalLrc, convertedTrans)
        }

        finalLrc = decodeHtmlEntities(finalLrc)
        if (isPureMusicPlaceholder(finalLrc)) return null
        return finalLrc.takeIf { it.isNotBlank() }
    }

    private fun decodeHtmlEntities(text: String): String {
        var res = text
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
        res = Regex("""&#(\d+);""").replace(res) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null) code.toChar().toString() else match.value
        }
        res = Regex("""&#x([0-9a-fA-F]+);""").replace(res) { match ->
            val code = match.groupValues[1].toIntOrNull(16)
            if (code != null) code.toChar().toString() else match.value
        }
        return res.replace("&amp;", "&")
    }

    private fun isPureMusicPlaceholder(text: String): Boolean {
        if (text.isBlank()) return false
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.isEmpty()) return false

        var hasContent = false
        for (line in lines) {
            val content = line.replace(Regex("""\[\d{2}:\d{2}(?:\.\d{1,3})?\]"""), "").trim()
            if (content.isEmpty()) continue
            hasContent = true
            val isPlaceholder = PURE_MUSIC_PATTERNS.any { it.containsMatchIn(content) }
            if (!isPlaceholder) return false
        }
        return hasContent
    }

    private fun parseLrcLineTimestamp(line: String): Long? {
        val match = Regex("""^\[(\d{2}):(\d{2}(?:\.\d{1,3})?)\]""").find(line) ?: return null
        val min = match.groupValues[1].toLongOrNull() ?: return null
        val sec = match.groupValues[2].toDoubleOrNull() ?: return null
        return ((min * 60.0 + sec) * 1000.0).toLong()
    }

    private fun mergeLrcWithTranslation(origLrc: String, transLrc: String?): String {
        if (transLrc.isNullOrBlank()) return origLrc
        val origLines = origLrc.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val transLines = transLrc.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (transLines.isEmpty()) return origLrc

        val entries = ArrayList<LrcEntry>(origLines.size + transLines.size)
        origLines.forEachIndexed { idx, raw ->
            entries.add(LrcEntry(raw, parseLrcLineTimestamp(raw), isTrans = false, order = idx))
        }
        transLines.forEachIndexed { idx, raw ->
            entries.add(LrcEntry(raw, parseLrcLineTimestamp(raw), isTrans = true, order = idx))
        }

        val headerLines = mutableListOf<String>()
        val timedLines = mutableListOf<LrcEntry>()

        for (entry in entries) {
            if (entry.ts == null) {
                if (!entry.isTrans) headerLines.add(entry.raw)
            } else {
                timedLines.add(entry)
            }
        }

        timedLines.sortWith(
            compareBy<LrcEntry> { it.ts }
                .thenBy { if (it.isTrans) 1 else -1 }
                .thenBy { it.order }
        )

        val merged = ArrayList<String>(headerLines.size + timedLines.size)
        merged.addAll(headerLines)
        timedLines.mapTo(merged) { it.raw }
        return merged.joinToString("\n")
    }

    private fun extractLrcInfo(text: String): LrcInfo? {
        if (!text.startsWith("{")) return null
        return try {
            val obj = JSONObject(text)
            if (obj.optBoolean("nolyric", false) || obj.optBoolean("uncollected", false)) {
                return LrcInfo("", null, noLyric = true)
            }
            val lrcNode = obj.opt("lrc") ?: return null
            val body = lyricText(lrcNode) ?: return null
            val translated = when (lrcNode) {
                is JSONObject -> lyricText(lrcNode.opt("tlyric"))
                else -> null
            } ?: lyricText(obj.opt("tlyric"))
            LrcInfo(body, translated)
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

