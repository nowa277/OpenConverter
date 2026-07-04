package com.openconverter.app.engine

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Real [FileSystemPort] for Android. Bridges:
 *   - SAF Uri ↔ ContentResolver streams for reads/writes,
 *   - app `cacheDir` for the temp files ffmpeg-kit operates on.
 *
 * `clearStaleCache()` is called by [ConversionService] on each FGS start so a
 * crash mid-job doesn't leave gigabytes of `in_*`/`out_*` behind.
 */
class AndroidFileSystemPort(private val context: Context) : FileSystemPort {

    override fun readBytes(uri: String): ByteArray =
        context.contentResolver.openInputStream(Uri.parse(uri))!!.use { it.readBytes() }

    override fun openInput(uri: String): InputStream =
        context.contentResolver.openInputStream(Uri.parse(uri))?.let { java.io.BufferedInputStream(it, 64 * 1024) }
            ?: throw IllegalArgumentException("Cannot open input URI: $uri")

    override fun cacheFile(name: String, bytes: ByteArray): String {
        val safe = name.replace('/', '_').replace('\\', '_')
        val f = File(context.cacheDir, safe)
        f.writeBytes(bytes)
        return f.absolutePath
    }

    /** Path inside cacheDir for ffmpeg to write its output to. Does not create the file. */
    override fun cachePath(name: String): String {
        val safe = name.replace('/', '_').replace('\\', '_')
        return File(context.cacheDir, safe).absolutePath
    }

    override fun openCacheOutput(path: String): OutputStream =
        java.io.BufferedOutputStream(File(path).outputStream(), 64 * 1024)

    override fun readCache(path: String): ByteArray = File(path).readBytes()

    override fun writeOutput(folderUri: String, displayName: String, mime: String, bytes: ByteArray): String {
        val docUri = createOutputDocument(folderUri, displayName, mime)
        (context.contentResolver.openOutputStream(docUri)?.let { java.io.BufferedOutputStream(it, 64 * 1024) }
            ?: throw IllegalArgumentException("Cannot open output stream for: $displayName")).use { it.write(bytes) }
        return docUri.toString()
    }

    override fun writeOutputFromCache(
        folderUri: String,
        displayName: String,
        mime: String,
        cachePath: String,
    ): String {
        val docUri = createOutputDocument(folderUri, displayName, mime)
        java.io.BufferedInputStream(File(cachePath).inputStream(), 64 * 1024).use { input ->
            val outStream = context.contentResolver.openOutputStream(docUri)?.let { java.io.BufferedOutputStream(it, 64 * 1024) }
                ?: throw IllegalArgumentException("Cannot open output stream for: $displayName")
            outStream.use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        }
        return docUri.toString()
    }

    private fun createOutputDocument(folderUri: String, displayName: String, mime: String): Uri {
        val tree = Uri.parse(folderUri)
        // createDocument requires a *document* URI, not the raw tree URI returned by
        // ACTION_OPEN_DOCUMENT_TREE. A tree URI's path is /tree/<docId>; createDocument
        // parses /document/<docId> and throws "Invalid URI" on the tree form. Rebuild
        // the tree-root document URI before creating the child document.
        val treeDocUri = DocumentsContract.buildDocumentUriUsingTree(
            tree, DocumentsContract.getTreeDocumentId(tree),
        )
        val docUri = DocumentsContract.createDocument(context.contentResolver, treeDocUri, mime, displayName)
            ?: throw RuntimeException("createDocument returned null for $displayName in $folderUri")
        return docUri
    }

    override fun cleanup(path: String) {
        runCatching { File(path).delete() }
    }

    /** Sweep `cacheDir` of stale `in_*`/`out_*` from a previous (possibly killed) run. */
    fun clearStaleCache() {
        context.cacheDir.listFiles { f ->
            f.name.startsWith("in_") || f.name.startsWith("out_")
        }?.forEach { runCatching { it.delete() } }
    }

    /** Resolve a SAF input Uri to its OpenableColumns DISPLAY_NAME (e.g. `song.ncm`). */
    fun queryDisplayName(uri: String): String {
        val cursor = context.contentResolver.query(
            Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
        ) ?: return uri.substringAfterLast('/')
        cursor.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return c.getString(idx) ?: uri.substringAfterLast('/')
            }
        }
        return uri.substringAfterLast('/')
    }
}
