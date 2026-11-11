package com.openconverter.app.decoders.kgg

import android.database.sqlite.SQLiteDatabase
import java.io.File

object KggDatabaseReader {
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray()

    fun readKeys(file: File, cacheDir: File? = null): Map<String, String> {
        if (!file.exists() || !file.isFile || file.length() < KggDatabaseCipher.PAGE_SIZE) {
            return emptyMap()
        }
        val prefix = ByteArray(SQLITE_HEADER.size)
        val readBytes = runCatching {
            file.inputStream().buffered().use { it.read(prefix) }
        }.getOrDefault(-1)
        if (readBytes == prefix.size && prefix.contentEquals(SQLITE_HEADER)) {
            return queryDatabase(file)
        }

        val plaintext = if (cacheDir != null) {
            File.createTempFile("kgg-decrypted-", ".sqlite", cacheDir)
        } else {
            File.createTempFile("kgg-decrypted-", ".sqlite")
        }
        return try {
            file.inputStream().buffered(64 * 1024).use { input ->
                plaintext.outputStream().buffered(64 * 1024).use { output ->
                    KggDatabaseCipher.decrypt(input, output)
                }
            }
            queryDatabase(plaintext)
        } catch (_: Exception) {
            emptyMap()
        } finally {
            plaintext.delete()
        }
    }

    fun readKeysFromBytes(bytes: ByteArray, cacheDir: File? = null): Map<String, String> {
        if (bytes.size < KggDatabaseCipher.PAGE_SIZE) return emptyMap()
        val tempSource = if (cacheDir != null) {
            File.createTempFile("kgg-src-", ".tmp", cacheDir)
        } else {
            File.createTempFile("kgg-src-", ".tmp")
        }
        return try {
            tempSource.writeBytes(bytes)
            readKeys(tempSource, cacheDir)
        } finally {
            tempSource.delete()
        }
    }

    internal fun queryDatabase(file: File): Map<String, String> {
        val result = linkedMapOf<String, String>()
        try {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery(
                    """SELECT EncryptionKeyId, EncryptionKey FROM ShareFileItems
                        WHERE EncryptionKeyId IS NOT NULL AND EncryptionKeyId != ''
                          AND EncryptionKey IS NOT NULL AND EncryptionKey != ''""".trimIndent(),
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val key = cursor.getString(1)
                        if (id.isNotBlank() && key.isNotBlank()) result[id] = key
                    }
                }
            }
        } catch (_: Exception) {
            return emptyMap()
        }
        return result
    }
}
