package com.openconverter.app.decoders.kgg

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class KggHeader(
    val headerLength: Int,
    val cryptoVersion: Int,
    val encryptionKeyId: String,
)

object KggHeaderParser {
    const val PREFIX_SIZE = 1024

    private const val ID_LENGTH_OFFSET = 0x44
    private const val ID_OFFSET = 0x48
    private const val MAX_ID_LENGTH = 256
    private val magic = byteArrayOf(
        0x7c, 0xd5.toByte(), 0x32, 0xeb.toByte(), 0x86.toByte(), 0x02, 0x7f, 0x4b,
        0xa8.toByte(), 0xaf.toByte(), 0xa6.toByte(), 0x8e.toByte(), 0x0f, 0xff.toByte(),
        0x99.toByte(), 0x14,
    )

    fun parse(prefix: ByteArray): KggHeader {
        require(prefix.size >= ID_OFFSET) { "KGG header is truncated" }
        require(prefix.copyOfRange(0, magic.size).contentEquals(magic)) { "KGG magic is invalid" }

        val headerLength = prefix.leInt(0x10)
        require(headerLength in ID_OFFSET..prefix.size) { "KGG header length is invalid: $headerLength" }

        val version = prefix.leInt(0x14)
        require(version == 3 || version == 5) { "Unsupported KGG crypto version: $version" }

        val idLength = prefix.leInt(ID_LENGTH_OFFSET)
        require(idLength in 1..MAX_ID_LENGTH) { "KGG key ID length is invalid: $idLength" }
        require(ID_OFFSET + idLength <= headerLength) { "KGG key ID exceeds header length" }
        require(ID_OFFSET + idLength <= prefix.size) { "KGG key ID is truncated" }

        val id = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(prefix, ID_OFFSET, idLength))
                .toString()
        } catch (error: Exception) {
            throw IllegalArgumentException("KGG key ID is not valid UTF-8", error)
        }
        require(id.isNotBlank()) { "KGG key ID is empty" }

        return KggHeader(headerLength, version, id)
    }

    private fun ByteArray.leInt(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)
}
