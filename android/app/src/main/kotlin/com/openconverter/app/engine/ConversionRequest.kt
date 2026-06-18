package com.openconverter.app.engine

/** A batch conversion request. `plainInputExts` (with leading dot, lowercase) are plaintext inputs that skip decryption. */
data class ConversionRequest(
    val inputUris: List<String>,
    val targetFormat: String,        // "mp3"|"flac"|"wav"|"m4a"|"ogg"
    val outputFolderUri: String,
    val bitrate: String?,            // null = codec default (FLAC lossless, etc.)
    val plainInputExts: Set<String>, // e.g. setOf(".mp3",".flac",".wav",".m4a",".aac",".ogg",".opus")
)

/** Per-file outcome. Exactly one of outputPath/error is non-null (skipped ⇒ outputPath set, skipped=true, error=null). */
data class FileResult(
    val index: Int,
    val inputUri: String,
    val outputPath: String?,
    val error: String?,
    val skipped: Boolean = false,
)
