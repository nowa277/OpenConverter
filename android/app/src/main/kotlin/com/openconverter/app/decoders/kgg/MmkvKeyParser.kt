package com.openconverter.app.decoders.kgg

import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

object MmkvKeyParser {
    // Matches 32-character hex ID, followed by binary delimiter, followed by Base64 key string (>= 100 chars)
    private val PATTERN = Pattern.compile("([a-f0-9]{32})[\\x00-\\x1f\\x7f-\\xff]{1,5}([A-Za-z0-9+/=]{100,})")

    fun parse(bytes: ByteArray): Map<String, String> {
        if (bytes.isEmpty()) return emptyMap()
        val latin1 = String(bytes, StandardCharsets.ISO_8859_1)
        val matcher = PATTERN.matcher(latin1)
        val result = linkedMapOf<String, String>()
        while (matcher.find()) {
            val id = matcher.group(1)
            val key = matcher.group(2)
            if (!id.isNullOrBlank() && !key.isNullOrBlank()) {
                result[id] = key
            }
        }
        return result
    }
}
