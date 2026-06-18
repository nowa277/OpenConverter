package com.openconverter.app.decoders

/**
 * Contract for a single encrypted-format decoder.
 *
 * Implementations MUST be pure (no filesystem, no Android, no I/O):
 * `decrypt` takes the whole file as bytes and returns the decrypted
 * audio bytes + the sniffed/declared container format. This purity is
 * what lets us byte-compare against the JS reference implementation
 * in JVM unit tests.
 *
 * To add a new format in v2+: create an object implementing this,
 * register it in [DecoderRegistry].
 */
interface Decoder {
    /** Lowercased extensions WITH leading dot, e.g. ".ncm". */
    val supportedExtensions: Set<String>

    /**
     * @param input the entire encrypted file as bytes
     * @return decrypted audio + its container format id ("mp3"/"flac"/...)
     */
    fun decrypt(input: ByteArray): DecryptResult
}

/**
 * @param audio decrypted, still-encoded audio bytes (NOT raw PCM)
 * @param format container format id, one of FormatSniffer's outputs
 * @param meta optional metadata JSON string (NCM carries track meta; others null)
 */
data class DecryptResult(
    val audio: ByteArray,
    val format: String,
    val meta: String? = null,
) {
    // ByteArray identity equality is wrong for our use; compare content.
    override fun equals(other: Any?): Boolean =
        this === other || (other is DecryptResult && audio.contentEquals(other.audio) && format == other.format && meta == other.meta)
    override fun hashCode(): Int = audio.contentHashCode() * 31 + format.hashCode()
}
