package com.openconverter.app.decoders

/**
 * Pure Kotlin port of src/decoders/kgm.js — KGM / KGMA / VPR decoder.
 *
 * Supports: .kgm, .kgma, .vpr (KuGou Music v2 cache family).
 * No external key required: the per-file 16-byte key lives in the header
 * at offset 0x1C, and the 17th byte is implicitly 0.
 *
 * Algorithm (mirrors JS `decryptBuffer`):
 *   1. Validate the 16-byte magic (KGM_HEADER or VPR_HEADER).
 *   2. Read headerLen (uint32 LE at 0x10).
 *   3. Build 17-byte key from header[0x1C..0x2C] + 0x00.
 *   4. For each audio byte at position i:
 *        med8 = key[i % 17] xor audio[i]
 *        med8 ^= (med8 & 0x0F) << 4
 *        msk8 = getMask(i) xor ((msk8 & 0x0F) << 4)
 *        audio[i] = med8 xor msk8
 *        if VPR: audio[i] xor= VPR_MASK_DIFF[i % 17]
 *   5. Sniff format from decrypted magic bytes (FLAC/OGG/MP3/WAV/M4A/...).
 *
 * The cipher is involutory (nibble-swap is self-inverse, all ops are XOR), so
 * the same routine also produces synthetic encrypted test vectors.
 *
 * References:
 *   - src/decoders/kgm.js (huangbao/MyKgmWasm MIT reference, 2025)
 *   - tests/kgm.test.js (round-trip verification)
 */
object KgmDecoder {

    private const val KEY_SIZE = 17
    private const val HEADER_KEY_OFFSET = 0x1C
    private const val HEADER_LEN_OFFSET = 0x10
    private const val MAGIC_SIZE = 16
    private const val MIN_FILE_SIZE = 0x2C

    // 16-byte magic headers
    private val KGM_MAGIC = byteArrayOf(
        0x7C.toByte(), 0xD5.toByte(), 0x32.toByte(), 0xEB.toByte(),
        0x86.toByte(), 0x02.toByte(), 0x7F.toByte(), 0x4B.toByte(),
        0xA8.toByte(), 0xAF.toByte(), 0xA6.toByte(), 0x8E.toByte(),
        0x0F.toByte(), 0xFF.toByte(), 0x99.toByte(), 0x14.toByte(),
    )
    private val VPR_MAGIC = byteArrayOf(
        0x05.toByte(), 0x28.toByte(), 0xBC.toByte(), 0x96.toByte(),
        0xE9.toByte(), 0xE4.toByte(), 0x5A.toByte(), 0x43.toByte(),
        0x91.toByte(), 0xAA.toByte(), 0xBD.toByte(), 0xD0.toByte(),
        0x7A.toByte(), 0xF5.toByte(), 0x36.toByte(), 0x31.toByte(),
    )

    // 17-byte VPR post-mask (key[16] is implicitly 0, so this maps both 0x00..0x10)
    private val VPR_MASK_DIFF = byteArrayOf(
        0x25.toByte(), 0xDF.toByte(), 0xE8.toByte(), 0xA6.toByte(),
        0x75.toByte(), 0x1E.toByte(), 0x75.toByte(), 0x0E.toByte(),
        0x2F.toByte(), 0x80.toByte(), 0xF3.toByte(), 0x2D.toByte(),
        0xB8.toByte(), 0xB6.toByte(), 0xE3.toByte(), 0x11.toByte(),
        0x00.toByte(),
    )

