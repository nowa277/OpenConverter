package com.openconverter.decoders

/**
 * Pure function: detect audio format from the first 16 bytes of a file.
 *
 * Magic bytes come from src/decoders/&#42;.js (read-only reference).
 * Returns the format identifier used by NcmDecoder / QmcDecoder / etc.,
 * or null if no known magic matches.
 *
 * Minimum bytes required: 16. Files smaller than that return null.
 */
object FormatDetector {
    private const val MIN_BYTES = 16

    fun detect(firstBytes: ByteArray): String? {
        if (firstBytes.size < MIN_BYTES) return null

        // NCM: ASCII "CTENFDAM" at offset 0
        if (matchesAscii(firstBytes, 0, "CTENFDAM")) return "ncm"

        // QMC variants: "QTag" magic at offset 0. Default to qmc0; the
        // specific variant (qmcflac, qmcogg, etc.) is determined by a
        // sub-magic that QmcDecoder parses internally. For M1 we only
        // need to know it's a QMC family file.
        if (matchesAscii(firstBytes, 0, "QTag")) return "qmc0"

        // KGMA: ASCII "KGMA" at offset 0 (a different format than KGM)
        if (matchesAscii(firstBytes, 0, "KGMA")) return "kgma"

        // Implementer: add KGM, KWM, MFLAC, MGG, BKC* magic detection here
        // when verified against src/decoders/*.js. For now, return null.
        return null
    }

    private fun matchesAscii(buf: ByteArray, offset: Int, ascii: String): Boolean {
        if (buf.size < offset + ascii.length) return false
        for (i in ascii.indices) {
            if (buf[offset + i] != ascii[i].code.toByte()) return false
        }
        return true
    }
}