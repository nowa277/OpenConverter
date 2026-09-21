package com.openconverter.app.engine

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class AndroidSafLyricLookup(
    context: Context,
    treeUri: String,
) : LyricLookupPort {
    private val app = context.applicationContext
    private val tree = Uri.parse(treeUri)
    private val delegate = DirectoryLyricLookup(::openRelative)

    override fun findLrcBytes(musicId: String): ByteArray? = delegate.findLrcBytes(musicId)

    private fun openRelative(relativePath: String): ByteArray? {
        val root = DocumentFile.fromTreeUri(app, tree) ?: return null
        var node: DocumentFile = root
        for (part in relativePath.split('/').filter { it.isNotEmpty() }) {
            node = node.findFile(part) ?: return null
        }
        if (node.isDirectory) return null
        return app.contentResolver.openInputStream(node.uri)?.use { it.readBytes() }
    }
}