    // 272-byte lookup tables (16*17 = 272)
    private val MASK_V2_PRE_DEF = byteArrayOf(
        0xB8.toByte(), 0xD5.toByte(), 0x3D.toByte(), 0xB2.toByte(), 0xE9.toByte(), 0xAF.toByte(), 0x78.toByte(), 0x8C.toByte(),
        0x83.toByte(), 0x33.toByte(), 0x71.toByte(), 0x51.toByte(), 0x76.toByte(), 0xA0.toByte(), 0xCD.toByte(), 0x37.toByte(),
        0x2F.toByte(), 0x3E.toByte(), 0x35.toByte(), 0x8D.toByte(), 0xA9.toByte(), 0xBE.toByte(), 0x98.toByte(), 0xB7.toByte(),
        0xE7.toByte(), 0x8C.toByte(), 0x22.toByte(), 0xCE.toByte(), 0x5A.toByte(), 0x61.toByte(), 0xDF.toByte(), 0x68.toByte(),
        0x69.toByte(), 0x89.toByte(), 0xFE.toByte(), 0xA5.toByte(), 0xB6.toByte(), 0xDE.toByte(), 0xA9.toByte(), 0x77.toByte(),
        0xFC.toByte(), 0xC8.toByte(), 0xBD.toByte(), 0xBD.toByte(), 0xE5.toByte(), 0x6D.toByte(), 0x3E.toByte(), 0x5A.toByte(),
        0x36.toByte(), 0xEF.toByte(), 0x69.toByte(), 0x4E.toByte(), 0xBE.toByte(), 0xE1.toByte(), 0xE9.toByte(), 0x66.toByte(),
        0x1C.toByte(), 0xF3.toByte(), 0xD9.toByte(), 0x02.toByte(), 0xB6.toByte(), 0xF2.toByte(), 0x12.toByte(), 0x9B.toByte(),
        0x44.toByte(), 0xD0.toByte(), 0x6F.toByte(), 0xB9.toByte(), 0x35.toByte(), 0x89.toByte(), 0xB6.toByte(), 0x46.toByte(),
        0x6D.toByte(), 0x73.toByte(), 0x82.toByte(), 0x06.toByte(), 0x69.toByte(), 0xC1.toByte(), 0xED.toByte(), 0xD7.toByte(),
        0x85.toByte(), 0xC2.toByte(), 0x30.toByte(), 0xDF.toByte(), 0xA2.toByte(), 0x62.toByte(), 0xBE.toByte(), 0x79.toByte(),
        0x2D.toByte(), 0x62.toByte(), 0x62.toByte(), 0x3D.toByte(), 0x0D.toByte(), 0x7E.toByte(), 0xBE.toByte(), 0x48.toByte(),
        0x89.toByte(), 0x23.toByte(), 0x02.toByte(), 0xA0.toByte(), 0xE4.toByte(), 0xD5.toByte(), 0x75.toByte(), 0x51.toByte(),
        0x32.toByte(), 0x02.toByte(), 0x53.toByte(), 0xFD.toByte(), 0x16.toByte(), 0x3A.toByte(), 0x21.toByte(), 0x3B.toByte(),
        0x16.toByte(), 0x0F.toByte(), 0xC3.toByte(), 0xB2.toByte(), 0xBB.toByte(), 0xB3.toByte(), 0xE2.toByte(), 0xBA.toByte(),
        0x3A.toByte(), 0x3D.toByte(), 0x13.toByte(), 0xEC.toByte(), 0xF6.toByte(), 0x01.toByte(), 0x45.toByte(), 0x84.toByte(),
        0xA5.toByte(), 0x70.toByte(), 0x0F.toByte(), 0x93.toByte(), 0x49.toByte(), 0x0C.toByte(), 0x64.toByte(), 0xCD.toByte(),
        0x31.toByte(), 0xD5.toByte(), 0xCC.toByte(), 0x4C.toByte(), 0x07.toByte(), 0x01.toByte(), 0x9E.toByte(), 0x00.toByte(),
        0x1A.toByte(), 0x23.toByte(), 0x90.toByte(), 0xBF.toByte(), 0x88.toByte(), 0x1E.toByte(), 0x3B.toByte(), 0xAB.toByte(),
        0xA6.toByte(), 0x3E.toByte(), 0xC4.toByte(), 0x73.toByte(), 0x47.toByte(), 0x10.toByte(), 0x7E.toByte(), 0x3B.toByte(),
        0x5E.toByte(), 0xBC.toByte(), 0xE3.toByte(), 0x00.toByte(), 0x84.toByte(), 0xFF.toByte(), 0x09.toByte(), 0xD4.toByte(),
        0xE0.toByte(), 0x89.toByte(), 0x0F.toByte(), 0x5B.toByte(), 0x58.toByte(), 0x70.toByte(), 0x4F.toByte(), 0xFB.toByte(),
        0x65.toByte(), 0xD8.toByte(), 0x5C.toByte(), 0x53.toByte(), 0x1B.toByte(), 0xD3.toByte(), 0xC8.toByte(), 0xC6.toByte(),
        0xBF.toByte(), 0xEF.toByte(), 0x98.toByte(), 0xB0.toByte(), 0x50.toByte(), 0x4F.toByte(), 0x0F.toByte(), 0xEA.toByte(),
        0xE5.toByte(), 0x83.toByte(), 0x58.toByte(), 0x8C.toByte(), 0x28.toByte(), 0x2C.toByte(), 0x84.toByte(), 0x67.toByte(),
        0xCD.toByte(), 0xD0.toByte(), 0x9E.toByte(), 0x47.toByte(), 0xDB.toByte(), 0x27.toByte(), 0x50.toByte(), 0xCA.toByte(),
        0xF4.toByte(), 0x63.toByte(), 0x63.toByte(), 0xE8.toByte(), 0x97.toByte(), 0x7F.toByte(), 0x1B.toByte(), 0x4B.toByte(),
        0x0C.toByte(), 0xC2.toByte(), 0xC1.toByte(), 0x21.toByte(), 0x4C.toByte(), 0xCC.toByte(), 0x58.toByte(), 0xF5.toByte(),
        0x94.toByte(), 0x52.toByte(), 0xA3.toByte(), 0xF3.toByte(), 0xD3.toByte(), 0xE0.toByte(), 0x68.toByte(), 0xF4.toByte(),
        0x00.toByte(), 0x23.toByte(), 0xF3.toByte(), 0x5E.toByte(), 0x0A.toByte(), 0x7B.toByte(), 0x93.toByte(), 0xDD.toByte(),
        0xAB.toByte(), 0x12.toByte(), 0xB2.toByte(), 0x13.toByte(), 0xE8.toByte(), 0x84.toByte(), 0xD7.toByte(), 0xA7.toByte(),
        0x9F.toByte(), 0x0F.toByte(), 0x32.toByte(), 0x4C.toByte(), 0x55.toByte(), 0x1D.toByte(), 0x04.toByte(), 0x36.toByte(),
        0x52.toByte(), 0xDC.toByte(), 0x03.toByte(), 0xF3.toByte(), 0xF9.toByte(), 0x4E.toByte(), 0x42.toByte(), 0xE9.toByte(),
        0x3D.toByte(), 0x61.toByte(), 0xEF.toByte(), 0x7C.toByte(), 0xB6.toByte(), 0xB3.toByte(), 0x93.toByte(), 0x50.toByte(),
    )

