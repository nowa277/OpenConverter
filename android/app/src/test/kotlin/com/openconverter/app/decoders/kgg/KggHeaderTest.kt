package com.openconverter.app.decoders.kgg

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KggHeaderTest {
    @Test
    fun parses_version_five_header() {
        val parsed = KggHeaderParser.parse(header(version = 5, id = "0123456789abcdef"))

        assertEquals(1024, parsed.headerLength)
        assertEquals(5, parsed.cryptoVersion)
        assertEquals("0123456789abcdef", parsed.encryptionKeyId)
    }

    @Test
    fun parses_version_three_for_legacy_routing() {
        assertEquals(3, KggHeaderParser.parse(header(version = 3, id = "legacy")).cryptoVersion)
    }

    @Test
    fun rejects_wrong_magic_unknown_version_and_truncated_fixed_header() {
        val wrongMagic = header().also { it[0] = 0 }

        listOf(
            wrongMagic,
            header(version = 4),
            ByteArray(67),
        ).forEach { bytes ->
            assertThrows(IllegalArgumentException::class.java) { KggHeaderParser.parse(bytes) }
        }
    }

    @Test
    fun rejects_header_and_id_lengths_outside_available_prefix() {
        val shortHeaderLength = header().also { it.putInt(0x10, 0x47) }
        val beyondPrefix = header().also { it.putInt(0x10, 1025) }
        val zeroId = header().also { it.putInt(0x44, 0) }
        val oversizedId = header().also { it.putInt(0x44, 257) }
        val idBeyondHeader = header(id = "valid").also { it.putInt(0x10, 0x49) }

        listOf(shortHeaderLength, beyondPrefix, zeroId, oversizedId, idBeyondHeader).forEach { bytes ->
            assertThrows(IllegalArgumentException::class.java) { KggHeaderParser.parse(bytes) }
        }
    }

    @Test
    fun rejects_malformed_utf8_key_id() {
        val bytes = header(id = "xx")
        bytes[0x48] = 0xc3.toByte()
        bytes[0x49] = 0x28

        assertThrows(IllegalArgumentException::class.java) { KggHeaderParser.parse(bytes) }
    }

    private fun header(version: Int = 5, id: String = "key-id"): ByteArray {
        val idBytes = id.toByteArray()
        return ByteArray(KggHeaderParser.PREFIX_SIZE).also { bytes ->
            MAGIC.copyInto(bytes)
            bytes.putInt(0x10, KggHeaderParser.PREFIX_SIZE)
            bytes.putInt(0x14, version)
            bytes.putInt(0x44, idBytes.size)
            idBytes.copyInto(bytes, 0x48)
        }
    }

    private fun ByteArray.putInt(offset: Int, value: Int) {
        ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
    }

    companion object {
        private val MAGIC = byteArrayOf(
            0x7c, 0xd5.toByte(), 0x32, 0xeb.toByte(), 0x86.toByte(), 0x02, 0x7f, 0x4b,
            0xa8.toByte(), 0xaf.toByte(), 0xa6.toByte(), 0x8e.toByte(), 0x0f, 0xff.toByte(),
            0x99.toByte(), 0x14,
        )
    }
}
