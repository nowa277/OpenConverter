package com.openconverter.app.decoders

/**
 * NetEase Cloud Music PC .uc cache restore — 1:1 port of `src/decoders/uc.js`.
 *
 * Algorithm references: 52pojie thread-1556228, thread-1560121,
 * thread-1822588, thread-1457398. Every byte is XORed with 0xA3 (163)
 * without skipping any headers; the output container is sniffed from the
 * first decrypted bytes (MP3, FLAC, WAV, OGG, M4A; fallback MP3).
 *
 * Like the JS reference this decoder is deliberately lenient: unknown
 * decrypted headers fall back to the extension's expected format, because
 * .uc caches are plain caches rather than structured containers.
 */
object UcDecoder : Decoder {
    private const val XOR_KEY = 0xA3

    override val supportedExtensions: Set<String> = setOf(".uc")

    /** XOR is self-inverse: restore(plain) == cipher, restore(cipher) == plain. */
    fun restoreUcBuffer(input: ByteArray): ByteArray {
        val out = ByteArray(input.size)
        for (i in input.indices) out[i] = (input[i].toInt() xor XOR_KEY).toByte()
        return out
    }

    /**
     * Sniff the decrypted container from the first bytes. Mirrors uc.js
     * `sniffUcFormat`: unrecognized headers fall back to MP3.
     */
    fun sniffUcFormat(probe: ByteArray): String {
        if (probe.size < 4) return "mp3"
        // fLaC
        if (probe[0] == 0x66.toByte() && probe[1] == 0x4C.toByte() && probe[2] == 0x61.toByte() && probe[3] == 0x43.toByte()) return "flac"
        // ID3 (MP3)
        if (probe[0] == 0x49.toByte() && probe[1] == 0x44.toByte() && probe[2] == 0x33.toByte()) return "mp3"
        // MP3 frame sync: 0xFF followed by top 3 bits set (0xE0)
        if (probe[0] == 0xFF.toByte() && (probe[1].toInt() and 0xE0) == 0xE0) return "mp3"
        // RIFF (WAV)
        if (probe[0] == 0x52.toByte() && probe[1] == 0x49.toByte() && probe[2] == 0x46.toByte() && probe[3] == 0x46.toByte()) return "wav"
        // OggS
        if (probe[0] == 0x4F.toByte() && probe[1] == 0x67.toByte() && probe[2] == 0x67.toByte() && probe[3] == 0x53.toByte()) return "ogg"
        // M4A: ftyp at offset 4
        if (probe.size >= 8 && probe[4] == 0x66.toByte() && probe[5] == 0x74.toByte() && probe[6] == 0x79.toByte() && probe[7] == 0x70.toByte()) return "m4a"
        return "mp3"
    }

    /** Filename pattern `<songId>-<bitrate>-<32hex>.uc` → songId; null otherwise. */
    fun parseUcSongId(filename: String): String? {
        val base = filename.substringAfterLast('/')
        val match = Regex("^(\\d+)-(\\d+)-([0-9a-fA-F]{32})\\.uc$").find(base)
        return match?.groupValues?.get(1)
    }

    override fun decrypt(input: ByteArray): DecryptResult {
        if (input.isEmpty()) throw IllegalArgumentException("UC 缓存文件为空")
        val restored = restoreUcBuffer(input)
        val format = sniffUcFormat(restored)
        return DecryptResult(audio = restored, format = format)
    }
}
