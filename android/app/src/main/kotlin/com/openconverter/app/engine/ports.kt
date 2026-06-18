package com.openconverter.app.engine

/**
 * Ports the pure-Kotlin ConversionEngine depends on. Android's ConversionService
 * supplies real implementations; JVM tests supply fakes. The engine itself never
 * imports android.* — this is what makes it fully JVM-testable.
 */

/** File I/O the engine needs. All paths/uris are opaque strings to the engine. */
interface FileSystemPort {
    fun readBytes(uri: String): ByteArray
    /** Write bytes to a temp file in app cacheDir; return its absolute path. */
    fun cacheFile(name: String, bytes: ByteArray): String
    /** Absolute path of a temp file in app cacheDir WITHOUT writing it. Used for
     *  ffmpeg's output path, which must differ from the input path even when the
     *  codec/format strings are identical (ffmpeg rejects input==output). */
    fun cachePath(name: String): String
    /** Read a cached file produced by ffmpeg back into memory. */
    fun readCache(path: String): ByteArray
    /** Write final output into the user-selected SAF folder; return its uri. */
    fun writeOutput(folderUri: String, displayName: String, mime: String, bytes: ByteArray): String
    /** Best-effort delete of a temp file. No-op if missing. */
    fun cleanup(path: String)
}

/** Per-file progress reporting back to the UI/notification. */
interface ProgressSink {
    fun onFileStart(index: Int, total: Int, name: String)
    fun onFileProgress(index: Int, percent: Int)
    fun onFileDone(index: Int, outputPath: String)
    fun onFileError(index: Int, message: String)
}

/** Time source (avoids System.currentTimeMillis in pure code; testable). */
interface Clock { fun nowMs(): Long }

/** Default Clock backed by System.currentTimeMillis. */
object SystemClock : Clock { override fun nowMs(): Long = System.currentTimeMillis() }
