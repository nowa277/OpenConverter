package com.openconverter.app.saf

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Wraps ACTION_OPEN_DOCUMENT and ACTION_CREATE_DOCUMENT for clean Compose use.
 */
class SafAdapter {
    /** Returns a contract that picks multiple documents. */
    fun openDocumentsContract(): ActivityResultContract<Array<String>, List<Uri>> =
        ActivityResultContracts.OpenMultipleDocuments()

    /**
     * Returns a contract that creates a document for saving.
     * @param mime MIME type, e.g. "audio/mpeg" (MP3), "audio/flac", "audio/wav",
     *             "audio/mp4" (M4A), "audio/ogg". Pass "" to use system default.
     */
    fun createDocumentContract(mime: String): ActivityResultContract<String, Uri?> =
        ActivityResultContracts.CreateDocument(mime.ifEmpty { "*/*" })
}
