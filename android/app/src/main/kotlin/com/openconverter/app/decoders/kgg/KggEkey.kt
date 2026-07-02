package com.openconverter.app.decoders.kgg

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

object KggEkey {
    private val v2Prefix = "QQMusic EncV2,Key:".toByteArray(StandardCharsets.US_ASCII)
    private val simpleKey = byteArrayOf(0x69, 0x56, 0x46, 0x38, 0x2b, 0x20, 0x15, 0x0b)
    private val v2Key1 = byteArrayOf(
        0x33, 0x38, 0x36, 0x5a, 0x4a, 0x59, 0x21, 0x40,
        0x23, 0x2a, 0x24, 0x25, 0x5e, 0x26, 0x29, 0x28,
    )
    private val v2Key2 = byteArrayOf(
        0x2a, 0x2a, 0x23, 0x21, 0x28, 0x23, 0x24, 0x25,
        0x26, 0x5e, 0x61, 0x31, 0x63, 0x5a, 0x2c, 0x54,
    )

    fun unwrap(encoded: String): ByteArray {
        var raw = decodeBase64(encoded)
        if (raw.startsWith(v2Prefix)) {
            raw = raw.copyOfRange(v2Prefix.size, raw.size)
            raw = decryptTencentTea(raw, v2Key1)
            raw = decryptTencentTea(raw, v2Key2)
            raw = decodeBase64(raw.toAsciiString())
        }
        return deriveV1(raw)
    }

    private fun deriveV1(raw: ByteArray): ByteArray {
        require(raw.size >= 16) { "KGG ekey is too short" }
        val teaKey = ByteArray(16)
        repeat(8) { index ->
            teaKey[index * 2] = simpleKey[index]
            teaKey[index * 2 + 1] = raw[index]
        }
        val suffix = decryptTencentTea(raw.copyOfRange(8, raw.size), teaKey)
        return raw.copyOfRange(0, 8) + suffix
    }

    private fun decryptTencentTea(input: ByteArray, key: ByteArray): ByteArray {
        require(input.size >= 16) { "Tencent TEA ciphertext is too short" }
        require(input.size % 8 == 0) { "Tencent TEA ciphertext is not block aligned" }
        require(key.size == 16) { "Tencent TEA key must be 16 bytes" }

        var decrypted = teaDecryptBlock(input.copyOfRange(0, 8), key)
        val padding = decrypted[0].toInt() and 7
        val outputLength = input.size - 1 - padding - 2 - 7
        require(outputLength >= 0) { "Tencent TEA padding is invalid" }

        var previousCipher = ByteArray(8)
        var currentCipher = input.copyOfRange(0, 8)
        var inputOffset = 8
        var decryptedOffset = 1 + padding

        fun decryptNextBlock() {
            require(inputOffset + 8 <= input.size) { "Tencent TEA ciphertext is truncated" }
            previousCipher = currentCipher
            currentCipher = input.copyOfRange(inputOffset, inputOffset + 8)
            val mixed = ByteArray(8) { index ->
                (decrypted[index].toInt() xor currentCipher[index].toInt()).toByte()
            }
            decrypted = teaDecryptBlock(mixed, key)
            inputOffset += 8
            decryptedOffset = 0
        }

        repeat(2) {
            if (decryptedOffset == 8) decryptNextBlock()
            decryptedOffset++
        }

        val output = ByteArray(outputLength)
        output.indices.forEach { index ->
            if (decryptedOffset == 8) decryptNextBlock()
            output[index] =
                (decrypted[decryptedOffset].toInt() xor previousCipher[decryptedOffset].toInt()).toByte()
            decryptedOffset++
        }

        repeat(7) {
            if (decryptedOffset == 8) decryptNextBlock()
            val value = decrypted[decryptedOffset].toInt() xor previousCipher[decryptedOffset].toInt()
            require(value == 0) { "Tencent TEA zero padding is invalid" }
            decryptedOffset++
        }
        return output
    }

    private fun teaDecryptBlock(block: ByteArray, key: ByteArray): ByteArray {
        val keyBuffer = ByteBuffer.wrap(key).order(ByteOrder.BIG_ENDIAN)
        val keys = LongArray(4) { keyBuffer.int.toLong() and MASK }
        val input = ByteBuffer.wrap(block).order(ByteOrder.BIG_ENDIAN)
        var v0 = input.int.toLong() and MASK
        var v1 = input.int.toLong() and MASK
        var sum = (DELTA * 32) and MASK
        repeat(32) {
            v1 = (v1 - (((v0 shl 4) + keys[2]) xor (v0 + sum) xor ((v0 ushr 5) + keys[3]))) and MASK
            v0 = (v0 - (((v1 shl 4) + keys[0]) xor (v1 + sum) xor ((v1 ushr 5) + keys[1]))) and MASK
            sum = (sum - DELTA) and MASK
        }
        return ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putInt(v0.toInt())
            .putInt(v1.toInt())
            .array()
    }

    private fun decodeBase64(encoded: String): ByteArray {
        require(encoded.isNotEmpty() && encoded.length % 4 == 0) { "KGG ekey Base64 length is invalid" }
        val padding = when {
            encoded.endsWith("==") -> 2
            encoded.endsWith('=') -> 1
            else -> 0
        }
        val output = ByteArray(encoded.length / 4 * 3 - padding)
        var outputOffset = 0
        for (offset in encoded.indices step 4) {
            val last = offset + 4 == encoded.length
            val a = base64Value(encoded[offset])
            val b = base64Value(encoded[offset + 1])
            val c = if (encoded[offset + 2] == '=') -1 else base64Value(encoded[offset + 2])
            val d = if (encoded[offset + 3] == '=') -1 else base64Value(encoded[offset + 3])
            require(a >= 0 && b >= 0) { "KGG ekey Base64 is invalid" }
            require(c >= 0 || last && c == -1 && d == -1) { "KGG ekey Base64 padding is invalid" }
            require(d >= 0 || last && d == -1) { "KGG ekey Base64 padding is invalid" }

            val bits = (a shl 18) or (b shl 12) or ((if (c < 0) 0 else c) shl 6) or if (d < 0) 0 else d
            if (outputOffset < output.size) output[outputOffset++] = (bits ushr 16).toByte()
            if (outputOffset < output.size) output[outputOffset++] = (bits ushr 8).toByte()
            if (outputOffset < output.size) output[outputOffset++] = bits.toByte()
        }
        return output
    }

    private fun base64Value(value: Char): Int = when (value) {
        in 'A'..'Z' -> value - 'A'
        in 'a'..'z' -> value - 'a' + 26
        in '0'..'9' -> value - '0' + 52
        '+' -> 62
        '/' -> 63
        else -> throw IllegalArgumentException("KGG ekey Base64 contains an invalid character")
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun ByteArray.toAsciiString(): String {
        require(all { (it.toInt() and 0xff) <= 0x7f }) { "KGG V2 ekey inner Base64 is invalid" }
        return toString(StandardCharsets.US_ASCII)
    }

    private const val MASK = 0xffffffffL
    private const val DELTA = 0x9e3779b9L
}