    private val TABLE1 = byteArrayOf(
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x01.toByte(), 0x21.toByte(), 0x01.toByte(), 0x61.toByte(), 0x01.toByte(), 0x21.toByte(), 0x01.toByte(),
        0xe1.toByte(), 0x01.toByte(), 0x21.toByte(), 0x01.toByte(), 0x61.toByte(), 0x01.toByte(), 0x21.toByte(), 0x01.toByte(),
        0xd2.toByte(), 0x23.toByte(), 0x02.toByte(), 0x02.toByte(), 0x42.toByte(), 0x42.toByte(), 0x02.toByte(), 0x02.toByte(),
        0xc2.toByte(), 0xc2.toByte(), 0x02.toByte(), 0x02.toByte(), 0x42.toByte(), 0x42.toByte(), 0x02.toByte(), 0x02.toByte(),
        0xd3.toByte(), 0xd3.toByte(), 0x02.toByte(), 0x03.toByte(), 0x63.toByte(), 0x43.toByte(), 0x63.toByte(), 0x03.toByte(),
        0xe3.toByte(), 0xc3.toByte(), 0xe3.toByte(), 0x03.toByte(), 0x63.toByte(), 0x43.toByte(), 0x63.toByte(), 0x03.toByte(),
        0x94.toByte(), 0xb4.toByte(), 0x94.toByte(), 0x65.toByte(), 0x04.toByte(), 0x04.toByte(), 0x04.toByte(), 0x04.toByte(),
        0x84.toByte(), 0x84.toByte(), 0x84.toByte(), 0x84.toByte(), 0x04.toByte(), 0x04.toByte(), 0x04.toByte(), 0x04.toByte(),
        0x95.toByte(), 0x95.toByte(), 0x95.toByte(), 0x95.toByte(), 0x04.toByte(), 0x05.toByte(), 0x25.toByte(), 0x05.toByte(),
        0xe5.toByte(), 0x85.toByte(), 0xa5.toByte(), 0x85.toByte(), 0xe5.toByte(), 0x05.toByte(), 0x25.toByte(), 0x05.toByte(),
        0xd6.toByte(), 0xb6.toByte(), 0x96.toByte(), 0xb6.toByte(), 0xd6.toByte(), 0x27.toByte(), 0x06.toByte(), 0x06.toByte(),
        0xc6.toByte(), 0xc6.toByte(), 0x86.toByte(), 0x86.toByte(), 0xc6.toByte(), 0xc6.toByte(), 0x06.toByte(), 0x06.toByte(),
        0xd7.toByte(), 0xd7.toByte(), 0x97.toByte(), 0x97.toByte(), 0xd7.toByte(), 0xd7.toByte(), 0x06.toByte(), 0x07.toByte(),
        0xe7.toByte(), 0xc7.toByte(), 0xe7.toByte(), 0x87.toByte(), 0xe7.toByte(), 0xc7.toByte(), 0xe7.toByte(), 0x07.toByte(),
        0x18.toByte(), 0x38.toByte(), 0x18.toByte(), 0x78.toByte(), 0x18.toByte(), 0x38.toByte(), 0x18.toByte(), 0xe9.toByte(),
        0x08.toByte(), 0x08.toByte(), 0x08.toByte(), 0x08.toByte(), 0x08.toByte(), 0x08.toByte(), 0x08.toByte(), 0x08.toByte(),
        0x19.toByte(), 0x19.toByte(), 0x19.toByte(), 0x19.toByte(), 0x19.toByte(), 0x19.toByte(), 0x19.toByte(), 0x19.toByte(),
        0x08.toByte(), 0x09.toByte(), 0x29.toByte(), 0x09.toByte(), 0x69.toByte(), 0x09.toByte(), 0x29.toByte(), 0x09.toByte(),
        0xda.toByte(), 0x3a.toByte(), 0x1a.toByte(), 0x3a.toByte(), 0x5a.toByte(), 0x3a.toByte(), 0x1a.toByte(), 0x3a.toByte(),
        0xda.toByte(), 0x2b.toByte(), 0x0a.toByte(), 0x0a.toByte(), 0x4a.toByte(), 0x4a.toByte(), 0x0a.toByte(), 0x0a.toByte(),
        0xdb.toByte(), 0xdb.toByte(), 0x1b.toByte(), 0x1b.toByte(), 0x5b.toByte(), 0x5b.toByte(), 0x1b.toByte(), 0x1b.toByte(),
        0xdb.toByte(), 0xdb.toByte(), 0x0a.toByte(), 0x0b.toByte(), 0x6b.toByte(), 0x4b.toByte(), 0x6b.toByte(), 0x0b.toByte(),
        0x9c.toByte(), 0xbc.toByte(), 0x9c.toByte(), 0x7c.toByte(), 0x1c.toByte(), 0x3c.toByte(), 0x1c.toByte(), 0x7c.toByte(),
        0x9c.toByte(), 0xbc.toByte(), 0x9c.toByte(), 0x6d.toByte(), 0x0c.toByte(), 0x0c.toByte(), 0x0c.toByte(), 0x0c.toByte(),
        0x9d.toByte(), 0x9d.toByte(), 0x9d.toByte(), 0x9d.toByte(), 0x1d.toByte(), 0x1d.toByte(), 0x1d.toByte(), 0x1d.toByte(),
        0x9d.toByte(), 0x9d.toByte(), 0x9d.toByte(), 0x9d.toByte(), 0x0c.toByte(), 0x0d.toByte(), 0x2d.toByte(), 0x0d.toByte(),
        0xde.toByte(), 0xbe.toByte(), 0x9e.toByte(), 0xbe.toByte(), 0xde.toByte(), 0x3e.toByte(), 0x1e.toByte(), 0x3e.toByte(),
        0xde.toByte(), 0xbe.toByte(), 0x9e.toByte(), 0xbe.toByte(), 0xde.toByte(), 0x2f.toByte(), 0x0e.toByte(), 0x0e.toByte(),
        0xdf.toByte(), 0xdf.toByte(), 0x9f.toByte(), 0x9f.toByte(), 0xdf.toByte(), 0xdf.toByte(), 0x1f.toByte(), 0x1f.toByte(),
        0xdf.toByte(), 0xdf.toByte(), 0x9f.toByte(), 0x9f.toByte(), 0xdf.toByte(), 0xdf.toByte(), 0x0e.toByte(), 0x0f.toByte(),
        0x00.toByte(), 0x20.toByte(), 0x00.toByte(), 0x60.toByte(), 0x00.toByte(), 0x20.toByte(), 0x00.toByte(), 0xe0.toByte(),
        0x00.toByte(), 0x20.toByte(), 0x00.toByte(), 0x60.toByte(), 0x00.toByte(), 0x20.toByte(), 0x00.toByte(), 0xf1.toByte(),
    )

