package com.openconverter.app.decoders.kgg

data class KggKeyMergeResult(
    val merged: Map<String, String>,
    val added: Int,
    val updated: Int,
)

object KggKeyMap {
    fun parse(text: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        text.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trimEnd('\r')
            if (line.isBlank()) return@forEachIndexed

            val separator = line.indexOf('$')
            require(separator > 0 && separator == line.lastIndexOf('$')) {
                "Invalid kgg.key line ${index + 1}"
            }
            val id = line.substring(0, separator)
            val key = line.substring(separator + 1)
            require(id.isNotBlank() && key.isNotBlank()) {
                "Invalid kgg.key line ${index + 1}"
            }
            result[id] = key
        }
        return result
    }

    fun serialize(keys: Map<String, String>): String = buildString {
        keys.toSortedMap().forEach { (id, key) ->
            append(id).append('$').append(key).append('\n')
        }
    }

    fun merge(current: Map<String, String>, incoming: Map<String, String>): KggKeyMergeResult {
        var added = 0
        var updated = 0
        val merged = current.toMutableMap()
        incoming.forEach { (id, key) ->
            when {
                id !in current -> added++
                current[id] != key -> updated++
            }
            merged[id] = key
        }
        return KggKeyMergeResult(merged.toMap(), added, updated)
    }
}
