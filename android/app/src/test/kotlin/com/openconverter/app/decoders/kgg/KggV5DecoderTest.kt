package com.openconverter.app.decoders.kgg

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class KggV5DecoderTest {
    @Test
    fun streams_map_and_rc4_payloads_at_different_buffer_sizes() {
        listOf(
            ByteArray(180) { ((it * 17 + 3) and 0xff).toByte() },
            ByteArray(512) { ((it * 23 + 9) and 0xff).toByte() },
        ).forEach { key ->
            val audio = byteArrayOf(0x66, 0x4c, 0x61, 0x43) + ByteArray(15_000) { (it * 29).toByte() }
            val encrypted = kggFile("key-id", key, audio)
            val decoder = KggV5Decoder(provider(mapOf("key-id" to "synthetic"))) { key }

            listOf(7, 4096).forEach { bufferSize ->
                val output = ByteArrayOutputStream()
                val format = decoder.decrypt(ByteArrayInputStream(encrypted), output, bufferSize)

                assertEquals("flac", format)
                assertArrayEquals(audio, output.toByteArray())
            }
        }
    }

    @Test
    fun byte_array_contract_delegates_to_streaming_decode() {
        val key = ByteArray(180) { (it + 1).toByte() }
        val audio = "ID3synthetic audio".toByteArray()
        val decoder = KggV5Decoder(provider(mapOf("id" to "synthetic"))) { key }

        val result = decoder.decrypt(kggFile("id", key, audio))

        assertEquals("mp3", result.format)
        assertArrayEquals(audio, result.audio)
    }

    @Test
    fun rejects_legacy_version_and_unknown_decrypted_format() {
        val key = ByteArray(180) { (it + 1).toByte() }
        val legacy = kggFile("id", key, "ID3audio".toByteArray(), version = 3)
        val unknown = kggFile("id", key, byteArrayOf(1, 2, 3, 4, 5, 6))
        val decoder = KggV5Decoder(provider(mapOf("id" to "synthetic"))) { key }

        assertThrows(IllegalArgumentException::class.java) {
            decoder.decrypt(ByteArrayInputStream(legacy), ByteArrayOutputStream())
        }
        assertThrows(IllegalArgumentException::class.java) {
            decoder.decrypt(ByteArrayInputStream(unknown), ByteArrayOutputStream())
        }
    }

    @Test
    fun reports_no_imported_keys_and_missing_specific_id_separately() {
        val key = ByteArray(180) { (it + 1).toByte() }
        val input = kggFile("missing-id", key, "ID3audio".toByteArray())

        val noKeys = assertThrows(IllegalArgumentException::class.java) {
            KggV5Decoder(provider(emptyMap())).decrypt(ByteArrayInputStream(input), ByteArrayOutputStream())
        }
        val missing = assertThrows(IllegalArgumentException::class.java) {
            KggV5Decoder(provider(mapOf("other" to "value")))
                .decrypt(ByteArrayInputStream(input), ByteArrayOutputStream())
        }

        assertEquals("No KGG keys imported; import KGMusicV3.db or kgg.key in Settings", noKeys.message)
        assertEquals(
            "Missing KGG key for missing-id; import a database or key file containing this ID",
            missing.message,
        )
    }

    @Test
    fun rejects_invalid_ekey_without_writing_output() {
        val key = ByteArray(180) { (it + 1).toByte() }
        val output = ByteArrayOutputStream()
        val decoder = KggV5Decoder(provider(mapOf("id" to "not-base64")))

        assertThrows(IllegalArgumentException::class.java) {
            decoder.decrypt(ByteArrayInputStream(kggFile("id", key, "ID3audio".toByteArray())), output)
        }
        assertEquals(0, output.size())
    }

    @Test
    fun does_not_close_caller_owned_streams() {
        val key = ByteArray(180) { (it + 1).toByte() }
        val input = TrackingInput(kggFile("id", key, "ID3audio".toByteArray()))
        val output = TrackingOutput()

        KggV5Decoder(provider(mapOf("id" to "synthetic"))) { key }.decrypt(input, output)

        assertFalse(input.closed)
        assertFalse(output.closed)
    }

    private fun provider(keys: Map<String, String>): KggKeyProvider = object : KggKeyProvider {
        override fun find(encryptionKeyId: String): String? = keys[encryptionKeyId]
        override fun count(): Int = keys.size
    }

    private fun kggFile(id: String, key: ByteArray, audio: ByteArray, version: Int = 5): ByteArray {
        val header = ByteArray(KggHeaderParser.PREFIX_SIZE)
        MAGIC.copyInto(header)
        header.putInt(0x10, header.size)
        header.putInt(0x14, version)
        val idBytes = id.toByteArray()
        header.putInt(0x44, idBytes.size)
        idBytes.copyInto(header, 0x48)
        val encrypted = audio.copyOf()
        KggQmc2.cipher(key).apply(encrypted, encrypted.size, 0)
        return header + encrypted
    }

    private fun ByteArray.putInt(offset: Int, value: Int) {
        ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
    }

    private class TrackingInput(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun close() {
            closed = true
            super.close()
        }
    }

    private class TrackingOutput : ByteArrayOutputStream() {
        var closed = false
        override fun close() {
            closed = true
            super.close()
        }
    }

    companion object {
        private val MAGIC = byteArrayOf(
            0x7c, 0xd5.toByte(), 0x32, 0xeb.toByte(), 0x86.toByte(), 0x02, 0x7f, 0x4b,
            0xa8.toByte(), 0xaf.toByte(), 0xa6.toByte(), 0x8e.toByte(), 0x0f, 0xff.toByte(),
            0x99.toByte(), 0x14,
        )
    }
}
