package com.openconverter.app.decoders

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

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
object NcmDecoder : StreamingDecoder {

    override val supportedExtensions: Set<String> = setOf(".ncm")

    private val MAGIC = "CTENFDAM".toByteArray(Charsets.US_ASCII)
    private val CORE_KEY = hexToBytes("687A4852416D736F356B496E62617857")
    private val META_KEY = hexToBytes("2331346C6A6B5F215C5D2630553C2728")
    private const val PREFIX_LEN = 17 // "neteasecloudmusic\0"
    private const val PROBE_SIZE = 16

    override fun decrypt(input: ByteArray): DecryptResult {
        val output = ByteArrayOutputStream()
        var meta: String? = null
        val format = decrypt(ByteArrayInputStream(input), output) { meta = it }
        return DecryptResult(audio = output.toByteArray(), format = format, meta = meta)
    }

    override fun decrypt(input: InputStream, output: OutputStream, bufferSize: Int): String =
        decrypt(input, output, bufferSize, null)

    private fun decrypt(
        input: InputStream,
        output: OutputStream,
        bufferSize: Int = DEFAULT_BUFFER_SIZE,
        onMeta: ((String?) -> Unit)?,
    ): String {
        require(bufferSize > 0) { "NCM buffer size must be positive" }
        val header = parseHeader(input)
        onMeta?.invoke(header.meta)

        val k = rc4Keystream(header.sBox)
        val probe = ByteArrayOutputStream(PROBE_SIZE)
        var format: String? = null
        var offset = 0
        val buffer = ByteArray(bufferSize)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (n == 0) continue
            decryptInPlace(buffer, n, offset, k)
            offset += n
            if (format == null) {
                val take = minOf(n, PROBE_SIZE - probe.size())
                if (take > 0) probe.write(buffer, 0, take)
                if (probe.size() >= PROBE_SIZE) format = FormatSniffer.sniff(probe.toByteArray())
            }
            output.write(buffer, 0, n)
        }
        if (format == null) {
            require(offset > 0) { "NCM: header parsing past end" }
            format = FormatSniffer.sniff(probe.toByteArray())
        }
        return format
    }

    private class ParsedHeader(val sBox: IntArray, val meta: String?)

    private fun parseHeader(input: InputStream): ParsedHeader {
        val magic = readExact(input, MAGIC.size)
        require(magic.size == MAGIC.size) { "NCM: too short" }
        for (i in MAGIC.indices) require(magic[i] == MAGIC[i]) { "NCM: bad magic" }
        skipExact(input, 2)

        val keyLen = readU32LE(input)
        require(keyLen in 1..1_000_000) { "NCM: invalid keyLen $keyLen" }
        val keyEnc = readExact(input, keyLen)
        require(keyEnc.size == keyLen) { "NCM: invalid keyLen $keyLen" }
        for (i in keyEnc.indices) keyEnc[i] = (keyEnc[i].toInt() xor 0x64).toByte()
        val keyPlain = pkcs7Unpad(aesEcbDecrypt(keyEnc, CORE_KEY))
        require(keyPlain.size >= PREFIX_LEN) { "NCM: key block too short" }
        val rc4Key = keyPlain.copyOfRange(PREFIX_LEN, keyPlain.size)

        val metaLen = readU32LE(input)
        var meta: String? = null
        if (metaLen > 0) {
            require(metaLen in 1..4_000_000) { "NCM: meta length overruns" }
            val metaEnc = readExact(input, metaLen)
            require(metaEnc.size == metaLen) { "NCM: meta length overruns" }
            for (i in metaEnc.indices) metaEnc[i] = (metaEnc[i].toInt() xor 0x63).toByte()
            val b64 = String(metaEnc, 22, metaEnc.size - 22, Charsets.US_ASCII)
            try {
                val metaAes = aesEcbDecrypt(java.util.Base64.getDecoder().decode(b64), META_KEY)
                meta = String(pkcs7Unpad(metaAes), Charsets.UTF_8).removePrefix("music:")
            } catch (_: Exception) {
                meta = null
            }
        }

        skipExact(input, 5)
        val imageSpace = readU32LE(input)
        readU32LE(input) // imageSize; image + padding are skipped via imageSpace
        require(imageSpace >= 0) { "NCM: image overruns" }
        skipExact(input, imageSpace)

        return ParsedHeader(buildRc4Sbox(rc4Key), meta)
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
    private fun rc4Keystream(s: IntArray): IntArray {
        val k = IntArray(256)
        for (i in 0 until 256) k[i] = s[(s[i] + s[(i + s[i]) and 0xff]) and 0xff]
        return k
    }

    private fun decryptInPlace(buf: ByteArray, length: Int, startOffset: Int, k: IntArray) {
        for (i in 0 until length) {
            buf[i] = ((buf[i].toInt() and 0xff) xor k[(startOffset + i + 1) % 256]).toByte()
        }
    }

    private fun readU32LE(input: InputStream): Int {
        val buf = readExact(input, 4)
        require(buf.size == 4) { "NCM: too short" }
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun readExact(input: InputStream, size: Int): ByteArray {
        val out = ByteArray(size)
        var count = 0
        while (count < size) {
            val n = input.read(out, count, size - count)
            if (n < 0) return out.copyOf(count)
            if (n == 0) continue
            count += n
        }
        return out
    }

    private fun skipExact(input: InputStream, bytes: Int) {
        var remaining = bytes
        val discard = ByteArray(DEFAULT_BUFFER_SIZE)
        while (remaining > 0) {
            val skipped = input.skip(remaining.toLong())
            if (skipped > 0) {
                remaining -= skipped.toInt()
                continue
            }
            val n = input.read(discard, 0, minOf(remaining, discard.size))
            require(n >= 0) { "NCM: image overruns" }
            if (n == 0) continue
            remaining -= n
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) out[i] = ((hex[i * 2].digitToInt(16) shl 4) or hex[i * 2 + 1].digitToInt(16)).toByte()
        return out
    }
}
