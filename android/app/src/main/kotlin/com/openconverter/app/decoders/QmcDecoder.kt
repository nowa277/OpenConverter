package com.openconverter.app.decoders

/**
 * Pure Kotlin port of src/decoders/qmc.js — QMCv1 decoder.
 *
 * Supports: .qmc0, .qmc3, .qmcflac, .qmcogg.
 * (QMCv2 — .mflac0, .mgg1, .bkc* — needs an ekey string from the QQ Music
 * client; see QmcV2Decoder in Task 2.2.)
 *
 * Algorithm: the whole file is XOR'd with a keystream produced by an 8x7
 * state machine (QmcSeed) with a 0x8000-position skip. The cipher is
 * symmetric — encrypt and decrypt are the same operation. There is no
 * file header; byte 0 of the QMC file is the first XOR'd audio byte.
 *
 * Reference: src/decoders/qmc.js (presburger/qmc-decoder, MIT).
 */
object QmcDecoder {

    private const val V1_OFFSET_BOUNDARY = 0x7fff

    // 8x7 seed table — same constants as the JS implementation.
    private val SEED_MAP = arrayOf(
        intArrayOf(0x4a, 0xd6, 0xca, 0x90, 0x67, 0xf7, 0x52), // y=0
        intArrayOf(0x5e, 0x95, 0x23, 0x9f, 0x13, 0x11, 0x7e), // y=1
        intArrayOf(0x47, 0x74, 0x3d, 0x90, 0xaa, 0x3f, 0x51), // y=2
        intArrayOf(0xc6, 0x09, 0xd5, 0x9f, 0xfa, 0x66, 0xf9), // y=3
        intArrayOf(0xf3, 0xd6, 0xa1, 0x90, 0xa0, 0xf7, 0xf0), // y=4
        intArrayOf(0x1d, 0x95, 0xde, 0x9f, 0x84, 0x11, 0xf4), // y=5
        intArrayOf(0x0e, 0x74, 0xbb, 0x90, 0xbc, 0x3f, 0x92), // y=6
        intArrayOf(0x00, 0x09, 0x5b, 0x9f, 0x62, 0x66, 0xa1), // y=7
    )

    /**
     * Stream cipher seed. Walks an 8x7 state machine, emitting one mask byte
     * per call to [nextMask]. Skips positions 0x8000 and 0x8000+N every N
     * bytes (i.e. positions where `(index + 1) % 0x8000 == 0` for index
     * strictly greater than 0x8000). Faithful port of JS `QmcSeed`.
     */
    private class QmcSeed {
        private var x: Int = -1
        private var y: Int = 8
        private var dx: Int = 1
        private var index: Int = -1

        fun nextMask(): Int {
            val ret: Int
            index++
            if (x < 0) {
                dx = 1
                y = (8 - y) % 8
                ret = 0xc3
            } else if (x > 6) {
                dx = -1
                y = 7 - y
                ret = 0xd8
            } else {
                ret = SEED_MAP[y][x]
            }
            x += dx
            if (index == 0x8000 || (index > 0x8000 && (index + 1) % 0x8000 == 0)) {
                return nextMask()
            }
            return ret
        }
    }

    /**
     * Decrypt a QMCv1 buffer (.qmc0 / .qmc3 / .qmcflac / .qmcogg).
     *
     * The cipher is symmetric, so this also encrypts plaintext into QMC form
     * (used by the test harness to generate synthetic vectors). The output
     * format hint is inferred from the decrypted audio's magic bytes; falls
     * back to the [extensionFormat] when the audio is too short or has no
     * recognisable magic.
     */
    fun decrypt(input: ByteArray, extensionFormat: String = "mp3"): AudioData {
        val seed = QmcSeed()
        val out = ByteArray(input.size)
        for (i in input.indices) {
            out[i] = (input[i].toInt() xor seed.nextMask()).toByte()
        }
        return AudioData(bytes = out, format = inferFormat(out, extensionFormat))
    }

    /**
     * Sniff audio format from magic bytes. Mirrors the JS `inferFormat`.
     */
    private fun inferFormat(audio: ByteArray, fallback: String): String {
        if (audio.size < 4) return fallback
        // "ID3"
        if (audio[0] == 0x49.toByte() && audio[1] == 0x44.toByte() &&
            audio[2] == 0x33.toByte()) return "mp3"
        // "fLaC"
        if (audio[0] == 0x66.toByte() && audio[1] == 0x4c.toByte() &&
            audio[2] == 0x61.toByte() && audio[3] == 0x43.toByte()) return "flac"
        // "OggS"
        if (audio[0] == 0x4f.toByte() && audio[1] == 0x67.toByte() &&
            audio[2] == 0x67.toByte() && audio[3] == 0x53.toByte()) return "ogg"
        // "RIFF"
        if (audio[0] == 0x52.toByte() && audio[1] == 0x49.toByte() &&
            audio[2] == 0x46.toByte() && audio[3] == 0x46.toByte()) return "wav"
        // MP3 sync 0xFF 0xEx
        if (audio[0] == 0xff.toByte() && (audio[1].toInt() and 0xe0) == 0xe0) return "mp3"
        // M4A: bytes 4..8 == "ftyp"
        if (audio.size >= 8 && audio[4] == 0x66.toByte() && audio[5] == 0x74.toByte() &&
            audio[6] == 0x79.toByte() && audio[7] == 0x70.toByte()) return "m4a"
        return fallback
    }
}