package com.openconverter.app.decoders.kgg

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KggDatabaseCipherTest {
    private val testMaster = ByteArray(16) { it.toByte() }

    @Test
    fun derives_reference_page_keys_and_ivs() {
        assertEquals("14834d065003a4246204b16d07b6c76b", KggDatabaseCipher.pageKey(testMaster, 1).hex())
        assertEquals("20d7420f9c37a35dca6fe92a1c6999a9", KggDatabaseCipher.pageIv(1).hex())
        assertEquals("6a788ce5bfd3e35dd4067af44463bf02", KggDatabaseCipher.pageKey(testMaster, 2).hex())
        assertEquals("b2547aac5270eef583e1d267a725d988", KggDatabaseCipher.pageIv(2).hex())
    }

    @Test
    fun decrypts_first_and_later_pages() {
        val plain = syntheticPlainDatabase()
        val encrypted = encryptDatabase(plain, testMaster)
        val output = ByteArrayOutputStream()

        KggDatabaseCipher.decrypt(ByteArrayInputStream(encrypted), output, testMaster)

        assertArrayEquals(plain, output.toByteArray())
    }

    @Test
    fun leaves_plain_sqlite_unchanged() {
        val plain = syntheticPlainDatabase()
        val output = ByteArrayOutputStream()

        KggDatabaseCipher.decrypt(ByteArrayInputStream(plain), output, testMaster)

        assertArrayEquals(plain, output.toByteArray())
    }

    @Test
    fun rejects_empty_truncated_and_invalid_first_pages() {
        listOf(
            ByteArray(0),
            ByteArray(KggDatabaseCipher.PAGE_SIZE - 1),
            ByteArray(KggDatabaseCipher.PAGE_SIZE),
        ).forEach { input ->
            assertThrows(IllegalArgumentException::class.java) {
                KggDatabaseCipher.decrypt(ByteArrayInputStream(input), ByteArrayOutputStream(), testMaster)
            }
        }
    }

    private fun syntheticPlainDatabase(): ByteArray {
        val bytes = ByteArray(KggDatabaseCipher.PAGE_SIZE * 2) { (it * 31).toByte() }
        "SQLite format 3\u0000".toByteArray().copyInto(bytes)
        bytes[16] = 0x04
        bytes[17] = 0x00
        bytes[18] = 0x01
        bytes[19] = 0x01
        bytes[20] = 0x00
        bytes[21] = 0x40
        bytes[22] = 0x20
        bytes[23] = 0x20
        return bytes
    }

    private fun encryptDatabase(plain: ByteArray, master: ByteArray): ByteArray {
        val encrypted = ByteArray(plain.size)
        val firstCipher = encryptPage(plain.copyOfRange(16, KggDatabaseCipher.PAGE_SIZE), 1, master)
        plain.copyOfRange(16, 24).copyInto(encrypted, 16)
        firstCipher.copyOfRange(0, 8).copyInto(encrypted, 8)
        firstCipher.copyOfRange(8, firstCipher.size).copyInto(encrypted, 24)

        val secondCipher = encryptPage(
            plain.copyOfRange(KggDatabaseCipher.PAGE_SIZE, plain.size),
            2,
            master,
        )
        secondCipher.copyInto(encrypted, KggDatabaseCipher.PAGE_SIZE)
        return encrypted
    }

    private fun encryptPage(page: ByteArray, number: Int, master: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(referencePageKey(master, number), "AES"),
            IvParameterSpec(referencePageIv(number)),
        )
        return cipher.doFinal(page)
    }

    private fun referencePageKey(master: ByteArray, number: Int): ByteArray {
        val material = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .put(master)
            .putInt(number)
            .putInt(0x546c4173)
            .array()
        return MessageDigest.getInstance("MD5").digest(material)
    }

    private fun referencePageIv(number: Int): ByteArray {
        var seed = number.toLong() + 1
        val material = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        repeat(4) {
            val value = (seed * 0x9ef4L - (seed / 0xce26L) * 0x7fffff07L) and 0xffffffffL
            seed = if (value and 0x80000000L == 0L) value else (value + 0x7fffff07L) and 0xffffffffL
            material.putInt(seed.toInt())
        }
        return MessageDigest.getInstance("MD5").digest(material.array())
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
