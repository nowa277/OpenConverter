package com.openconverter.app.engine

import com.openconverter.app.decoders.Decoder
import com.openconverter.app.decoders.DecoderRegistry
import com.openconverter.app.ffmpeg.FfmpegRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Pure-Kotlin per-file orchestration. Reads input bytes, optionally
 * decrypts via the registry, optionally transcodes via ffmpeg, writes the
 * result to the user's SAF folder. Everything Android-specific is hidden
 * behind [FileSystemPort] so the engine is JVM-testable end-to-end.
 *
 * Per-file failures do not abort the batch — the [FileResult] for that file
 * carries `error` and the loop continues. CancellationException always
 * propagates after best-effort cleanup of the in-flight temp.
 */
class ConversionEngine(
    private val registry: DecoderRegistry,
    private val ffmpeg: FfmpegRunner,
    private val fs: FileSystemPort,
    private val sink: ProgressSink,
    private val clock: Clock = SystemClock,
) {
    suspend fun convertAll(req: ConversionRequest): List<FileResult> {
        val results = mutableListOf<FileResult>()
        val total = req.inputUris.size
        for (i in 0 until total) {
            val uri = req.inputUris[i]
            val displayName = req.inputDisplayNames[i]
            results += runOne(i, total, uri, displayName, req)
        }
        return results
    }

    private suspend fun runOne(
        i: Int, total: Int,
        uri: String, displayName: String,
        req: ConversionRequest,
    ): FileResult {
        var inPath: String? = null
        var outPath: String? = null
        try {
            sink.onFileStart(i, total, displayName)
            coroutineContext.ensureActive()

            val ext = "." + displayName.substringAfterLast('.', "").lowercase()
            val bytes = fs.readBytes(uri)
            coroutineContext.ensureActive()

            // Decide audio + srcFormatExt for the transcode subroutine
            val (audio: ByteArray, srcFormatExt: String, isPlain: Boolean) =
                if (ext in req.plainInputExts) {
                    Triple(bytes, ext.removePrefix("."), true)
                } else {
                    val decoder = registry.find(ext)
                        ?: return FileResult(i, uri, null, "no decoder for $ext", skipped = false).also {
                            sink.onFileError(i, "no decoder for $ext")
                        }
                    val dr = decoder.decrypt(bytes)
                    Triple(dr.audio, dr.format, false)
                }

            // Direct write if format already matches and no bitrate change requested.
            if (srcFormatExt == req.targetFormat && req.bitrate == null) {
                val outName = outName(displayName, req.targetFormat)
                val outDocUri = fs.writeOutput(
                    req.outputFolderUri, outName, mimeFor(req.targetFormat), audio
                )
                sink.onFileDone(i, outDocUri)
                return FileResult(i, uri, outDocUri, null)
            }

            // Transcode subroutine
            val tag = if (isPlain) "plain" else "dec"
            inPath = fs.cacheFile("in_${i}_$tag.$srcFormatExt", audio)
            outPath = inPath.replaceAfterLast('.', req.targetFormat)
            coroutineContext.ensureActive()

            val r = ffmpeg.execute(
                inPath!!, outPath!!, req.targetFormat, req.bitrate,
                onProgress = { p -> sink.onFileProgress(i, p) },
            )
            if (r.isFailure) {
                val msg = r.exceptionOrNull()?.message ?: "ffmpeg failed"
                sink.onFileError(i, msg)
                return FileResult(i, uri, null, msg)
            }
            val outBytes = fs.readCache(outPath!!)
            val outDocUri = fs.writeOutput(
                req.outputFolderUri, outName(displayName, req.targetFormat),
                mimeFor(req.targetFormat), outBytes,
            )
            sink.onFileDone(i, outDocUri)
            return FileResult(i, uri, outDocUri, null)
        } catch (ce: CancellationException) {
            inPath?.let { fs.cleanup(it) }
            outPath?.let { fs.cleanup(it) }
            throw ce
        } catch (t: Throwable) {
            val msg = t.message ?: t::class.simpleName ?: "error"
            sink.onFileError(i, msg)
            return FileResult(i, uri, null, msg)
        } finally {
            inPath?.let { fs.cleanup(it) }
            outPath?.let { fs.cleanup(it) }
        }
    }
}

internal fun mimeFor(format: String): String = when (format.lowercase()) {
    "mp3"  -> "audio/mpeg"
    "flac" -> "audio/flac"
    "wav"  -> "audio/wav"
    "m4a"  -> "audio/mp4"
    "ogg"  -> "audio/ogg"
    else   -> "application/octet-stream"
}

internal fun outName(displayName: String, targetFormat: String): String {
    val stem = displayName.substringBeforeLast('.', displayName)
    return "$stem.$targetFormat"
}
