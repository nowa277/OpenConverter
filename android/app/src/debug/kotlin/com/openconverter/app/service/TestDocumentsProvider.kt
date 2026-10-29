package com.openconverter.app.service

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File

/** A SAF provider confined to this instrumentation package's synthetic fixtures. */
class TestDocumentsProvider : DocumentsProvider() {
    companion object { @Volatile var onCreateDocument: ((String) -> Unit)? = null }
    private val root: File get() = File(context!!.filesDir, "conversion-fixtures").apply { mkdirs() }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: android.os.Bundle?): android.os.Bundle? {
        if (method != "prepare") return super.call(method, arg, extras)
        root.listFiles()?.forEach { check(it.delete()) }
        val fixture = com.openconverter.app.decoders.SyntheticNcm
        File(root, "tagged.ncm").writeBytes(fixture.build())
        File(root, "small.wav").writeBytes(fixture.audio)
        File(root, "long.wav").outputStream().use { output ->
            val header = fixture.audio.copyOf(44)
            java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
                putInt(4, 36 + 57_600_000); putInt(40, 57_600_000)
            }
            output.write(header)
            val pcm = fixture.audio.copyOfRange(44, fixture.audio.size)
            repeat(3600) { output.write(pcm) }
        }
        val tree = android.provider.DocumentsContract.buildTreeDocumentUri("com.openconverter.app.test.documents", "root")
        val identity = android.os.Binder.clearCallingIdentity()
        try {
            context!!.grantUriPermission(requireNotNull(arg), tree,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        } finally {
            android.os.Binder.restoreCallingIdentity(identity)
        }
        return android.os.Bundle.EMPTY
    }

    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(
        projection ?: arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS, Root.COLUMN_MIME_TYPES),
    ).apply {
        val row = newRow()
        columnNames.forEach { name -> row.add(name, when (name) {
            Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID -> "root"
            Root.COLUMN_TITLE -> "Synthetic fixtures"
            Root.COLUMN_FLAGS -> Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD
            Root.COLUMN_MIME_TYPES -> "*/*"
            else -> null
        }) }
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor = cursor(projection).apply {
        addDocument(documentId)
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor = cursor(projection).apply {
        file(parentDocumentId).listFiles()?.forEach { addDocument(it.name) }
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        require(parentDocumentId == "root")
        val destination = file(displayName)
        if (mimeType == Document.MIME_TYPE_DIR) check(destination.mkdir()) else check(destination.createNewFile())
        onCreateDocument?.invoke(displayName)
        return displayName
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))

    override fun deleteDocument(documentId: String) { check(file(documentId).delete()) }
    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean = parentDocumentId == "root" && documentId != "root"

    private fun file(id: String): File {
        if (id == "root") return root
        require('/' !in id && '\\' !in id && id != "." && id != "..")
        return File(root, id)
    }

    private fun cursor(projection: Array<out String>?) = MatrixCursor(projection ?: arrayOf(
        Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_FLAGS,
    ))

    private fun MatrixCursor.addDocument(id: String) {
        val file = file(id)
        val row = newRow()
        columnNames.forEach { name -> row.add(name, when (name) {
            Document.COLUMN_DOCUMENT_ID -> id
            Document.COLUMN_DISPLAY_NAME -> file.name
            Document.COLUMN_MIME_TYPE -> if (file.isDirectory) Document.MIME_TYPE_DIR else "application/octet-stream"
            Document.COLUMN_SIZE -> file.length()
            Document.COLUMN_FLAGS -> if (file.isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE
            else -> null
        }) }
    }
}
