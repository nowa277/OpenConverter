package com.openconverter.app.decoders

/**
 * Pure Kotlin port of src/decoders/kwm.js (Kuwo Music KWM format).
 *
 * Algorithm: 32-byte circular XOR cipher. Re-implemented from scratch
 * based on davidxuang/MusicDecrypto (LGPL-2.1, do not copy).
 *
 * File layout:
 *   0x000..0x00A  Magic "yeelion-kuwo" (10 bytes)
 *   0x00A..0x010  6 bytes padding
 *   0x010..0x014  4-byte uint32 LE seed
 *   0x014..0x400  Reserved / padding
 *   0x400..end   Encrypted audio (XOR with derived 32-byte mask)
 *
 * Decryption:
 *   1. decimal = uint32_to_decimal_string(seed)
 *   2. mask first len(decimal) bytes = ASCII bytes of decimal
 *   3. mask remainder = 0
 *   4. for i in 0..32: mask[i] ^= ROOT[i]
 *   5. for i in 0..audio_len: audio[i] ^= mask[i % 32]
 *
 * The cipher is involutory (XOR is self-inverse), so encrypt == decrypt.
 */
object KwmDecoder {

    private val MAGIC = byteArrayOf(
        0x79, 0x65, 0x65, 0x6C, 0x69, 0x6F, 0x6E, 0x2D, 0x6B, 0x75, 0x77, 0x6F,
    )
    private const val MAGIC_LEN = 10
    private const val MASK_SIZE = 32

    // "MoOtOiTvINGwd2E6n0E1i7L5t2IoOoNk" — 32 bytes
    private val ROOT = byteArrayOf(
        0x4D, 0x6F, 0x4F, 0x74, 0x4F, 0x69, 0x54, 0x76,
        0x49, 0x4E, 0x47, 0x77, 0x64, 0x32, 0x45, 0x36,
        0x6E, 0x30, 0x45, 0x31, 0x69, 0x37, 0x4C, 0x35,
        0x74, 0x32, 0x49, 0x6F, 0x4F, 0x6F, 0x4E, 0x6B,
    )

    private const val AUDIO_OFFSET = 0x400 // 1024
    private const val SEED_OFFSET = 0x10   // 16

    /**
     * Build the 32-byte XOR mask from a seed uint32.
     */
    private fun buildMask(seed: Long): ByteArray {
        val decimal = seed.toString(10)
        val mask = ByteArray(MASK_SIZE)
        val len = minOf(decimal.length, MASK_SIZE)
        for (i in 0 until len) {
            mask[i] = decimal[i].code.toByte()
        }
        for (i in 0 until MASK_SIZE) {
            mask[i] = (mask[i].toInt() xor ROOT[i].toInt()).toByte()
        }
        return mask
    }

    /**
     * Decrypt a KWM buffer. Pure function — no filesystem access.
     */
    fun decrypt(input: ByteArray): AudioData {
        require(input.size >= AUDIO_OFFSET) {
            "KWM: file too small (${input.size} < $AUDIO_OFFSET)"
        }
        for (i in 0 until MAGIC_LEN) {
            require(input[i] == MAGIC[i]) {
                "KWM: bad magic, expected \"yeelion-kuwo\" at offset 0"
            }
        }
        val seed = readUInt32LE(input, SEED_OFFSET)
        val mask = buildMask(seed.toLong() and 0xFFFFFFFFL)

        val audio = input.copyOfRange(AUDIO_OFFSET, input.size)
        for (i in audio.indices) {
            audio[i] = (audio[i].toInt() xor mask[i % MASK_SIZE].toInt()).toByte()
        }
        return AudioData(bytes = audio, format = inferFormat(audio))
    }

    private fun readUInt32LE(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xff) or
            ((buf[offset + 1].toInt() and 0xff) shl 8) or
            ((buf[offset + 2].toInt() and 0xff) shl 16) or
            ((buf[offset + 3].toInt() and 0xff) shl 24)
    }

    /**
     * Sniff audio format from decrypted magic bytes.
     * Kuwo uses: MP3 ("ID3" or 0xFF 0xFB/0xFA/0xF3/0xF2), FLAC ("fLaC"),
     * M4A ("ftyp" at offset 4), OGG ("OggS"), WAV ("RIFF").
     */
    private fun inferFormat(audio: ByteArray): String {
        if (audio.size < 4) return "mp3"
        // "ID3"
        if (audio[0] == 0x49.toByte() && audio[1] == 0x44.toByte() && audio[2] == 0x33.toByte()) return "mp3"
        // "fLaC"
        if (audio[0] == 0x66.toByte() && audio[1] == 0x4c.toByte() &&
            audio[2] == 0x61.toByte() && audio[3] == 0x43.toByte()) return "flac"
        // "OggS"
        if (audio[0] == 0x4f.toByte() && audio[1] == 0x67.toByte() &&
            audio[2] == 0x67.toByte() && audio[3] == 0x53.toByte()) return "ogg"
        // "RIFF"
        if (audio[0] == 0x52.toByte() && audio[1] == 0x49.toByte() &&
            audio[2] == 0x46.toByte() && audio[3] == 0x46.toByte()) return "wav"
        // MP3 frame sync 0xFF 0xEx
        if (audio[0] == 0xff.toByte() && (audio[1].toInt() and 0xe0) == 0xe0) return "mp3"
        // M4A: bytes 4..8 == "ftyp"
        if (audio.size >= 8 && audio[4] == 0x66.toByte() && audio[5] == 0x74.toByte() &&
            audio[6] == 0x79.toByte() && audio[7] == 0x70.toByte()) return "m4a"
        return "mp3"
    }
}