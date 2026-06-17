package com.openconverter.app.service

import com.openconverter.app.decoders.AudioData
import com.openconverter.app.decoders.FormatDetector
import com.openconverter.app.decoders.KgmDecoder
import com.openconverter.app.decoders.KwmDecoder
import com.openconverter.app.decoders.NcmDecoder
import com.openconverter.app.decoders.QmcDecoder
import com.openconverter.app.decoders.QmcV2Decoder
import com.openconverter.app.ffmpeg.FfmpegBridge
import com.openconverter.app.ffmpeg.FfmpegTranscoder

/**
 * Pure logic for converting one file's encrypted bytes to a target format.
 *
 * Pipeline: detect format → decrypt → ffmpeg transcode → return encoded bytes.
 *
 * NO I/O. The Service (Task 3.2) handles Uri/ContentResolver; this class
 * only transforms bytes in memory. NO Android imports. Pure Kotlin +
 * the [FfmpegTranscoder] interface (the production [FfmpegBridge] loads
 * native libs; tests inject a fake transcoder).
 *
 * Supported input formats: ncm, qmc0/qmc3/qmcflac/qmcogg (QMCv1), the
 * QMCv2 family (mflac/mflac0/mgg/mgg1/bkc*), kgm/kgma/vpr, kwm.
 * Unsupported formats throw [IllegalArgumentException] with a diagnostic.
 */
class ConversionOrchestrator(
    private val ffmpeg: FfmpegTranscoder = FfmpegBridge,
) {
    /**
     * Outcome of converting one file. `encoded` is the post-transcode
     * audio bytes ready to be written to disk by the caller; the
     * `sourceFormat`/`targetFormat` are echoed for the caller's logging
     * and metadata (e.g. output filename extension).
     */
    data class Result(
        val encoded: ByteArray,
        val sourceFormat: String,
        val targetFormat: String,
        val durationSec: Double? = null,
    )

    /**
     * Convert one in-memory buffer.
     *
     * @param input raw encrypted bytes (any of the supported formats).
     * @param fileName original filename for extension-based dispatch
     *   (headerless formats like QMC v1/v2 require this).
     * @param ekey QQ Music ekey string, required only for QMCv2 files.
     * @param targetFormat output container ("mp3" / "flac" / "wav" / "m4a" / "ogg").
     * @param bitrateKbps target bitrate hint for ffmpeg.
     * @throws IllegalArgumentException if the format is unknown or unsupported.
     * @throws com.openconverter.app.decoders.MissingEkeyException for QMCv2
     *   without an ekey.
     */
    fun convertOneInMemory(
        input: ByteArray,
        fileName: String? = null,
        ekey: String? = null,
        targetFormat: String = "mp3",
        bitrateKbps: Int = 256,
    ): Result {
        val firstBytes = if (input.size >= 16) input.copyOfRange(0, 16) else null
        val sourceFormat = FormatDetector.detect(firstBytes, fileName)
            ?: throw IllegalArgumentException(
                "Unknown format. First bytes: " +
                    input.copyOfRange(0, minOf(16, input.size))
                        .joinToString("") { "%02x".format(it) }
            )

        val audio: AudioData = when (sourceFormat) {
            "ncm" -> NcmDecoder.decrypt(input)
            "qmc0", "qmc3", "qmcflac", "qmcogg" ->
                QmcDecoder.decrypt(input, sourceFormat)
            "mflac", "mflac0", "mgg", "mgg1",
            "bkc", "bkcmp3", "bkcflac", "bkcogg",
            "bkcm4a", "bkcwav", "bkcwma", "bkcape" ->
                QmcV2Decoder.decrypt(input, ekey, sourceFormat)
            "kgm", "kgma" -> KgmDecoder.decrypt(input, sourceFormat)
            "vpr" -> KgmDecoder.decrypt(input, "vpr")
            "kwm" -> KwmDecoder.decrypt(input)
            else -> throw IllegalArgumentException("Unsupported format: $sourceFormat")
        }

        // Probe the decrypted audio to validate + get duration.
        // ffmpeg.probeDuration returns -1.0 for invalid audio.
        val probedDuration: Double = ffmpeg.probeDuration(audio.bytes, audio.format)
        if (probedDuration < 0.0) {
            throw IllegalArgumentException("Input is not valid ${audio.format} audio")
        }
        val probed = audio.copy(durationSec = probedDuration)

        val encoded = ffmpeg.transcode(probed.bytes, probed.format, targetFormat, bitrateKbps)
        return Result(
            encoded = encoded,
            sourceFormat = sourceFormat,
            targetFormat = targetFormat,
            durationSec = probed.durationSec,
        )
    }
}
