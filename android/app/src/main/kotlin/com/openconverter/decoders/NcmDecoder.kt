package com.openconverter.decoders

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * NCM (NetEase Cloud Music) decoder — pure JVM crypto, no native deps.
 *
 * Port of src/decoders/ncm.js. Algorithm (see ncmdump):
 *
 *   Offset  Size  Content
 *   ------  ----  --------------------------------------------------------
 *   0       8     Magic "CTENFDAM"
 *   8       2     Gap (skip)
 *   10      4     key_length (LE)
 *   14      N     Encrypted key data (XOR each byte with 0x64, then
 *                  AES-128-ECB decrypt with core_key, PKCS7 unpad,
 *                  skip 17 bytes "neteasecloudmusic\0")
 *   ...     4     meta_length (LE)  [0 if no meta]
 *   ...     M     Meta JSON (XOR 0x63, base64 decode, AES-128-ECB with meta_key, PKCS7 unpad)
 *   ...     5     Gap
 *   ...     4     image_space (LE)
 *   ...     4     image_size (LE)
 *   ...     S     image bytes
 *   ...     ?     (image_space - image_size) bytes of padding
 *   ...     rest  Audio data (RC4-encrypted with the modified S-box built from the key)
 *
 * Verified against 14 real NCM samples — sha256 of decrypted bytes must
 * match desktop reference EXACTLY.
 *
 * core_key = "687A4852416D736F356B496E62617857" (hex of "hzHRAmso5kInbaxW")
 */
object NcmDecoder {
    private val MAGIC = "CTENFDAM".toByteArray(Charsets.US_ASCII)
    private val CORE_KEY = "687A4852416D736F356B496E62617857".hexToBytes()
    private const val PREFIX_LEN = 17  // "neteasecloudmusic" + "\0"

    fun decrypt(input: ByteArray): AudioData {
        require(input.size >= 8 && MAGIC.contentEquals(input.copyOfRange(0, 8))) {
            "Not a valid NCM file (missing CTENFDAM magic)"
        }

        var offset = 10  // skip 8 magic + 2 gap

        // Read key_length (LE u32)
        val keyLength = readLe32(input, offset); offset += 4
        require(keyLength in 1..0x10000) { "Invalid key_length: $keyLength" }

        // Read + XOR-decrypt key block
        val keyEnc = input.copyOfRange(offset, offset + keyLength)
        offset += keyLength
        for (i in keyEnc.indices) keyEnc[i] = (keyEnc[i].toInt() xor 0x64).toByte()

        // AES-128-ECB decrypt + PKCS7 unpad
        val keyPlain = aesEcbDecrypt(keyEnc, CORE_KEY)
        val keyUnpadded = pkcs7Unpad(keyPlain)
        require(
            keyUnpadded.size >= PREFIX_LEN &&
                "neteasecloudmusic".toByteArray(Charsets.US_ASCII).contentEquals(
                    keyUnpadded.copyOfRange(0, PREFIX_LEN)
                )
        ) { "Key block did not start with neteasecloudmusic" }
        val rc4Key = keyUnpadded.copyOfRange(PREFIX_LEN, keyUnpadded.size)

        // Meta block — we don't decode it, just skip
        val metaLength = readLe32(input, offset); offset += 4
        if (metaLength > 0) {
            require(offset + metaLength <= input.size) { "Meta length exceeds file size" }
            offset += metaLength
        }

        // 5-byte gap
        offset += 5

        // Image block: 4-byte space, 4-byte size, image_size bytes, then (image_space - image_size) padding
        val imageSpace = readLe32(input, offset); offset += 4
        val imageSize = readLe32(input, offset); offset += 4
        require(offset + imageSize <= input.size) { "Image extends past end of file" }
        offset += imageSize
        offset += (imageSpace - imageSize)  // padding

        require(offset <= input.size) { "Header parsing ran past end of file" }

        // Encrypted audio — RC4-decrypt with the modified S-box
        val encryptedAudio = input.copyOfRange(offset, input.size)
        val sBox = buildRc4Sbox(rc4Key)
        val decrypted = rc4Decrypt(sBox, encryptedAudio)

        return AudioData(bytes = decrypted, format = "mp3")
    }

    private fun readLe32(buf: ByteArray, offset: Int): Int =
        (buf[offset].toInt() and 0xFF) or
            ((buf[offset + 1].toInt() and 0xFF) shl 8) or
            ((buf[offset + 2].toInt() and 0xFF) shl 16) or
            ((buf[offset + 3].toInt() and 0xFF) shl 24)

    private fun aesEcbDecrypt(block: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(block)
    }

    private fun pkcs7Unpad(buf: ByteArray): ByteArray {
        val pad = buf[buf.size - 1].toInt() and 0xFF
        require(pad in 1..16) { "Invalid PKCS7 padding: $pad" }
        return buf.copyOfRange(0, buf.size - pad)
    }

    private fun buildRc4Sbox(key: ByteArray): ByteArray {
        val s = ByteArray(256) { it.toByte() }
        var j = 0
        for (i in 0 until 256) {
            j = (j + (s[i].toInt() and 0xFF) + (key[i % key.size].toInt() and 0xFF)) and 0xFF
            val tmp = s[i]; s[i] = s[j]; s[j] = tmp
        }
        return s
    }

    /**
     * Modified RC4 from ncmdump: the 256-byte keystream is built from S,
     * then repeated to length data.length + 1, and the first byte is skipped.
     * So for data[i], the keystream byte used is k[(i + 1) % 256].
     */
    private fun rc4Decrypt(s: ByteArray, data: ByteArray): ByteArray {
        val k = ByteArray(256)
        for (i in 0 until 256) {
            val idx = ((s[i].toInt() and 0xFF) + (s[(i + (s[i].toInt() and 0xFF)) and 0xFF].toInt() and 0xFF)) and 0xFF
            k[i] = s[idx]
        }
        val out = ByteArray(data.size)
        for (i in data.indices) {
            out[i] = (data[i].toInt() xor (k[(i + 1) % 256].toInt() and 0xFF)).toByte()
        }
        return out
    }

    private fun String.hexToBytes(): ByteArray =
        ByteArray(length / 2) { i ->
            (((this[i * 2].digitToInt(16) shl 4) or this[i * 2 + 1].digitToInt(16))).toByte()
        }
}
