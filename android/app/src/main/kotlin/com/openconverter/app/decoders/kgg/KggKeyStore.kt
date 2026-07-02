package com.openconverter.app.decoders.kgg

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class KggKeyStore internal constructor(
    private val filesDir: File,
    private val cacheDir: File,
    private val openInput: (Uri) -> InputStream,
) : KggKeyProvider, KggKeyImporter {
    constructor(context: Context) : this(
        context.filesDir,
        context.cacheDir,
        { uri ->
            context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot open selected KGG key source")
        },
    )

    private val atomicFile = AtomicFile(File(filesDir, STORE_NAME))
    private val importMutex = Mutex()

    @Volatile
    private var keys: Map<String, String> = emptyMap()

    private val _state: MutableStateFlow<KggImportState>
    val state: StateFlow<KggImportState> get() = _state.asStateFlow()

    init {
        filesDir.mkdirs()
        cacheDir.mkdirs()
        val loaded = runCatching {
            if (atomicFile.baseFile.exists()) {
                KggKeyMap.parse(decodeUtf8(atomicFile.readFully()))
            } else {
                emptyMap()
            }
        }
        keys = loaded.getOrDefault(emptyMap())
        _state = MutableStateFlow(
            loaded.fold(
                onSuccess = { KggImportState.Ready(it.size) },
                onFailure = { KggImportState.Failed("Stored KGG keys are invalid", 0) },
            ),
        )
    }

    override fun find(encryptionKeyId: String): String? = keys[encryptionKeyId]
    override fun count(): Int = keys.size

    override suspend fun import(uri: Uri): KggImportResult = importMutex.withLock {
        _state.value = KggImportState.Importing
        try {
            val incoming = withContext(Dispatchers.IO) { readImport(uri) }
            require(incoming.isNotEmpty()) { "Selected KGG key source contains no keys" }

            val result = KggKeyMap.merge(keys, incoming)
            withContext(Dispatchers.IO) { persist(result.merged) }
            keys = result.merged
            KggImportResult(result.added, result.updated, result.merged.size).also {
                _state.value = KggImportState.Ready(it.total, it)
            }
        } catch (error: Throwable) {
            val message = error.message ?: "KGG key import failed"
            _state.value = KggImportState.Failed(message, keys.size)
            if (error is IllegalArgumentException) throw error
            throw IllegalArgumentException(message, error)
        }
    }

    private fun readImport(uri: Uri): Map<String, String> {
        val source = File.createTempFile(TEMP_PREFIX, ".source", cacheDir)
        val plaintext = File.createTempFile(TEMP_PREFIX, ".sqlite", cacheDir)
        try {
            openInput(uri).use { input -> source.outputStream().use(input::copyTo) }
            val prefix = source.inputStream().use { input ->
                ByteArray(SQLITE_HEADER.size).also { bytes ->
                    var count = 0
                    while (count < bytes.size) {
                        val read = input.read(bytes, count, bytes.size - count)
                        if (read < 0) break
                        count += read
                    }
                    if (count < bytes.size) return@use bytes.copyOf(count)
                }
            }
            if (prefix.contentEquals(SQLITE_HEADER)) return queryDatabase(source)

            val raw = source.readBytes()
            val text = runCatching { decodeUtf8(raw) }.getOrNull()
            if (text != null && '\u0000' !in text) return KggKeyMap.parse(text)

            source.inputStream().use { input ->
                plaintext.outputStream().use { output -> KggDatabaseCipher.decrypt(input, output) }
            }
            return queryDatabase(plaintext)
        } finally {
            source.delete()
            plaintext.delete()
        }
    }

    private fun queryDatabase(file: File): Map<String, String> {
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
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid KGMusicV3.db: ${error.message}", error)
        }
        require(result.isNotEmpty()) { "KGMusicV3.db contains no KGG keys" }
        return result
    }

    private fun persist(merged: Map<String, String>) {
        val stream = atomicFile.startWrite()
        try {
            stream.write(KggKeyMap.serialize(merged).toByteArray(StandardCharsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    companion object {
        private const val STORE_NAME = "kgg.keys"
        private const val TEMP_PREFIX = "kgg-import-"
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(StandardCharsets.US_ASCII)

        private fun decodeUtf8(bytes: ByteArray): String = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }
}
