package com.openconverter.app.decoders

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * NCM (NetEase Cloud Music) decoder — 1:1 port of `src/decoders/ncm.js`.
 *
 * Pipeline:
 *  1. Read u32 LE keyLen at offset 10, read keyEnc, XOR each byte with 0x64,
 *     AES-128-ECB decrypt with CORE_KEY, PKCS7 unpad, drop the 17-byte
 *     "neteasecloudmusic\0" prefix → rc4Key.
 *  2. Build the standard RC4 S-box from rc4Key.
 *  3. Optionally parse the meta block (XOR 0x63 / b64 / AES with META_KEY).
 *  4. Skip 5-byte gap, u32 imageSpace, u32 imageSize, image bytes,
 *     (imageSpace - imageSize) padding.
 *  5. Apply the modified-RC4 keystream over the audio bytes (key[i] =
 *     S[(S[i] + S[(i + S[i]) & 0xff]) & 0xff]; out[i] = data[i] ^ key[(i+1) % 256]).
 */
object NcmDecoder : Decoder {

    override val supportedExtensions: Set<String> = setOf(".ncm")

    private val MAGIC = "CTENFDAM".toByteArray(Charsets.US_ASCII)
    private val CORE_KEY = hexToBytes("687A4852416D736F356B496E62617857")
    private val META_KEY = hexToBytes("2331346C6A6B5F215C5D2630553C2728")
    private const val PREFIX_LEN = 17 // "neteasecloudmusic\0"

    override fun decrypt(input: ByteArray): DecryptResult {
        require(input.size >= 8) { "NCM: too short" }
        for (i in MAGIC.indices) require(input[i] == MAGIC[i]) { "NCM: bad magic" }

        var off = 10 // 8 magic + 2 gap

        val keyLen = readU32LE(input, off); off += 4
        require(keyLen in 1..(input.size - off)) { "NCM: invalid keyLen $keyLen" }
        val keyEnc = ByteArray(keyLen)
        System.arraycopy(input, off, keyEnc, 0, keyLen)
        off += keyLen
        for (i in keyEnc.indices) keyEnc[i] = (keyEnc[i].toInt() xor 0x64).toByte()
        val keyPlain = pkcs7Unpad(aesEcbDecrypt(keyEnc, CORE_KEY))
        require(keyPlain.size >= PREFIX_LEN) { "NCM: key block too short" }
        // 17-byte "neteasecloudmusic\0" prefix
        val rc4Key = keyPlain.copyOfRange(PREFIX_LEN, keyPlain.size)

        val sBox = buildRc4Sbox(rc4Key)

        // Meta (we capture but do not propagate fields beyond format hint).
        val metaLen = readU32LE(input, off); off += 4
        var meta: String? = null
        if (metaLen > 0) {
            require(off + metaLen <= input.size) { "NCM: meta length overruns" }
            val metaEnc = ByteArray(metaLen)
            System.arraycopy(input, off, metaEnc, 0, metaLen)
            off += metaLen
            for (i in metaEnc.indices) metaEnc[i] = (metaEnc[i].toInt() xor 0x63).toByte()
            // first 22 bytes are a fixed prefix in JS reference
            val b64 = String(metaEnc, 22, metaEnc.size - 22, Charsets.US_ASCII)
            try {
                val metaAes = aesEcbDecrypt(java.util.Base64.getDecoder().decode(b64), META_KEY)
                val metaJson = String(pkcs7Unpad(metaAes), Charsets.UTF_8).removePrefix("music:")
                // not parsing JSON in v1; only capturing the string for callers that want it
                meta = metaJson
            } catch (_: Exception) {
                meta = null
            }
        }

        // 5-byte gap
        off += 5
        val imageSpace = readU32LE(input, off); off += 4
        // imageSize: read but unused — image data + (imageSpace-imageSize) padding
        // are skipped together via `imageSpace` advance below. Read it to advance `off`.
        off += 4
        require(off + imageSpace <= input.size) { "NCM: image overruns" }
        // skip image_size bytes + (imageSpace - imageSize) padding
        off += imageSpace

        require(off <= input.size) { "NCM: header parsing past end" }
        val encryptedAudio = ByteArray(input.size - off)
        System.arraycopy(input, off, encryptedAudio, 0, encryptedAudio.size)

        val audio = rc4Decrypt(sBox, encryptedAudio)
        // NCM meta says format; if absent, sniff.
        val format = FormatSniffer.sniff(audio)
        return DecryptResult(audio = audio, format = format, meta = meta)
    }

    private fun aesEcbDecrypt(block: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(block)
    }

    private fun pkcs7Unpad(buf: ByteArray): ByteArray {
        val pad = buf[buf.size - 1].toInt() and 0xff
        require(pad in 1..16) { "Invalid PKCS7 padding $pad" }
        return buf.copyOfRange(0, buf.size - pad)
    }

    private fun buildRc4Sbox(key: ByteArray): IntArray {
        val s = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + s[i] + (key[i % key.size].toInt() and 0xff)) and 0xff
            val tmp = s[i]; s[i] = s[j]; s[j] = tmp
        }
        return s
    }

    /**
     * Modified RC4 (matches ncm.js exactly):
     *   k[i] = S[(S[i] + S[(i + S[i]) & 0xff]) & 0xff]
     *   out[i] = data[i] ^ k[(i + 1) % 256]
     */
    private fun rc4Decrypt(s: IntArray, data: ByteArray): ByteArray {
        val k = IntArray(256)
        for (i in 0 until 256) k[i] = s[(s[i] + s[(i + s[i]) and 0xff]) and 0xff]
        val out = ByteArray(data.size)
        for (i in data.indices) out[i] = ((data[i].toInt() and 0xff) xor k[(i + 1) % 256]).toByte()
        return out
    }

    private fun readU32LE(buf: ByteArray, off: Int): Int =
        ByteBuffer.wrap(buf, off, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun hexToBytes(hex: String): ByteArray {
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) out[i] = ((hex[i * 2].digitToInt(16) shl 4) or hex[i * 2 + 1].digitToInt(16)).toByte()
        return out
    }
}
