package com.openconverter.app.decoders

/**
 * Pure function: detect audio format from first 16 bytes (optional) and/or
 * filename (optional).
 *
 * Per desktop src/decoders/\*.js (read-only reference):
 *  - 4 formats have unique magic: NCM, VPR, KWM, KGM-family (KGM+KGMA share magic)
 *  - 7 formats are headerless: QMC v1 (4 variants), QMC v2 (3 family)
 *  - KGM vs KGMA: same magic, distinguished only by extension
 *
 * For headerless formats, fileName extension is the only routing signal.
 * For magic-detected formats, fileName is fallback (used for KGM vs KGMA).
 *
 * @param firstBytes at least 16 bytes; null = extension-only
 * @param fileName used for extension hint (e.g. "song1.qmcflac"); null = magic-only
 * @return format identifier, or null if undetermined
 */
object FormatDetector {
    private const val MIN_BYTES = 16

    // NCM: ASCII "CTENFDAM" (8 bytes)
    // VPR: 16-byte VprHeader (hex)
    // KWM: ASCII "yeelion-kuwo" (12 bytes)
    // KGM family: 16-byte KgmHeader (hex) — KGM and KGMA share this
    private val VPR_MAGIC = byteArrayOf(
        0x05, 0x28, 0xBC.toByte(), 0x96.toByte(),
        0xE9.toByte(), 0xE4.toByte(), 0x5A, 0x43,
        0x91.toByte(), 0xAA.toByte(), 0xBD.toByte(), 0xD0.toByte(),
        0x7A, 0xF5.toByte(), 0x36, 0x31
    )
    private val KGM_MAGIC = byteArrayOf(
        0x7C, 0xD5.toByte(), 0x32, 0xEB.toByte(),
        0x86.toByte(), 0x02, 0x7F, 0x4B,
        0xA8.toByte(), 0xAF.toByte(), 0xA6.toByte(), 0x8E.toByte(),
        0x0F, 0xFF.toByte(), 0x99.toByte(), 0x14
    )

    fun detect(firstBytes: ByteArray?, fileName: String? = null): String? {
        // 1. Magic-based detection (4 unique + 1 shared)
        if (firstBytes != null && firstBytes.size >= MIN_BYTES) {
            if (matchesAscii(firstBytes, 0, "CTENFDAM")) return "ncm"
            if (matchesBytes(firstBytes, 0, VPR_MAGIC)) return "vpr"
            if (matchesAscii(firstBytes, 0, "yeelion-kuwo")) return "kwm"
            if (matchesBytes(firstBytes, 0, KGM_MAGIC)) {
                // KGM vs KGMA: same magic; extension disambiguates
                return when (extractExtension(fileName)) {
                    "kgm" -> "kgm"
                    "kgma" -> "kgma"
                    else -> "kgma"  // default per desktop behavior
                }
            }
        }

        // 2. Extension-based detection (headerless QMC v1/v2)
        val ext = extractExtension(fileName) ?: return null
        return when (ext) {
            "qmc0" -> "qmc0"
            "qmc3" -> "qmc3"
            "qmcflac" -> "qmcflac"
            "qmcogg" -> "qmcogg"
            "mflac" -> "mflac"
            "mflac0" -> "mflac0"
            "mgg" -> "mgg"
            "mgg1" -> "mgg1"
            "bkc" -> "bkc"
            "bkcmp3", "bkcflac", "bkcogg",
            "bkcm4a", "bkcwav", "bkcwma", "bkcape" -> ext
            else -> null
        }
    }

    private fun extractExtension(fileName: String?): String? {
        if (fileName == null) return null
        val lastDot = fileName.lastIndexOf('.')
        if (lastDot < 0 || lastDot == fileName.length - 1) return null
        return fileName.substring(lastDot + 1).lowercase()
    }

    private fun matchesAscii(buf: ByteArray, offset: Int, ascii: String): Boolean {
        if (buf.size < offset + ascii.length) return false
        for (i in ascii.indices) {
            if (buf[offset + i] != ascii[i].code.toByte()) return false
        }
        return true
    }

    private fun matchesBytes(buf: ByteArray, offset: Int, magic: ByteArray): Boolean {
        if (buf.size < offset + magic.size) return false
        for (i in magic.indices) {
            if (buf[offset + i] != magic[i]) return false
        }
        return true
    }
}
