package com.openconverter.app.log

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OCLogTest {
    private class CapturingSink : LogSink {
        data class Record(val level: LogLevel, val tag: String, val msg: String, val err: String?)
        val records = mutableListOf<Record>()
        override fun log(level: LogLevel, tag: String, msg: String, throwable: Throwable?) {
            records.add(Record(level, tag, msg, throwable?.message))
        }
    }

    @Test
    fun `OCLog uses single OpenConverter tag`() {
        val sink = CapturingSink()
        OCLog.setSink(sink)
        OCLog.i("hello world")
        assertEquals(1, sink.records.size)
        assertEquals("OpenConverter", sink.records[0].tag)
        assertEquals(LogLevel.INFO, sink.records[0].level)
    }

    @Test
    fun `OCLog error includes throwable message`() {
        val sink = CapturingSink()
        OCLog.setSink(sink)
        OCLog.e("crash", IllegalStateException("disk full"))
        val r = sink.records.single()
        assertEquals(LogLevel.ERROR, r.level)
        assertEquals("crash", r.msg)
        assertEquals("disk full", r.err)
    }

    @Test
    fun `OCLog formats contextual args as key=value`() {
        val sink = CapturingSink()
        OCLog.setSink(sink)
        OCLog.i("convert", "uri" to "content://x", "format" to "mp3")
        val msg = sink.records.single().msg
        assertTrue(msg.contains("uri=content://x"), "missing uri in: $msg")
        assertTrue(msg.contains("format=mp3"), "missing format in: $msg")
    }

    @Test
    fun `snapshot returns records in append order, ring bounded to capacity`() {
        OCLog.clearRing()
        val sink = CapturingSink()
        OCLog.setSink(sink)
        repeat(250) { OCLog.i("event-$it") }
        val snap = OCLog.snapshot()
        assertEquals(200, snap.size, "ring should cap at 200")
        assertTrue(snap.first().contains("event-50"),
            "oldest retained should be event-50 (we appended 250, kept last 200), got: ${snap.first()}")
        assertTrue(snap.last().contains("event-249"),
            "newest should be event-249, got: ${snap.last()}")
    }

    @Test
    fun `snapshot is independent of later log calls`() {
        OCLog.clearRing()
        val sink = CapturingSink()
        OCLog.setSink(sink)
        OCLog.i("a")
        val snap1 = OCLog.snapshot()
        OCLog.i("b")
        val snap2 = OCLog.snapshot()
        assertEquals(1, snap1.size)
        assertEquals(2, snap2.size)
        assertTrue(snap1.first().contains("a"))
    }
}
