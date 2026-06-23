package com.openconverter.app.decoders

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * QMC decoder — 1:1 port of `src/decoders/qmc.js`.
 *
 * v1: deterministic 8x7 state-machine XOR keystream with 0x8000-position
 *     skip. Used for `.qmc0`/`.qmc3`/`.qmcflac`/`.qmcogg`. No per-file key.
 * v2: SAME XOR cipher but uses a 128-byte working key derived via key_compress
 *     from a base64 ekey. Automatically extracts key from file tail if present.
 */
object QmcDecoder : Decoder {

    override val supportedExtensions: Set<String> = setOf(
        ".qmc0", ".qmc3", ".qmcflac", ".qmcogg", ".qmc1", ".qmc2", ".tkm",
        ".mflac", ".mflac0", ".mgg", ".mgg1",
        ".bkc", ".bkcmp3", ".bkcflac", ".bkcogg", ".bkcm4a", ".bkcwav", ".bkcwma", ".bkcape"
    )

    private val SEED_MAP = arrayOf(
        intArrayOf(0x4a, 0xd6, 0xca, 0x90, 0x67, 0xf7, 0x52),
        intArrayOf(0x5e, 0x95, 0x23, 0x9f, 0x13, 0x11, 0x7e),
        intArrayOf(0x47, 0x74, 0x3d, 0x90, 0xaa, 0x3f, 0x51),
        intArrayOf(0xc6, 0x09, 0xd5, 0x9f, 0xfa, 0x66, 0xf9),
        intArrayOf(0xf3, 0xd6, 0xa1, 0x90, 0xa0, 0xf7, 0xf0),
        intArrayOf(0x1d, 0x95, 0xde, 0x9f, 0x84, 0x11, 0xf4),
        intArrayOf(0x0e, 0x74, 0xbb, 0x90, 0xbc, 0x3f, 0x92),
        intArrayOf(0x00, 0x09, 0x5b, 0x9f, 0x62, 0x66, 0xa1),
    )

    private class V1Seed {
        var x = -1
        var y = 8
        var dx = 1
        var index = -1

        fun nextMask(): Int {
            val ret: Int
            index++
            when {
                x < 0 -> { dx = 1; y = (8 - y) % 8; ret = 0xc3 }
                x > 6 -> { dx = -1; y = 7 - y;     ret = 0xd8 }
                else -> ret = SEED_MAP[y][x]
            }
            x += dx
            if (index == 0x8000 || (index > 0x8000 && (index + 1) % 0x8000 == 0)) {
                return nextMask()
            }
            return ret
        }
    }

    private object Base64Decoder {
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        private val INV = IntArray(256) { -1 }.apply {
            for (i in ALPHABET.indices) {
                this[ALPHABET[i].code] = i
            }
            this['='.code] = 0
        }

        fun decode(str: String): ByteArray {
            val clean = str.filter { !it.isWhitespace() }
            if (clean.isEmpty()) return ByteArray(0)
            var pad = 0
            if (clean.endsWith("==")) pad = 2
            else if (clean.endsWith("=")) pad = 1
            
            val numBytes = (clean.length * 6 / 8) - pad
            val out = ByteArray(numBytes)
            var outIdx = 0
            var i = 0
            while (i < clean.length) {
                val c0 = INV[clean[i].code]
                val c1 = INV[clean[i + 1].code]
                val c2 = INV[clean[i + 2].code]
                val c3 = INV[clean[i + 3].code]
                
                val triple = (c0 shl 18) or (c1 shl 12) or (c2 shl 6) or c3
                if (outIdx < numBytes) out[outIdx++] = (triple ushr 16).toByte()
                if (outIdx < numBytes) out[outIdx++] = (triple ushr 8).toByte()
                if (outIdx < numBytes) out[outIdx++] = triple.toByte()
                i += 4
            }
            return out
        }
    }

    private data class DetectedKey(val ekey: ByteArray, val audioLen: Int)

    private fun detectKey(buf: ByteArray): DetectedKey? {
        val len = buf.size
        if (len < 8) return null

        // 1. Check QTag tail
        try {
            val qTagBytes = buf.sliceArray(len - 4 until len)
            if (qTagBytes.decodeToString() == "QTag") {
                val metaLen = ByteBuffer.wrap(buf, len - 8, 4).order(ByteOrder.BIG_ENDIAN).int
                if (metaLen > 0 && metaLen < len - 8) {
                    val rawMeta = buf.sliceArray(len - 8 - metaLen until len - 8).decodeToString()
                    val parts = rawMeta.split(',')
                    if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                        val ekeyBytes = Base64Decoder.decode(parts[0])
                        return DetectedKey(ekeyBytes, len - 8 - metaLen)
                    }
                }
            }
        } catch (t: Throwable) {
            // Ignore parsing errors
        }

        // 2. Check Numeric tail
        try {
            val keyLen = ByteBuffer.wrap(buf, len - 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (keyLen in 1 until 0xFFFF && keyLen < len - 4) {
                val ekeyBase64 = buf.sliceArray(len - 4 - keyLen until len - 4).decodeToString().trim()
                if (ekeyBase64.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '=' || it.isWhitespace() }) {
                    val ekeyBytes = Base64Decoder.decode(ekeyBase64)
                    return DetectedKey(ekeyBytes, len - 4 - keyLen)
                }
            }
        } catch (t: Throwable) {
            // Ignore parsing errors
        }

        return null
    }

    private fun shiftMix(byte: Int, shift: Int): Int {
        val s = shift and 7
        if (s == 0) return byte and 0xff
        val b = byte and 0xff
        return ((b shl s) or (b ushr s)) and 0xff
    }

    private fun keyCompress(ekey: ByteArray): ByteArray {
        val n = ekey.size
        if (n == 0) throw IllegalArgumentException("ekey is empty")
        val out = ByteArray(128)
        for (i in 0 until 128) {
            val idx = (i * i + 71214) % n
            val shift = (idx + 4) % 8
            out[i] = shiftMix(ekey[idx].toInt(), shift).toByte()
        }
        return out
    }

    fun decryptV1(input: ByteArray): ByteArray {
        val seed = V1Seed()
        val out = ByteArray(input.size)
        for (i in input.indices) {
            out[i] = ((input[i].toInt() and 0xff) xor seed.nextMask()).toByte()
        }
        return out
    }

    fun decryptV2(input: ByteArray, ekey: ByteArray): ByteArray {
        val key = keyCompress(ekey)
        val out = ByteArray(input.size)
        for (i in input.indices) {
            val o = if (i > 0x7fff) i % 0x7fff else i
            val keyByte = key[o % 128].toInt() and 0xff
            out[i] = (input[i].toInt() xor keyByte).toByte()
        }
        return out
    }

    override fun decrypt(input: ByteArray): DecryptResult {
        val detected = detectKey(input)
        val audio = if (detected != null) {
            val cipher = input.sliceArray(0 until detected.audioLen)
            decryptV2(cipher, detected.ekey)
        } else {
            decryptV1(input)
        }
        return DecryptResult(audio = audio, format = FormatSniffer.sniff(audio))
    }
}