    private val TABLE2 = byteArrayOf(
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x01.toByte(), 0x23.toByte(), 0x01.toByte(), 0x67.toByte(), 0x01.toByte(), 0x23.toByte(), 0x01.toByte(),
        0xef.toByte(), 0x01.toByte(), 0x23.toByte(), 0x01.toByte(), 0x67.toByte(), 0x01.toByte(), 0x23.toByte(), 0x01.toByte(),
        0xdf.toByte(), 0x21.toByte(), 0x02.toByte(), 0x02.toByte(), 0x46.toByte(), 0x46.toByte(), 0x02.toByte(), 0x02.toByte(),
        0xce.toByte(), 0xce.toByte(), 0x02.toByte(), 0x02.toByte(), 0x46.toByte(), 0x46.toByte(), 0x02.toByte(), 0x02.toByte(),
        0xde.toByte(), 0xde.toByte(), 0x02.toByte(), 0x03.toByte(), 0x65.toByte(), 0x47.toByte(), 0x65.toByte(), 0x03.toByte(),
        0xed.toByte(), 0xcf.toByte(), 0xed.toByte(), 0x03.toByte(), 0x65.toByte(), 0x47.toByte(), 0x65.toByte(), 0x03.toByte(),
        0x9d.toByte(), 0xbf.toByte(), 0x9d.toByte(), 0x63.toByte(), 0x04.toByte(), 0x04.toByte(), 0x04.toByte(), 0x04.toByte(),
        0x8c.toByte(), 0x8c.toByte(), 0x8c.toByte(), 0x8c.toByte(), 0x04.toByte(), 0x04.toByte(), 0x04.toByte(), 0x04.toByte(),
        0x9c.toByte(), 0x9c.toByte(), 0x9c.toByte(), 0x9c.toByte(), 0x04.toByte(), 0x05.toByte(), 0x27.toByte(), 0x05.toByte(),
        0xeb.toByte(), 0x8d.toByte(), 0xaf.toByte(), 0x8d.toByte(), 0xeb.toByte(), 0x05.toByte(), 0x27.toByte(), 0x05.toByte(),
        0xdb.toByte(), 0xbd.toByte(), 0x9f.toByte(), 0xbd.toByte(), 0xdb.toByte(), 0x25.toByte(), 0x06.toByte(), 0x06.toByte(),
        0xca.toByte(), 0xca.toByte(), 0x8e.toByte(), 0x8e.toByte(), 0xca.toByte(), 0xca.toByte(), 0x06.toByte(), 0x06.toByte(),
        0xda.toByte(), 0xda.toByte(), 0x9e.toByte(), 0x9e.toByte(), 0xda.toByte(), 0xda.toByte(), 0x06.toByte(), 0x07.toByte(),
        0xe9.toByte(), 0xcb.toByte(), 0xe9.toByte(), 0x8f.toByte(), 0xe9.toByte(), 0xcb.toByte(), 0xe9.toByte(), 0x07.toByte(),
        0x19.toByte(), 0x3b.toByte(), 0x19.toByte(), 0x7f.toByte(), 0x19.toByte(), 0x3b.toByte(), 0x19.toByte(), 0xe7.toByte(),
        0x08.toByte(), 0x08.toByte(), 0x08.toByte(), 0x08.toByte(), 0x08.toByte(), 0x08.toByte(), 0x08.toByte(), 0x08.toByte(),
        0x18.toByte(), 0x18.toByte(), 0x18.toByte(), 0x18.toByte(), 0x18.toByte(), 0x18.toByte(), 0x18.toByte(), 0x18.toByte(),
        0x08.toByte(), 0x09.toByte(), 0x2b.toByte(), 0x09.toByte(), 0x6f.toByte(), 0x09.toByte(), 0x2b.toByte(), 0x09.toByte(),
        0xd7.toByte(), 0x39.toByte(), 0x1b.toByte(), 0x39.toByte(), 0x5f.toByte(), 0x39.toByte(), 0x1b.toByte(), 0x39.toByte(),
        0xd7.toByte(), 0x29.toByte(), 0x0a.toByte(), 0x0a.toByte(), 0x4e.toByte(), 0x4e.toByte(), 0x0a.toByte(), 0x0a.toByte(),
        0xd6.toByte(), 0xd6.toByte(), 0x1a.toByte(), 0x1a.toByte(), 0x5e.toByte(), 0x5e.toByte(), 0x1a.toByte(), 0x1a.toByte(),
        0xd6.toByte(), 0xd6.toByte(), 0x0a.toByte(), 0x0b.toByte(), 0x6d.toByte(), 0x4f.toByte(), 0x6d.toByte(), 0x0b.toByte(),
        0x95.toByte(), 0xb7.toByte(), 0x95.toByte(), 0x7b.toByte(), 0x1d.toByte(), 0x3f.toByte(), 0x1d.toByte(), 0x7b.toByte(),
        0x95.toByte(), 0xb7.toByte(), 0x95.toByte(), 0x6b.toByte(), 0x0c.toByte(), 0x0c.toByte(), 0x0c.toByte(), 0x0c.toByte(),
        0x94.toByte(), 0x94.toByte(), 0x94.toByte(), 0x94.toByte(), 0x1c.toByte(), 0x1c.toByte(), 0x1c.toByte(), 0x1c.toByte(),
        0x94.toByte(), 0x94.toByte(), 0x94.toByte(), 0x94.toByte(), 0x0c.toByte(), 0x0d.toByte(), 0x2f.toByte(), 0x0d.toByte(),
        0xd3.toByte(), 0xb5.toByte(), 0x97.toByte(), 0xb5.toByte(), 0xd3.toByte(), 0x3d.toByte(), 0x1f.toByte(), 0x3d.toByte(),
        0xd3.toByte(), 0xb5.toByte(), 0x97.toByte(), 0xb5.toByte(), 0xd3.toByte(), 0x2d.toByte(), 0x0e.toByte(), 0x0e.toByte(),
        0xd2.toByte(), 0xd2.toByte(), 0x96.toByte(), 0x96.toByte(), 0xd2.toByte(), 0xd2.toByte(), 0x1e.toByte(), 0x1e.toByte(),
        0xd2.toByte(), 0xd2.toByte(), 0x96.toByte(), 0x96.toByte(), 0xd2.toByte(), 0xd2.toByte(), 0x0e.toByte(), 0x0f.toByte(),
        0x00.toByte(), 0x22.toByte(), 0x00.toByte(), 0x66.toByte(), 0x00.toByte(), 0x22.toByte(), 0x00.toByte(), 0xee.toByte(),
        0x00.toByte(), 0x22.toByte(), 0x00.toByte(), 0x66.toByte(), 0x00.toByte(), 0x22.toByte(), 0x00.toByte(), 0xfe.toByte(),
    )

