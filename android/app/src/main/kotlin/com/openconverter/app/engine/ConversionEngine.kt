package com.openconverter.app.engine

import com.openconverter.app.decoders.DecoderRegistry
import com.openconverter.app.decoders.StreamDecryptResult
import com.openconverter.app.decoders.StreamingDecoder
import com.openconverter.app.decoders.kgg.KugouKeySyncManager
import com.openconverter.app.ffmpeg.Ffmetadata
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
    private val lyricResolver: LyricResolverPort? = null,
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
        var coverPath: String? = null
        var metaPath: String? = null
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
            var tags: Map<String, String> = emptyMap()
            var cover: ByteArray? = null
            var musicId: String? = null

            if (streamingDecoder != null) {
                inPath = fs.cachePath("in_${i}_dec")
                var streamResult: StreamDecryptResult? = null
                try {
                    streamResult = fs.openInput(uri).use { input ->
                        fs.openCacheOutput(inPath!!).use { output ->
                            streamingDecoder.decryptStreaming(input, output)
                        }
                    }
                } catch (t: Throwable) {
                    val msg = t.message.orEmpty()
                    if (msg.contains("Missing KGG key for", ignoreCase = true) || msg.contains("No KGG keys imported", ignoreCase = true)) {
                        val healed = runCatching {
                            KugouKeySyncManager.syncKeys().isNotEmpty()
                        }.getOrDefault(false)
                        if (healed) {
                            streamResult = fs.openInput(uri).use { input ->
                                fs.openCacheOutput(inPath!!).use { output ->
                                    streamingDecoder.decryptStreaming(input, output)
                                }
                            }
                        } else {
                            throw t
                        }
                    } else {
                        throw t
                    }
                }
                val streamed = requireNotNull(streamResult)
                srcFormatExt = streamed.format
                tags = streamed.tags
                cover = streamed.cover
                musicId = streamed.musicId
                streamedToCache = true
                isPlain = false
            } else {
                val bytes = fs.readBytes(uri)
                val decoder = decoderMatch?.decoder
                if (decoder != null) {
                    val result = decoder.decrypt(bytes)
                    val parsed = com.openconverter.app.meta.NcmTrackMeta.fromJson(result.meta)
                    tags = parsed.tags
                    musicId = parsed.musicId
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

            val lrc = resolveLrcOrNull(musicId)
            val canEmbedLyrics = !lrc.isNullOrBlank() && req.targetFormat.lowercase() in EMBED_LYRICS_FORMATS
            // Direct write if format already matches, no bitrate change, and no tags/cover/lyrics sidecar.
            val hasSidecar = tags.isNotEmpty() || cover != null || canEmbedLyrics
            if (srcFormatExt == req.targetFormat && req.bitrate == null && !hasSidecar) {
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
                writeSiblingLrc(req.outputFolderUri, displayName, req.targetFormat, decoderMatch?.encryptedExtension, lrc)
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
            if (cover != null) {
                val coverExt = com.openconverter.app.meta.NcmTrackMeta.coverExtension(cover)
                coverPath = fs.cacheFile("cover_${i}.$coverExt", cover)
            }
            if (canEmbedLyrics) {
                try {
                    val meta = LinkedHashMap<String, String>()
                    meta.putAll(tags)
                    meta["lyrics"] = lrc!!
                    metaPath = fs.cacheFile("lyrics_${i}.ffm", Ffmetadata.bytes(meta))
                } catch (_: Throwable) {
                    metaPath = null
                }
            }
            val copyAudio = srcFormatExt == req.targetFormat && req.bitrate == null
            val inputPath = requireNotNull(inPath)
            val outputPath = requireNotNull(outPath)
            var r = ffmpeg.execute(
                inputPath, outputPath, req.targetFormat, req.bitrate,
                totalDurationMs = probedMs,
                onProgress = { p -> sink.onFileProgress(i, p) },
                metadata = if (metaPath != null) emptyMap() else tags,
                coverPath = coverPath,
                copyAudio = copyAudio,
                metadataFile = metaPath,
            )
            if (r.isFailure && metaPath != null && !isInterrupt(r)) {
                fs.cleanup(outputPath)
                r = ffmpeg.execute(
                    inputPath, outputPath, req.targetFormat, req.bitrate,
                    totalDurationMs = probedMs,
                    onProgress = { p -> sink.onFileProgress(i, p) },
                    metadata = tags,
                    coverPath = coverPath,
                    copyAudio = copyAudio,
                    metadataFile = null,
                )
            }
            if (r.isFailure) {
                val msg = r.exceptionOrNull()?.message ?: "ffmpeg failed"
                sink.onFileError(i, msg)
                return FileResult(i, uri, null, msg)
            }
            val outDocUri = fs.writeOutputFromCache(
                req.outputFolderUri,
                outName(displayName, req.targetFormat, decoderMatch?.encryptedExtension),
                mimeFor(req.targetFormat),
                outputPath,
            )
            writeSiblingLrc(req.outputFolderUri, displayName, req.targetFormat, decoderMatch?.encryptedExtension, lrc)
            sink.onFileDone(i, outDocUri)
            return FileResult(i, uri, outDocUri, null)
        } catch (ce: CancellationException) {
            inPath?.let { fs.cleanup(it) }
            outPath?.let { fs.cleanup(it) }
            coverPath?.let { fs.cleanup(it) }
            metaPath?.let { fs.cleanup(it) }
            throw ce
        } catch (t: Throwable) {
            val msg = t.message ?: t::class.simpleName ?: "error"
            sink.onFileError(i, msg)
            return FileResult(i, uri, null, msg)
        } finally {
            inPath?.let { fs.cleanup(it) }
            outPath?.let { fs.cleanup(it) }
            coverPath?.let { fs.cleanup(it) }
            metaPath?.let { fs.cleanup(it) }
        }
    }

    private suspend fun resolveLrcOrNull(musicId: String?): String? {
        val id = musicId ?: return null
        val resolver = lyricResolver ?: return null
        return try {
            resolver.resolveLrc(id)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            null
        }
    }

    private fun writeSiblingLrc(
        folderUri: String,
        displayName: String,
        targetFormat: String,
        encryptedExtension: String?,
        lrc: String?,
    ) {
        if (lrc.isNullOrBlank()) return
        runCatching {
            val audioName = outName(displayName, targetFormat, encryptedExtension)
            val lrcName = audioName.substringBeforeLast('.', audioName) + ".lrc"
            // octet-stream: DocumentsContract.createDocument("text/plain", "song.lrc")
            // becomes song.lrc.txt on some OEM SAF providers (vivo Android 13).
            fs.writeOutput(folderUri, lrcName, "application/octet-stream", lrc.toByteArray(Charsets.UTF_8))
        }
    }

    private fun isInterrupt(r: Result<*>): Boolean {
        val ex = r.exceptionOrNull()
        return ex is CancellationException || ex is InterruptedException
    }

    private companion object {
        val EMBED_LYRICS_FORMATS = setOf("mp3", "flac", "m4a")
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
