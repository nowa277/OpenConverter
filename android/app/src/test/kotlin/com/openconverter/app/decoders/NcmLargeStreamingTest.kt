package com.openconverter.app.decoders

import java.io.InputStream
import java.io.OutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NcmLargeStreamingTest {
    @Test fun streams_216_mib_audio_with_bounded_reads_and_exact_output() {
        val small = SyntheticNcm.build(meta = null, image = byteArrayOf(), imagePadding = 0)
        val header = small.copyOfRange(0, small.size - SyntheticNcm.audio.size)
        val encrypted = small.copyOfRange(header.size, small.size)
        val keystream = ByteArray(256) { (encrypted[it].toInt() xor SyntheticNcm.audio[it].toInt()).toByte() }
        val audioLength = 216 * 1024 * 1024
        val wavHeader = SyntheticNcm.audio.copyOf(44)
        java.nio.ByteBuffer.wrap(wavHeader).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            putInt(4, audioLength - 8); putInt(40, audioLength - 44)
        }
        fun plainByte(index: Int): Byte = if (index < 44) wavHeader[index] else SyntheticNcm.audio[44 + (index - 44) % 16000]
        var consumed = 0
        var largestRead = 0
        val input = object : InputStream() {
            override fun read(): Int = error("bulk reads required")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                largestRead = maxOf(largestRead, length)
                if (consumed == header.size + audioLength) return -1
                val count = minOf(length, header.size + audioLength - consumed)
                repeat(count) { i ->
                    val position = consumed + i
                    buffer[offset + i] = if (position < header.size) header[position] else {
                        val audioPosition = position - header.size
                        (plainByte(audioPosition).toInt() xor keystream[audioPosition % 256].toInt()).toByte()
                    }
                }
                consumed += count
                return count
            }
        }
        var written = 0
        val output = object : OutputStream() {
            override fun write(value: Int) = error("bulk writes required")
            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                repeat(length) { i -> check(buffer[offset + i] == plainByte(written + i)) }
                written += length
            }
        }
        assertEquals("wav", NcmDecoder.decrypt(input, output, 8192))
        assertEquals(audioLength, written)
        assertTrue(largestRead <= 8192)
    }
}
