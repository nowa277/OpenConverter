package com.openconverter.app.decoders

/**
 * QMC decoder — 1:1 port of `src/decoders/qmc.js`.
 *
 * v1: deterministic 8x7 state-machine XOR keystream with 0x8000-position
 *     skip. Used for `.qmc0`/`.qmc3`/`.qmcflac`/`.qmcogg`. No per-file key.
 * v2: SAME XOR cipher but uses a 128-byte working key derived via key_compress
 *     from a base64 ekey. Reserved for v2 (architecture-only here).
 */
object QmcDecoder : Decoder {

    override val supportedExtensions: Set<String> = setOf(".qmc0", ".qmc3", ".qmcflac", ".qmcogg")

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

    /**
     * Reproduces the JS class `QmcSeed` exactly. Field semantics:
     *   x  -1..7
     *   y   0..7  (constructed at 8 to trigger the pre-roll branch on first call)
     *   dx  -1 or 1
     *   index counts mask outputs (incremented even on the boundary skips)
     */
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
            // 0x8000 boundary skip — recurse instead of returning the boundary byte
            if (index == 0x8000 || (index > 0x8000 && (index + 1) % 0x8000 == 0)) {
                return nextMask()
            }
            return ret
        }
    }

    fun decryptV1(input: ByteArray): ByteArray {
        val seed = V1Seed()
        val out = ByteArray(input.size)
        for (i in input.indices) {
            out[i] = ((input[i].toInt() and 0xff) xor seed.nextMask()).toByte()
        }
        return out
    }

    override fun decrypt(input: ByteArray): DecryptResult {
        val audio = decryptV1(input)
        return DecryptResult(audio = audio, format = FormatSniffer.sniff(audio))
    }
}
