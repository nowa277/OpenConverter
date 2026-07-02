package com.openconverter.app.decoders.kgg

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KggQmc2Test {
    @Test
    fun map_matches_known_non_rotating_shift_vector() {
        val key = ByteArray(256) { ((it * 29 + 17) and 0xff).toByte() }
        val data = byteArrayOf(0)

        KggQmc2.cipher(key).apply(data, data.size, 0)

        assertEquals(0x1d, data[0].toInt() and 0xff)
    }

    @Test
    fun rc4_matches_non_512_byte_key_vector() {
        val key = ByteArray(400) { ((it * 7 + 3) and 0xff).toByte() }
        val data = ByteArray(16)

        KggQmc2.cipher(key).apply(data, data.size, 0x1400)

        assertEquals("c5da5561b1a57eea4705518a8161de6a", data.hex())
    }

    @Test
    fun map_and_rc4_are_independent_of_chunk_size() {
        listOf(
            ByteArray(180) { ((it * 11 + 5) and 0xff).toByte() },
            ByteArray(512) { ((it * 13 + 7) and 0xff).toByte() },
        ).forEach { key ->
            val source = ByteArray(18_000) { ((it * 31 + 9) and 0xff).toByte() }
            val oneShot = source.copyOf()
            KggQmc2.cipher(key).apply(oneShot, oneShot.size, 0)

            listOf(1, 7, 4096, 65537).forEach { chunkSize ->
                val chunked = source.copyOf()
                var offset = 0
                while (offset < chunked.size) {
                    val length = minOf(chunkSize, chunked.size - offset)
                    val chunk = chunked.copyOfRange(offset, offset + length)
                    KggQmc2.cipher(key).apply(chunk, length, offset.toLong())
                    chunk.copyInto(chunked, offset)
                    offset += length
                }
                assertArrayEquals("key=${key.size} chunk=$chunkSize", oneShot, chunked)
            }

            KggQmc2.cipher(key).apply(oneShot, oneShot.size, 0)
            assertArrayEquals(source, oneShot)
        }
    }

    @Test
    fun applies_only_valid_buffer_prefix_and_rejects_invalid_arguments() {
        val data = byteArrayOf(1, 2, 3, 4)
        KggQmc2.cipher(byteArrayOf(5)).apply(data, 2, 0)
        assertEquals(3, data[2].toInt())
        assertEquals(4, data[3].toInt())

        assertThrows(IllegalArgumentException::class.java) { KggQmc2.cipher(byteArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) {
            KggQmc2.cipher(byteArrayOf(1)).apply(data, 5, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            KggQmc2.cipher(byteArrayOf(1)).apply(data, 1, -1)
        }
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
