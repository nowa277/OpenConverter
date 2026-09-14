package com.openconverter.app.decoders.kgg

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream
import java.io.InputStream

object KugouKeySyncManager {
    private const val TARGET_MMKV_PATH = "/data/data/com.kugou.android/files/mmkv/mggkey_multi_process"

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
                arrayOf("sh", "-c", "cat $TARGET_MMKV_PATH 2>/dev/null"),
                null,
                null
            ) as Process
            val bytes = process.inputStream.readAllBytesCompat()
            process.waitFor()
            MmkvKeyParser.parse(bytes)
        }.getOrDefault(emptyMap())
    }

    private fun readViaRoot(): Map<String, String> {
        val commands = listOf(
            arrayOf("su", "0", "sh", "-c", "cat $TARGET_MMKV_PATH"),
            arrayOf("su", "-c", "cat $TARGET_MMKV_PATH"),
            arrayOf("su", "0", "cat", TARGET_MMKV_PATH),
            arrayOf("su", "root", "sh", "-c", "cat $TARGET_MMKV_PATH"),
            arrayOf("/system/xbin/su", "0", "sh", "-c", "cat $TARGET_MMKV_PATH"),
            arrayOf("/system/xbin/su", "0", "cat", TARGET_MMKV_PATH),
            arrayOf("/system/bin/su", "-c", "cat $TARGET_MMKV_PATH"),
        )

        for (cmd in commands) {
            val parsed = runCatching {
                val process = Runtime.getRuntime().exec(cmd)
                val bytes = process.inputStream.readAllBytesCompat()
                val exit = process.waitFor()
                if (exit == 0 && bytes.isNotEmpty()) {
                    MmkvKeyParser.parse(bytes)
                } else {
                    emptyMap()
                }
            }.getOrDefault(emptyMap())

            if (parsed.isNotEmpty()) return parsed
        }

        return emptyMap()
    }

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


