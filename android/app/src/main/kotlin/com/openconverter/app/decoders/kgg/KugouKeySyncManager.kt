package com.openconverter.app.decoders.kgg

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream
import java.io.InputStream

object KugouKeySyncManager {
    private val KUGOU_PACKAGES = listOf(
        "com.kugou.android",
        "com.kugou.android.lite",
        "com.kugou.android.hifi",
    )
    private val KNOWN_KEY_FILES = listOf(
        "files/mmkv/mggkey_multi_process",
        "files/mmkv/mggkey",
        "databases/kugou_music_v2.db",
        "databases/KGMusicV3.db",
    )
    private const val MAX_KEY_FILE_BYTES = 20 * 1024 * 1024
    private val FIND_SCRIPT = KUGOU_PACKAGES.joinToString(" ") { "/data/data/$it" }.let { roots ->
        "find $roots -maxdepth 5 -type f \\( -iname '*mggkey*' -o -iname '*kugou_music*' -o -iname 'KGMusicV3.db' -o -iname 'kugou_music_v2.db' \\) 2>/dev/null || true"
    }

    fun isShizukuAvailable(): Boolean {
        return runCatching {
            Shizuku.pingBinder()
        }.getOrDefault(false)
    }

    fun hasShizukuPermission(): Boolean {
        return runCatching {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    fun isRootAvailable(): Boolean {
        val checkCommands = listOf(
            arrayOf("su", "-c", "id"),
            arrayOf("su", "0", "id"),
            arrayOf("su", "root", "id"),
            arrayOf("/system/xbin/su", "0", "id"),
            arrayOf("/system/bin/su", "-c", "id"),
        )
        return checkCommands.any { cmd ->
            runCatching {
                val process = Runtime.getRuntime().exec(cmd)
                process.waitFor() == 0
            }.getOrDefault(false)
        }
    }

    suspend fun syncKeys(): Map<String, String> = withContext(Dispatchers.IO) {
        val aggregated = mutableMapOf<String, String>()

        // 1. Direct Root (su) execution
        val rootKeys = readViaRoot()
        if (rootKeys.isNotEmpty()) {
            aggregated.putAll(rootKeys)
        }

        // 2. Shizuku if granted
        if (hasShizukuPermission()) {
            val shizukuKeys = readViaShizuku()
            if (shizukuKeys.isNotEmpty()) {
                aggregated.putAll(shizukuKeys)
            }
        }

        // 3. Public storage scanner (/sdcard/Download, /sdcard/Music, kgmusic, etc.)
        val publicKeys = PublicStorageKeyScanner.scanPublicKeys()
        if (publicKeys.isNotEmpty()) {
            aggregated.putAll(publicKeys)
        }

        if (aggregated.isNotEmpty()) {
            return@withContext aggregated
        }

        // Detailed error reporting
        if (isShizukuAvailable() && !hasShizukuPermission() && !isRootAvailable()) {
            throw IllegalStateException("SHIZUKU_PERMISSION_REQUIRED")
        }

        if (!isRootAvailable() && !isShizukuAvailable()) {
            throw IllegalStateException("NO_ROOT_OR_PUBLIC_KEY")
        }

        throw IllegalStateException("NO_PERMISSION_OR_KEYS")
    }

    private fun readViaShizuku(): Map<String, String> {
        return runCatching {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", FIND_SCRIPT),
                null,
                null
            ) as Process
            val listed = String(process.inputStream.readAllBytesCompat(), Charsets.UTF_8)
            process.waitFor()
            val paths = listed.lines().filter { it.isNotBlank() }.ifEmpty { knownPaths() }
            parseKeyFiles(paths) { path ->
                val cat = method.invoke(null, arrayOf("sh", "-c", "head -c $MAX_KEY_FILE_BYTES ${shellQuote(path)}"), null, null) as Process
                val bytes = cat.inputStream.readAllBytesCompat()
                cat.waitFor()
                bytes
            }
        }.getOrDefault(emptyMap())
    }

    private fun readViaRoot(): Map<String, String> {
        val shells = listOf(
            arrayOf("su", "0", "sh", "-c"),
            arrayOf("su", "-c"),
            arrayOf("su", "root", "sh", "-c"),
            arrayOf("/system/xbin/su", "0", "sh", "-c"),
            arrayOf("/system/bin/su", "-c"),
        )
        for (shell in shells) {
            val listed = runCatching {
                val process = Runtime.getRuntime().exec(shell + FIND_SCRIPT)
                val text = String(process.inputStream.readAllBytesCompat(), Charsets.UTF_8)
                process.waitFor()
                text
            }.getOrDefault("")
            val paths = listed.lines().filter { it.isNotBlank() }.ifEmpty { knownPaths() }
            val parsed = parseKeyFiles(paths) { path ->
                val process = Runtime.getRuntime().exec(shell + "head -c $MAX_KEY_FILE_BYTES ${shellQuote(path)}")
                val bytes = process.inputStream.readAllBytesCompat()
                val exit = process.waitFor()
                if (exit == 0) bytes else ByteArray(0)
            }
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyMap()
    }

    private fun knownPaths(): List<String> =
        KUGOU_PACKAGES.flatMap { pkg -> KNOWN_KEY_FILES.map { "/data/data/$pkg/$it" } }

    private fun parseKeyFiles(paths: Iterable<String>, read: (String) -> ByteArray): Map<String, String> {
        val result = linkedMapOf<String, String>()
        for (path in paths) {
            if (path.isBlank()) continue
            val bytes = runCatching { read(path) }.getOrDefault(ByteArray(0))
            if (bytes.isEmpty()) continue
            val mmkv = MmkvKeyParser.parse(bytes)
            if (mmkv.isNotEmpty()) {
                result.putAll(mmkv)
                continue
            }
            val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()
            if (text != null && '\u0000' !in text && '$' in text) {
                val parsed = runCatching { KggKeyMap.parse(text) }.getOrDefault(emptyMap())
                if (parsed.isNotEmpty()) result.putAll(parsed)
            }
        }
        return result
    }

    private fun shellQuote(path: String): String = "'" + path.replace("'", "'\\''") + "'"

    private fun InputStream.readAllBytesCompat(): ByteArray {
        val buffer = ByteArrayOutputStream()
        val data = ByteArray(8192)
        var nRead: Int
        while (read(data, 0, data.size).also { nRead = it } != -1) {
            buffer.write(data, 0, nRead)
        }
        return buffer.toByteArray()
    }
}


