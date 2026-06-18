package com.openconverter.app.ui.history

import android.net.Uri

/**
 * One row in the History screen. Captured at completion time by
 * ConversionService and stored in [HistoryRepository].
 *
 * @param timestampMs epoch millis when conversion completed
 * @param sourceUri original input URI
 * @param sourceName display name of the source
 * @param outputUri URI of the produced file (in the user's chosen folder)
 * @param targetFormat output container (mp3/flac/...)
 * @param status "DONE" or "FAILED"
 * @param errorMessage present only when status == "FAILED"
 */
data class HistoryEntry(
    val timestampMs: Long,
    val sourceName: String,
    val targetFormat: String,
    val status: String,
    val errorMessage: String? = null,
    val outputUri: Uri? = null,
    val sourceUri: Uri,
)
