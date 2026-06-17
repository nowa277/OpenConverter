package com.openconverter.decoders

/**
 * Decrypted audio data, ready to be fed into ffmpeg for transcoding.
 *
 * - bytes: the raw plaintext audio (typically MP3 or FLAC bytes).
 * - format: hint to ffmpeg for the input format ("mp3" or "flac" etc.).
 * - durationSec: ffprobe result, or null if unknown.
 */
data class AudioData(
    val bytes: ByteArray,
    val format: String,
    val durationSec: Double? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioData) return false
        return bytes.contentEquals(other.bytes) && format == other.format && durationSec == other.durationSec
    }
    override fun hashCode(): Int = bytes.contentHashCode() * 31 + format.hashCode()
}
