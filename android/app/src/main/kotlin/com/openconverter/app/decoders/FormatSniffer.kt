package com.openconverter.app.decoders

/**
 * Sniff the container format of decrypted audio bytes by magic headers.
 * 1:1 port of src/decoders/kgm.js:inferFormat (the most complete JS sniffer).
 *
 * Order matters: MP3 ID3 tag check comes before FLAC/etc because
 * `[0]==0x49,0x44,0x33` ("ID3") is unambiguous. The MP3 frame sync
 * test (`0xff, 0xe0..`) sits AFTER the WAV/OGG/FLAC checks because
 * those magics partially overlap a generic frame-sync mask.
 */
object FormatSniffer {
    private val FLAC_HDR = byteArrayOf(0x66, 0x4c, 0x61, 0x43) // "fLaC"
    private val MP3_HDR  = byteArrayOf(0x49, 0x44, 0x33)        // "ID3"
    private val OGG_HDR  = byteArrayOf(0x4f, 0x67, 0x67, 0x53) // "OggS"
    private val WAV_HDR  = byteArrayOf(0x52, 0x49, 0x46, 0x46) // "RIFF"
    private val M4A_HDR  = byteArrayOf(0x66, 0x74, 0x79, 0x70) // "ftyp" @ offset 4
    private val DFF_HDR  = byteArrayOf(0x46, 0x52, 0x4d, 0x38) // "FRM8"
    private val WMA_HDR  = byteArrayOf(
        0x30, 0x26, 0xb2.toByte(), 0x75, 0x8e.toByte(), 0x66, 0xcf.toByte(), 0x11,
        0xa6.toByte(), 0xd9.toByte(), 0x00, 0xaa.toByte(), 0x00, 0x62, 0xce.toByte(), 0x6c
    )

    fun sniff(b: ByteArray): String {
        if (b.size < 4) return "mp3"
        if (b[0] == MP3_HDR[0] && b[1] == MP3_HDR[1] && b[2] == MP3_HDR[2]) return "mp3"
        if (b[0] == FLAC_HDR[0] && b[1] == FLAC_HDR[1] && b[2] == FLAC_HDR[2] && b[3] == FLAC_HDR[3]) return "flac"
        if (b[0] == OGG_HDR[0] && b[1] == OGG_HDR[1] && b[2] == OGG_HDR[2] && b[3] == OGG_HDR[3]) return "ogg"
        if (b[0] == WAV_HDR[0] && b[1] == WAV_HDR[1] && b[2] == WAV_HDR[2] && b[3] == WAV_HDR[3]) return "wav"
        // AAC ADTS: 0xff, 0xf1 (must come before generic frame-sync mp3 check)
        if (b[0].toInt() and 0xff == 0xff && b[1].toInt() and 0xff == 0xf1) return "aac"
        // MP3 frame sync: 0xff, 0xe?
        if (b[0].toInt() and 0xff == 0xff && (b[1].toInt() and 0xe0) == 0xe0) return "mp3"
        if (b[0] == DFF_HDR[0] && b[1] == DFF_HDR[1] && b[2] == DFF_HDR[2] && b[3] == DFF_HDR[3]) return "dff"
        if (b.size >= WMA_HDR.size) {
            var match = true
            for (i in WMA_HDR.indices) if (b[i] != WMA_HDR[i]) { match = false; break }
            if (match) return "wma"
        }
        if (b.size >= 8 && b[4] == M4A_HDR[0] && b[5] == M4A_HDR[1] && b[6] == M4A_HDR[2] && b[7] == M4A_HDR[3]) return "m4a"
        return "mp3"
    }
}
