package com.openconverter.app.meta

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.InflaterInputStream

/**
 * KuGou KRC lyric decoder and LRC converter for Android.
 *
 * Algorithm reference:
 * - 52pojie thread-286822 (2014-08-24).
 *
 * 1. Magic check: "krc1" (4 bytes).
 * 2. XOR encryption with 16-byte repeating key:
 *    [64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105]
 * 3. Zlib decompress (InflaterInputStream) -> UTF-8 plaintext with syllable timestamps.
 * 4. Parse KRC syntax and convert to standard LRC format [mm:ss.xx].
 */
object KrcDecoder {
    private val KRC_MAGIC = "krc1".toByteArray(Charsets.US_ASCII)
    private val XOR_KEY = byteArrayOf(
        64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45,
        206.toByte(), 210.toByte(), 110, 105
    )

    fun decryptKrc(bytes: ByteArray): String {
        require(bytes.size >= KRC_MAGIC.size) { "KRC 文件头无效 (文件长度过短)" }
        for (i in KRC_MAGIC.indices) {
            require(bytes[i] == KRC_MAGIC[i]) { "KRC 文件头无效 (需以 krc1 开头)" }
        }

        val payloadLen = bytes.size - KRC_MAGIC.size
        val decrypted = ByteArray(payloadLen)
        for (i in 0 until payloadLen) {
            val k = XOR_KEY[i % XOR_KEY.size].toInt()
            decrypted[i] = (bytes[i + KRC_MAGIC.size].toInt() xor k).toByte()
        }

        return try {
            val out = ByteArrayOutputStream()
            InflaterInputStream(ByteArrayInputStream(decrypted)).use { it.copyTo(out) }
            out.toByteArray().toString(Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalArgumentException("KRC zlib 解压失败 (可能非合法的 KRC 密文): ${e.message}", e)
        }
    }

    fun krcToLrc(krcText: String): String? {
        if (krcText.isBlank()) return null
        val lines = krcText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val headers = mutableListOf<String>()
        val timedLines = mutableListOf<String>()

        val metaTagRegex = Regex("""^\[(ti|ar|al|by|offset):.*\]""", RegexOption.IGNORE_CASE)
        val ignoreMetaRegex = Regex("""^\[(language|id|hash|total|sign):.*\]""", RegexOption.IGNORE_CASE)
        val timedLineRegex = Regex("""^\[(\d+),(\d+)\](.*)$""")

        for (line in lines) {
            if (ignoreMetaRegex.containsMatchIn(line)) continue
            if (metaTagRegex.containsMatchIn(line)) {
                headers.add(line)
                continue
            }
            val match = timedLineRegex.find(line) ?: continue
            val startMs = match.groupValues[1].toLongOrNull() ?: continue
            val text = match.groupValues[3].replace(Regex("""<\d+,\d+,\d+>"""), "").trim()
            if (text.isNotEmpty()) {
                timedLines.add("${NeteaseLyricParser.formatTs(startMs)}$text")
            }
        }
        val all = headers + timedLines
        return if (all.isEmpty()) null else all.joinToString("\n")
    }

    fun decodeKrcToLrc(bytes: ByteArray): String? {
        val text = decryptKrc(bytes)
        return krcToLrc(text)
    }
}
