package com.openconverter.app.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Thin wrapper over SAF ActivityResultContracts + display-name query.
 * Kept stateless so HomeScreen (M7) can hold the launcher references and
 * ask this object for a human-readable name after each pick.
 */
object SafAdapter {

    /** Multi-select audio files. Supports pre-selecting the initial directory. */
    fun openMultipleAudioFilesContract(): ActivityResultContract<Uri?, List<Uri>> =
        object : ActivityResultContract<Uri?, List<Uri>>() {
            private val delegate = ActivityResultContracts.OpenMultipleDocuments()

            override fun createIntent(context: Context, input: Uri?): Intent {
                val intent = delegate.createIntent(context, arrayOf("audio/*", "*/*"))
                if (input != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, input)
                }
                return intent
            }

            override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> =
                delegate.parseResult(resultCode, intent)
        }

    /** Pick an output folder tree. Caller MUST take persistable permission on the result.
     *  Launch with `null` to let the user pick any tree, or a `Uri` to pre-select. */
    fun openOutputFolderContract(): ActivityResultContract<Uri?, Uri?> =
        ActivityResultContracts.OpenDocumentTree()

    /** Best-effort display name for a SAF document/content uri. Falls back to last path segment. */
    fun queryDisplayName(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = c.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment ?: uri.toString()
    }

    /** Best-effort byte size for a SAF document/content uri, or -1 if the provider omits SIZE. */
    fun querySize(context: Context, uri: Uri): Long {
        val resolver = context.contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && !c.isNull(idx)) {
                    val size = c.getLong(idx)
                    if (size > 0) return size
                }
            }
        }
        return -1L
    }
}

