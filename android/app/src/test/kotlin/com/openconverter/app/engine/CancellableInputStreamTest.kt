package com.openconverter.app.engine

import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class CancellableInputStreamTest {
    @Test fun cancellation_stops_decoder_cache_writes_after_input_has_been_staged() {
        val job = Job()
        val source = java.io.ByteArrayOutputStream()
        CancellableOutputStream(source, job).use { output ->
            output.write(byteArrayOf(1, 2))
            job.cancel()
            assertThrows(CancellationException::class.java) { output.write(byteArrayOf(3, 4)) }
        }
        org.junit.Assert.assertArrayEquals(byteArrayOf(1, 2), source.toByteArray())
    }

    @Test fun cancellation_stops_the_next_read_and_closes_the_input() {
        val job = Job()
        var closed = false
        val source = object : ByteArrayInputStream(ByteArray(100)) {
            override fun close() { closed = true; super.close() }
        }
        CancellableInputStream(source, job).use { input ->
            assertEquals(16, input.read(ByteArray(16)))
            job.cancel()
            assertThrows(CancellationException::class.java) { input.read(ByteArray(16)) }
        }
        assertEquals(true, closed)
    }
}
