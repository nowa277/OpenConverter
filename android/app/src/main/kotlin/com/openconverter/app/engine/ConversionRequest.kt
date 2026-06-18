package com.openconverter.app.engine

/**
 * A batch conversion request. `plainInputExts` (with leading dot, lowercase) are
 * plaintext inputs that skip decryption.
 *
 * `inputDisplayNames[i]` accompanies `inputUris[i]` so the engine never has to
 * issue an Android-coupled `OpenableColumns.DISPLAY_NAME` query — the SAF layer
 * resolves names once at request-construction time. Both lists MUST have the
 * same length.
 */
data class ConversionRequest(
    val inputUris: List<String>,
    val inputDisplayNames: List<String>,
    val targetFormat: String,        // "mp3"|"flac"|"wav"|"m4a"|"ogg"
    val outputFolderUri: String,
    val bitrate: String?,            // null = codec default (FLAC lossless, etc.)
    val plainInputExts: Set<String>, // e.g. setOf(".mp3",".flac",".wav",".m4a",".aac",".ogg",".opus")
) {
    init {
        require(inputUris.size == inputDisplayNames.size) {
            "inputUris and inputDisplayNames must be parallel lists"
        }
    }
}

/** Per-file outcome. Exactly one of outputPath/error is non-null (skipped ⇒ outputPath set, skipped=true, error=null). */
data class FileResult(
    val index: Int,
    val inputUri: String,
    val outputPath: String?,
    val error: String?,
    val skipped: Boolean = false,
)
