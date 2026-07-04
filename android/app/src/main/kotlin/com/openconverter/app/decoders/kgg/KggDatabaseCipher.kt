package com.openconverter.app.decoders.kgg

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object KggDatabaseCipher {
    const val PAGE_SIZE = 1024

    private val sqliteHeader = "SQLite format 3\u0000".toByteArray()
    private val masterKey = byteArrayOf(
        0x1d, 0x61, 0x31, 0x45, 0xb2.toByte(), 0x47, 0xbf.toByte(), 0x7f,
        0x3d, 0x18, 0x96.toByte(), 0x72, 0x14, 0x4f, 0xe4.toByte(), 0xbf.toByte(),
    )

    fun decrypt(input: InputStream, output: OutputStream) = decrypt(input, output, masterKey)

    internal fun decrypt(input: InputStream, output: OutputStream, masterKey: ByteArray) {
        require(masterKey.size == 16) { "KGG database master key must be 16 bytes" }
        val firstPage = readPage(input)
        require(firstPage != null) { "KGG database is empty" }

        val plaintext = firstPage.copyOfRange(0, sqliteHeader.size).contentEquals(sqliteHeader)
        if (!plaintext) decryptFirstPage(firstPage, masterKey)
        output.write(firstPage)

        var pageNumber = 2
        while (true) {
            val page = readPage(input) ?: break
            if (!plaintext) decryptPage(page, pageNumber, masterKey)
            output.write(page)
            pageNumber++
        }
    }

    internal fun pageKey(masterKey: ByteArray, pageNumber: Int): ByteArray {
        require(masterKey.size == 16) { "KGG database master key must be 16 bytes" }
        require(pageNumber > 0) { "KGG database page number must be positive" }
        val material = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .put(masterKey)
            .putInt(pageNumber)
            .putInt(0x546c4173)
            .array()
        return MessageDigest.getInstance("MD5").digest(material)
    }

    internal fun pageIv(pageNumber: Int): ByteArray {
        require(pageNumber > 0) { "KGG database page number must be positive" }
        var seed = pageNumber.toLong() + 1
        val material = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        repeat(4) {
            val value = (seed * 0x9ef4L - (seed / 0xce26L) * 0x7fffff07L) and 0xffffffffL
            seed = if (value and 0x80000000L == 0L) {
                value
            } else {
                (value + 0x7fffff07L) and 0xffffffffL
            }
            material.putInt(seed.toInt())
        }
        return MessageDigest.getInstance("MD5").digest(material.array())
    }

    private fun readPage(input: InputStream): ByteArray? {
        val page = ByteArray(PAGE_SIZE)
        var count = 0
        while (count < PAGE_SIZE) {
            val read = input.read(page, count, PAGE_SIZE - count)
            if (read < 0) break
            if (read == 0) continue
            count += read
        }
        if (count == 0) return null
        require(count == PAGE_SIZE) { "KGG database size is not aligned to $PAGE_SIZE-byte pages" }
        return page
    }

    private fun decryptFirstPage(page: ByteArray, masterKey: ByteArray) {
        require(validEncryptedHeader(page)) { "Invalid encrypted KGG database header" }
        val expectedHeader = page.copyOfRange(16, 24)
        page.copyInto(page, destinationOffset = 16, startIndex = 8, endIndex = 16)

        val decrypted = decryptBlocks(page.copyOfRange(16, PAGE_SIZE), 1, masterKey)
        decrypted.copyInto(page, destinationOffset = 16)
        require(page.copyOfRange(16, 24).contentEquals(expectedHeader)) {
            "KGG database page 1 integrity check failed"
        }
        sqliteHeader.copyInto(page)
    }

    private fun validEncryptedHeader(page: ByteArray): Boolean {
        val order = ByteBuffer.wrap(page).order(ByteOrder.LITTLE_ENDIAN)
        val offset10 = order.getInt(16)
        val offset14 = order.getInt(20)
        val pageSize = ((offset10 and 0xff) shl 8) or ((offset10 and 0xff00) shl 16)
        return offset14 == 0x20204000 &&
            pageSize - 0x200 <= 0xfe00 &&
            ((pageSize - 1) and pageSize) == 0
    }

    private fun decryptPage(page: ByteArray, pageNumber: Int, masterKey: ByteArray) {
        decryptBlocks(page, pageNumber, masterKey).copyInto(page)
    }

    private fun decryptBlocks(ciphertext: ByteArray, pageNumber: Int, masterKey: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(pageKey(masterKey, pageNumber), "AES"),
            IvParameterSpec(pageIv(pageNumber)),
        )
        return cipher.doFinal(ciphertext)
    }
}
