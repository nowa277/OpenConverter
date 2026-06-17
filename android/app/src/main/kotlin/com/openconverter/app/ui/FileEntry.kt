package com.openconverter.app.ui

import android.net.Uri

/**
 * One row in the file list LazyColumn.
 *
 * @param uri SAF URI for the file
 * @param displayName filename as shown to the user (from DISPLAY_NAME column)
 * @param sizeBytes file size in bytes (0 if unknown)
 * @param sourceFormat detected source format ("ncm" / "qmcflac" / etc.), or null if undetermined
 * @param readable true if ContentResolver.canRead(uri) returns true; false means the file was deleted or permission revoked
 */
data class FileEntry(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val sourceFormat: String?,
    val readable: Boolean,
)
