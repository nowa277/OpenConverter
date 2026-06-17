package com.openconverter.app.saf

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Wraps ACTION_OPEN_DOCUMENT, ACTION_OPEN_DOCUMENT_TREE and ACTION_CREATE_DOCUMENT
 * for clean Compose use.
 */
class SafAdapter {
    /** Returns a contract that picks multiple documents. */
    fun openDocumentsContract(): ActivityResultContract<Array<String>, List<Uri>> =
        ActivityResultContracts.OpenMultipleDocuments()

    /**
     * Returns a contract that opens a folder picker (ACTION_OPEN_DOCUMENT_TREE).
     * The returned Uri is a tree URI suitable for DocumentsContract.createDocument.
     */
    fun openDocumentTreeContract(): ActivityResultContract<Uri?, Uri?> =
        ActivityResultContracts.OpenDocumentTree()

    /**
     * Returns a contract that creates a document for saving.
     * @param mime MIME type, e.g. "audio/mpeg" (MP3), "audio/flac", "audio/wav",
     *             "audio/mp4" (M4A), "audio/ogg". Pass "" to use system default.
     */
    fun createDocumentContract(mime: String): ActivityResultContract<String, Uri?> =
        ActivityResultContracts.CreateDocument(mime.ifEmpty { "*/*" })

    /**
     * Persist read access to the URIs returned by OpenMultipleDocuments.
     *
     * Without this, the read grant only lives as long as the activity that
     * received the result. If the user navigates away or the OS reclaims the
     * activity (Huawei HarmonyOS Files picker, vivo OriginOS, etc.), the URI
     * becomes dead and any later ContentResolver.openInputStream() throws
     * SecurityException.
     *
     * Returns the URIs that were successfully persisted (logging failures to
     * logcat so the user/developer can see which picker provider refused).
     */
    fun persistReadAccess(cr: ContentResolver, uris: List<Uri>): List<Uri> {
        val ok = mutableListOf<Uri>()
        uris.forEach { uri ->
            try {
                cr.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                ok.add(uri)
            } catch (e: SecurityException) {
                Log.w(
                    "OpenConverter",
                    "SafAdapter.persistReadAccess | denied | uri=$uri | " +
                        "err=${e.message}",
                )
            }
        }
        return ok
    }

    /**
     * Persist write access to the tree URI returned by OpenDocumentTree.
     * DocumentsContract.createDocument later requires this write grant; without
     * it, foreground service write throws SecurityException.
     */
    fun persistTreeWriteAccess(cr: ContentResolver, treeUri: Uri): Boolean = try {
        cr.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        true
    } catch (e: SecurityException) {
        Log.w(
            "OpenConverter",
            "SafAdapter.persistTreeWriteAccess | denied | treeUri=$treeUri | " +
                "err=${e.message}",
        )
        false
    }
}