    /**
     * Decrypt a KGM / KGMA / VPR buffer. The per-file 16-byte key is read from
     * the header at offset 0x1C; the [variant] hint selects the magic (KGM or
     * VPR) and toggles the VPR post-mask. KGMA shares the KGM magic.
     */
    fun decrypt(input: ByteArray, variant: String): AudioData {
        require(variant in setOf("kgm", "kgma", "vpr")) {
            "Unsupported KGM variant: $variant"
        }
        require(input.size >= MIN_FILE_SIZE) {
            "KGM/VPR: file too small (${input.size} < $MIN_FILE_SIZE)"
        }

        val magic = input.copyOfRange(0, MAGIC_SIZE)
        val isVpr = when {
            magic.contentEquals(VPR_MAGIC) -> true
            magic.contentEquals(KGM_MAGIC) -> false
            else -> throw IllegalArgumentException(
                "KGM/VPR: bad magic, expected KGM or VPR header, got ${magic.joinToString("") { "%02x".format(it) }}"
            )
        }

        val headerLen = readUInt32LE(input, HEADER_LEN_OFFSET)
        require(headerLen >= MIN_FILE_SIZE && headerLen <= input.size) {
            "KGM/VPR: invalid header length $headerLen"
        }

        // 17-byte key: 16 bytes from header, then key[16] = 0
        val key = ByteArray(KEY_SIZE)
        System.arraycopy(input, HEADER_KEY_OFFSET, key, 0, 16)
        // key[16] is already 0

        val audio = input.copyOfRange(headerLen, input.size)
        for (i in audio.indices) {
            var med8 = (key[i % KEY_SIZE].toInt() xor audio[i].toInt()) and 0xff
            med8 = med8 xor ((med8 and 0x0f) shl 4) and 0xff
            var msk8 = getMask(i) and 0xff
            msk8 = msk8 xor ((msk8 and 0x0f) shl 4) and 0xff
            var out = (med8 xor msk8) and 0xff
            if (isVpr) {
                out = (out xor VPR_MASK_DIFF[i % KEY_SIZE].toInt()) and 0xff
            }
            audio[i] = out.toByte()
        }
        return AudioData(bytes = audio, format = inferFormat(audio))
    }

    private fun getMask(pos: Int): Int {
        var offset = pos ushr 4
        var value = 0
        while (offset >= 0x11) {
            value = value xor TABLE1[offset % 272].toInt()
            offset = offset ushr 4
            value = value xor TABLE2[offset % 272].toInt()
            offset = offset ushr 4
        }
        return value xor MASK_V2_PRE_DEF[pos % 272].toInt()
    }

    private fun readUInt32LE(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xff) or
            ((buf[offset + 1].toInt() and 0xff) shl 8) or
            ((buf[offset + 2].toInt() and 0xff) shl 16) or
            ((buf[offset + 3].toInt() and 0xff) shl 24)
    }

    /**
     * Sniff audio format from decrypted magic bytes. Mirrors the JS `inferFormat`.
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
        // MP3 sync 0xFF 0xEx
        if (audio[0] == 0xff.toByte() && (audio[1].toInt() and 0xe0) == 0xe0) return "mp3"
        // M4A: bytes 4..8 == "ftyp"
        if (audio.size >= 8 && audio[4] == 0x66.toByte() && audio[5] == 0x74.toByte() &&
            audio[6] == 0x79.toByte() && audio[7] == 0x70.toByte()) return "m4a"
        return "mp3"
    }
}
