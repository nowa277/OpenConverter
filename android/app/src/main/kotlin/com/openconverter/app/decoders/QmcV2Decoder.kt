package com.openconverter.app.decoders

import java.util.Base64

/**
 * Pure Kotlin port of src/decoders/qmc.js — QMCv2 decoder.
 *
 * Supports: .mflac0, .mflac, .mgg1, .mgg, .bkc*.
 * Requires the user-provided QQ Music ekey (base64 string from client DB).
 * Throws MissingEkeyException if ekey is null/blank; InvalidEkeyException if
 * the ekey base64-decodes to zero bytes.
 *
 * Algorithm (mirrors JS `decryptV2Buffer`):
 *   1. base64-decode ekey → raw ekey bytes.
 *   2. Derive 128-byte working key via keyCompress(ekey).
 *   3. For each byte, XOR with working_key[o % 128] where
 *      o = offset > 0x7fff ? offset % 0x7fff : offset.
 *   4. Sniff format from decrypted magic bytes (FLAC/OGG/MP3/WAV/M4A).
 *
 * References:
 *   - src/decoders/qmc.js (keyCompress, shiftMix, decryptV2Buffer, inferFormat)
 *   - ikun0014/pyqmc-rust src/map/key.rs (key_compress cross-checked test vector)
 */
object QmcV2Decoder {

    private const val V1_OFFSET_BOUNDARY = 0x7fff
    private const val V2_KEY_SIZE = 128
    private const val KEY_COMPRESS_INDEX_OFFSET = 71214

    /**
     * Decrypt a QMCv2 buffer. [ekey] is the base64-encoded QQ Music ekey.
     * [extensionFormat] is the fallback format hint if the audio magic cannot
     * be recognised (e.g. ".mflac0" → "flac", ".mgg" → "ogg").
     */
    fun decrypt(
        input: ByteArray,
        ekey: String?,
        extensionFormat: String = "mp3",
    ): AudioData {
        if (ekey.isNullOrBlank()) throw MissingEkeyException()

        val ekeyBytes = try {
            Base64.getDecoder().decode(ekey)
        } catch (e: IllegalArgumentException) {
            throw InvalidEkeyException("not valid base64: ${e.message}")
        }
        if (ekeyBytes.isEmpty()) {
            throw InvalidEkeyException("base64 decoded to 0 bytes")
        }

        val key = keyCompress(ekeyBytes)
        val out = ByteArray(input.size)
        for (i in input.indices) {
            val off = if (i > V1_OFFSET_BOUNDARY) i % V1_OFFSET_BOUNDARY else i
            out[i] = (input[i].toInt() xor key[off % V2_KEY_SIZE].toInt()).toByte()
        }
        return AudioData(bytes = out, format = inferFormat(out, extensionFormat))
    }

    /**
     * (byte << shift) | (byte >> shift), masked to 8 bits. NOT a rotation —
     * matches the JS reference (ikun0014/pyqmc-rust).
     */
    private fun shiftMix(byte: Int, shift: Int): Int {
        val s = shift and 7
        if (s == 0) return byte and 0xff
        return (((byte and 0xff) shl s) or ((byte and 0xff) ushr s)) and 0xff
    }

    /**
     * Derive a 128-byte working key from the raw ekey bytes.
     * out[i] = shiftMix(ekey[(i*i + 71214) % n], (idx + 4) % 8)
     */
    private fun keyCompress(ekey: ByteArray): ByteArray {
        val n = ekey.size
        val out = ByteArray(V2_KEY_SIZE)
        for (i in 0 until V2_KEY_SIZE) {
            val idx = (i * i + KEY_COMPRESS_INDEX_OFFSET) % n
            val shift = (idx + 4) % 8
            out[i] = shiftMix(ekey[idx].toInt(), shift).toByte()
        }
        return out
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

class MissingEkeyException :
    RuntimeException("QQ Music ekey is required for QMCv2 files. Set it in Settings → ekey.")

class InvalidEkeyException(message: String) :
    RuntimeException("Invalid ekey: $message")
