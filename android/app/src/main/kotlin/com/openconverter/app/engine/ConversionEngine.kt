package com.openconverter.app.engine

import com.openconverter.app.decoders.Decoder
import com.openconverter.app.decoders.DecoderRegistry
import com.openconverter.app.decoders.StreamingDecoder
import com.openconverter.app.ffmpeg.FfmpegRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    suspend fun convertAll(req: ConversionRequest): List<FileResult> = coroutineScope {
        val total = req.inputUris.size
        val semaphore = Semaphore(2)
        val deferreds = (0 until total).map { i ->
            async {
                semaphore.withPermit {
                    val uri = req.inputUris[i]
                    val displayName = req.inputDisplayNames[i]
                    runOne(i, total, uri, displayName, req)
                }
            }
        }
        deferreds.awaitAll()
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
            val decoderMatch = registry.findForName(displayName)
            val streamingDecoder = decoderMatch?.decoder as? StreamingDecoder
            var audio: ByteArray? = null
            var streamedToCache = false
            val srcFormatExt: String
            val isPlain: Boolean

            if (streamingDecoder != null) {
                inPath = fs.cachePath("in_${i}_dec")
                srcFormatExt = fs.openInput(uri).use { input ->
                    fs.openCacheOutput(inPath!!).use { output ->
                        streamingDecoder.decrypt(input, output)
                    }
                }
                streamedToCache = true
                isPlain = false
            } else {
                val bytes = fs.readBytes(uri)
                val decoder = decoderMatch?.decoder
                if (decoder != null) {
                    val result = decoder.decrypt(bytes)
                    audio = result.audio
                    srcFormatExt = result.format
                    isPlain = false
                } else if (ext in req.plainInputExts) {
                    audio = bytes
                    srcFormatExt = ext.removePrefix(".")
                    isPlain = true
                } else {
                    val message = "no decoder for $ext"
                    sink.onFileError(i, message)
                    return FileResult(i, uri, null, message, skipped = false)
                }
            }
            coroutineContext.ensureActive()

            // Direct write if format already matches and no bitrate change requested.
            if (srcFormatExt == req.targetFormat && req.bitrate == null) {
                val outName = outName(displayName, req.targetFormat, decoderMatch?.encryptedExtension)
                val outDocUri = if (streamedToCache) {
                    fs.writeOutputFromCache(
                        req.outputFolderUri, outName, mimeFor(req.targetFormat), requireNotNull(inPath),
                    )
                } else {
                    fs.writeOutput(
                        req.outputFolderUri, outName, mimeFor(req.targetFormat), requireNotNull(audio),
                    )
                }
                sink.onFileDone(i, outDocUri)
                return FileResult(i, uri, outDocUri, null)
            }

            // Transcode subroutine
            val tag = if (isPlain) "plain" else "dec"
            if (!streamedToCache) {
                inPath = fs.cacheFile("in_${i}_$tag.$srcFormatExt", requireNotNull(audio))
            }
            // Independent output cache name — never derived from inPath. When
            // srcFormatExt == targetFormat, replaceAfterLast('.') would yield
            // inPath itself, making ffmpeg refuse input==output.
            outPath = fs.cachePath("out_${i}_$tag.${req.targetFormat}")
            coroutineContext.ensureActive()

            val probedMs = ffmpeg.probeDurationMs(inPath!!)
            val r = ffmpeg.execute(
                inPath, outPath, req.targetFormat, req.bitrate,
                totalDurationMs = probedMs,
                onProgress = { p -> sink.onFileProgress(i, p) },
            )
            if (r.isFailure) {
                val msg = r.exceptionOrNull()?.message ?: "ffmpeg failed"
                sink.onFileError(i, msg)
                return FileResult(i, uri, null, msg)
            }
            val outDocUri = fs.writeOutputFromCache(
                req.outputFolderUri,
                outName(displayName, req.targetFormat, decoderMatch?.encryptedExtension),
                mimeFor(req.targetFormat),
                outPath,
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

internal fun outName(
    displayName: String,
    targetFormat: String,
    encryptedExtension: String? = null,
): String {
    val stem = if (encryptedExtension == null) {
        displayName.substringBeforeLast('.', displayName)
    } else {
        val lowerName = displayName.lowercase()
        val marker = "$encryptedExtension."
        when {
            lowerName.endsWith(encryptedExtension) -> displayName.dropLast(encryptedExtension.length)
            lowerName.contains(marker) -> displayName.substring(0, lowerName.lastIndexOf(marker))
            else -> displayName.substringBeforeLast('.', displayName)
        }
    }
    return "$stem.$targetFormat"
}
