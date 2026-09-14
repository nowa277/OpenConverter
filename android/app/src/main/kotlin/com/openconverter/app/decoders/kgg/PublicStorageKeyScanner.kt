package com.openconverter.app.decoders.kgg

import android.os.Environment
import java.io.File
import java.nio.charset.StandardCharsets

object PublicStorageKeyScanner {

    fun scanPublicKeys(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val searchDirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            File("/sdcard/Download"),
            File("/sdcard/Music"),
            File("/sdcard/kgmusic"),
            File("/sdcard/kugou"),
        ).distinct().filter { it.exists() && it.isDirectory }

        for (dir in searchDirs) {
            runCatching {
                dir.walkTopDown()
                    .maxDepth(3)
                    .filter { file ->
                        val name = file.name.lowercase()
                        name == "kgg.keys" ||
                        name == "kgg.key" ||
                        name.endsWith(".kgg.key") ||
                        name.contains("mggkey") ||
                        name.endsWith("kgmusicv3.db")
                    }
                    .forEach { file ->
                        val keys = parseCandidateFile(file)
                        if (keys.isNotEmpty()) {
                            result.putAll(keys)
                        }
                    }
            }
        }

        return result
    }

    fun parseCandidateFile(file: File): Map<String, String> {
        return runCatching {
            if (!file.exists() || !file.isFile || file.length() == 0L || file.length() > 20 * 1024 * 1024) {
                return emptyMap()
            }
            val bytes = file.readBytes()
            // 1. Try MMKV format first if filename contains mggkey or binary detected
            val mmkvKeys = MmkvKeyParser.parse(bytes)
            if (mmkvKeys.isNotEmpty()) {
                return mmkvKeys
            }

            // 2. Try plain text kgg.key ("id$ekey")
            val text = String(bytes, StandardCharsets.UTF_8)
            if ('\u0000' !in text && '$' in text) {
                val parsed = runCatching { KggKeyMap.parse(text) }.getOrDefault(emptyMap())
                if (parsed.isNotEmpty()) {
                    return parsed
                }
            }

            emptyMap()
        }.getOrDefault(emptyMap())
    }
}
