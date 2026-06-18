package com.openconverter.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PortsTest {
    @Test fun progress_sink_is_interface() {
        val events = mutableListOf<String>()
        val sink = object : ProgressSink {
            override fun onFileStart(index: Int, total: Int, name: String) { events.add("start $index/$total $name") }
            override fun onFileProgress(index: Int, percent: Int) { events.add("prog $index $percent") }
            override fun onFileDone(index: Int, outputPath: String) { events.add("done $index $outputPath") }
            override fun onFileError(index: Int, message: String) { events.add("err $index $message") }
        }
        sink.onFileStart(0, 2, "a.ncm"); sink.onFileProgress(0, 50); sink.onFileDone(0, "out/a.mp3")
        assertEquals(listOf("start 0/2 a.ncm", "prog 0 50", "done 0 out/a.mp3"), events)
    }

    @Test fun clock_returns_long() {
        val c = object : Clock { override fun nowMs() = 42L }
        assertEquals(42L, c.nowMs())
    }
}
