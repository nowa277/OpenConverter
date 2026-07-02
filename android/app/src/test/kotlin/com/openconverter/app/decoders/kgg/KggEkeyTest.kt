package com.openconverter.app.decoders.kgg

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KggEkeyTest {
    @Test
    fun unwraps_synthetic_v1() {
        val key = ByteArray(64) { ((it * 19 + 11) and 0xff).toByte() }

        assertArrayEquals(key, KggEkey.unwrap(v1Ekey(key)))
    }

    @Test
    fun unwraps_synthetic_v2() {
        val key = ByteArray(512) { ((it * 23 + 17) and 0xff).toByte() }

        assertArrayEquals(key, KggEkey.unwrap(v2Ekey(key)))
    }

    @Test
    fun rejects_invalid_base64_and_short_or_unaligned_ciphertext() {
        listOf(
            "",
            "not valid base64 @@@",
            Base64.getEncoder().encodeToString(ByteArray(8)),
            Base64.getEncoder().encodeToString(ByteArray(11)),
        ).forEach { encoded ->
            assertThrows(IllegalArgumentException::class.java) { KggEkey.unwrap(encoded) }
        }
    }

    @Test
    fun rejects_invalid_tencent_zero_padding() {
        val key = ByteArray(32) { (it + 1).toByte() }
        val decoded = Base64.getDecoder().decode(v1Ekey(key))
        decoded[decoded.lastIndex] = (decoded.last().toInt() xor 0x40).toByte()

        assertThrows(IllegalArgumentException::class.java) {
            KggEkey.unwrap(Base64.getEncoder().encodeToString(decoded))
        }
    }

    private fun v1Ekey(key: ByteArray): String {
        require(key.size >= 8)
        val prefix = key.copyOfRange(0, 8)
        val teaKey = ByteArray(16)
        SIMPLE_KEY.forEachIndexed { index, value ->
            teaKey[index * 2] = value
            teaKey[index * 2 + 1] = prefix[index]
        }
        val raw = prefix + encryptTencentTea(key.copyOfRange(8, key.size), teaKey)
        return Base64.getEncoder().encodeToString(raw)
    }

    private fun v2Ekey(key: ByteArray): String {
        val v1Raw = Base64.getDecoder().decode(v1Ekey(key))
        val innerBase64 = Base64.getEncoder().encode(v1Raw)
        val layer2 = encryptTencentTea(innerBase64, V2_KEY_2)
        val layer1 = encryptTencentTea(layer2, V2_KEY_1)
        return Base64.getEncoder().encodeToString(V2_PREFIX.toByteArray() + layer1)
    }

    private fun encryptTencentTea(body: ByteArray, key: ByteArray): ByteArray {
        val padding = (8 - (body.size + 10) % 8) % 8
        val plain = ByteArray(1 + padding + 2 + body.size + 7)
        plain[0] = (0xa8 or padding).toByte()
        repeat(padding) { plain[1 + it] = (0x31 + it).toByte() }
        plain[1 + padding] = 0x53
        plain[2 + padding] = 0x71
        body.copyInto(plain, 3 + padding)

        val output = ByteArray(plain.size)
        var previousCipher = ByteArray(8)
        var previousMixedPlain = ByteArray(8)
        for (offset in plain.indices step 8) {
            val mixed = ByteArray(8) { index ->
                (plain[offset + index].toInt() xor previousCipher[index].toInt()).toByte()
            }
            val encrypted = teaEncryptBlock(mixed, key)
            val cipher = ByteArray(8) { index ->
                (encrypted[index].toInt() xor previousMixedPlain[index].toInt()).toByte()
            }
            cipher.copyInto(output, offset)
            previousMixedPlain = mixed
            previousCipher = cipher
        }
        return output
    }

    private fun teaEncryptBlock(block: ByteArray, key: ByteArray): ByteArray {
        val keyWords = ByteBuffer.wrap(key).order(ByteOrder.BIG_ENDIAN)
        val k = IntArray(4) { keyWords.int.toLong().and(0xffffffffL).toInt() }
        val input = ByteBuffer.wrap(block).order(ByteOrder.BIG_ENDIAN)
        var v0 = input.int.toLong() and 0xffffffffL
        var v1 = input.int.toLong() and 0xffffffffL
        var sum = 0L
        repeat(16) {
            sum = (sum + DELTA) and MASK
            v0 = (v0 + ((((v1 shl 4) + unsigned(k[0])) xor (v1 + sum) xor ((v1 ushr 5) + unsigned(k[1]))))) and MASK
            v1 = (v1 + ((((v0 shl 4) + unsigned(k[2])) xor (v0 + sum) xor ((v0 ushr 5) + unsigned(k[3]))))) and MASK
        }
        return ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putInt(v0.toInt()).putInt(v1.toInt()).array()
    }

    private fun unsigned(value: Int): Long = value.toLong() and MASK

    companion object {
        private const val MASK = 0xffffffffL
        private const val DELTA = 0x9e3779b9L
        private const val V2_PREFIX = "QQMusic EncV2,Key:"
        private val SIMPLE_KEY = byteArrayOf(0x69, 0x56, 0x46, 0x38, 0x2b, 0x20, 0x15, 0x0b)
        private val V2_KEY_1 = "386ZJY!@#*$%^&)(".toByteArray()
        private val V2_KEY_2 = "**#!(#$%&^a1cZ,T".toByteArray()
    }
}
