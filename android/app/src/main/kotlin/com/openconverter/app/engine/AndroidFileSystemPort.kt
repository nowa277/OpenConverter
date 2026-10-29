package com.openconverter.app.engine

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** SAF streams and app-local temporary files used by the conversion engine. */
class AndroidFileSystemPort(private val context: Context) : FileSystemPort {
    override fun readBytes(uri: String): ByteArray = openInput(uri).use { it.readBytes() }

    override fun openInput(uri: String): InputStream =
        context.contentResolver.openInputStream(Uri.parse(uri))?.let { java.io.BufferedInputStream(it, 64 * 1024) }
            ?: throw IllegalArgumentException("Cannot open input URI: $uri")

    override fun cacheFile(name: String, bytes: ByteArray): String = cachePath(name).also { File(it).writeBytes(bytes) }

    override fun cachePath(name: String): String =
        File(context.cacheDir, name.replace('/', '_').replace('\\', '_')).absolutePath

    override fun openCacheOutput(path: String): OutputStream =
        java.io.BufferedOutputStream(File(path).outputStream(), 64 * 1024)

    override fun readCache(path: String): ByteArray = File(path).readBytes()

    override suspend fun writeOutput(folderUri: String, displayName: String, mime: String, bytes: ByteArray): String =
        ByteArrayInputStream(bytes).use { writeDocument(folderUri, displayName, mime, it) }

    override suspend fun writeOutputFromCache(folderUri: String, displayName: String, mime: String, cachePath: String): String =
        File(cachePath).inputStream().buffered(64 * 1024).use { writeDocument(folderUri, displayName, mime, it) }

    private suspend fun writeDocument(folderUri: String, displayName: String, mime: String, input: InputStream): String {
        val context = currentCoroutineContext()
        context.ensureActive()
        val document = createOutputDocument(folderUri, displayName, mime)
        try {
            val stream = this.context.contentResolver.openOutputStream(document)
                ?: throw java.io.IOException("Cannot open output stream for: $displayName")
            stream.buffered(64 * 1024).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    context.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                context.ensureActive()
            }
            return document.toString()
        } catch (error: Exception) {
            try {
                DocumentsContract.deleteDocument(this.context.contentResolver, document)
            } catch (cleanupError: Exception) {
                error.addSuppressed(cleanupError)
            }
            throw error
        }
    }

    private fun createOutputDocument(folderUri: String, displayName: String, mime: String): Uri {
        val tree = Uri.parse(folderUri)
        val treeDocument = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return DocumentsContract.createDocument(context.contentResolver, treeDocument, mime, displayName)
            ?: throw java.io.IOException("createDocument returned null for $displayName in $folderUri")
    }

    override fun deleteOutput(uri: String) {
        check(DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(uri))) { "Cannot remove cancelled output: $uri" }
    }

    override fun cleanup(path: String) { File(path).delete() }

    fun clearStaleCache() {
        context.cacheDir.listFiles { file ->
            listOf("in_", "out_", "cover_", "lyrics_").any(file.name::startsWith)
        }?.forEach { it.delete() }
    }

    fun queryDisplayName(uri: String): String {
        context.contentResolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0) ?: uri.substringAfterLast('/')
        }
        return uri.substringAfterLast('/')
    }
}
