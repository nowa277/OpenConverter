package com.openconverter.app.decoders

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * KWM decoder (Kuwo Music, 酷我) — 1:1 port of `src/decoders/kwm.js`.
 *
 * 32-byte circular XOR mask. The mask is built from a uint32 LE seed at
 * offset 0x10 of the file: ASCII bytes of seed.toString(10) fill the front
 * of the mask, the rest is zeros, then mask[i] ^= ROOT[i].
 *
 * File layout:
 *   0x000..0x00A  Magic "yeelion-kuwo" (10 bytes)
 *   0x00A..0x010  6 bytes padding
 *   0x010..0x014  4-byte uint32 LE seed
 *   0x014..0x400  Reserved
 *   0x400..end   Encrypted audio
 */
object KwmDecoder : Decoder {
    private val MAGIC = "yeelion-kuwo".toByteArray(Charsets.US_ASCII)         // 10 bytes
    private val ROOT  = "MoOtOiTvINGwd2E6n0E1i7L5t2IoOoNk".toByteArray(Charsets.US_ASCII) // 32 bytes
    private const val MASK_SIZE = 32
    private const val AUDIO_OFFSET = 0x400 // 1024
    private const val SEED_OFFSET = 0x10   // 16

    override val supportedExtensions: Set<String> = setOf(".kwm")

    /** Build the 32-byte XOR mask from the uint32 seed. Same algorithm as kwm.js:buildMask. */
    fun buildMask(seed: Long): ByteArray {
        val decimal = seed.toString() // base 10, like JS Number.toString(10)
        val mask = ByteArray(MASK_SIZE) // zero-initialized
        val len = minOf(decimal.length, MASK_SIZE)
        for (i in 0 until len) mask[i] = decimal[i].code.toByte()
        for (i in 0 until MASK_SIZE) mask[i] = (mask[i].toInt() xor ROOT[i].toInt()).toByte()
        return mask
    }

    override fun decrypt(input: ByteArray): DecryptResult {
        require(input.size >= AUDIO_OFFSET) { "KWM: file too small (${input.size} < $AUDIO_OFFSET)" }
        for (i in MAGIC.indices) require(input[i] == MAGIC[i]) { "KWM: bad magic at offset $i" }

        // uint32 LE seed at offset 0x10. Convert to unsigned long for decimal stringification.
        val seed = ByteBuffer.wrap(input, SEED_OFFSET, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFF_FFFFL
        val mask = buildMask(seed)

        val audioLen = input.size - AUDIO_OFFSET
        val audio = ByteArray(audioLen)
        for (i in 0 until audioLen) {
            audio[i] = (input[AUDIO_OFFSET + i].toInt() xor mask[i % MASK_SIZE].toInt()).toByte()
        }
        return DecryptResult(audio = audio, format = FormatSniffer.sniff(audio))
    }
}
