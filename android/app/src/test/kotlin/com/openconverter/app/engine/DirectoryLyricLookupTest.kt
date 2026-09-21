package com.openconverter.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectoryLyricLookupTest {
    @Test fun prefers_LrcDownload_then_LrcCache_then_root() {
        val files = mapOf(
            "LrcDownload/1" to """{"lrc":"[00:00.00]A"}""".toByteArray(),
            "LrcCache/2" to """{"lrc":"[00:00.00]B"}""".toByteArray(),
            "3" to "[00:00.00]C".toByteArray(),
        )
        val lookup = DirectoryLyricLookup { files[it] }
        assertEquals("[00:00.00]A", lookup.findLrcBytes("1")!!.toString(Charsets.UTF_8))
        assertEquals("[00:00.00]B", lookup.findLrcBytes("2")!!.toString(Charsets.UTF_8))
        assertEquals("[00:00.00]C", lookup.findLrcBytes("3")!!.toString(Charsets.UTF_8))
        assertNull(lookup.findLrcBytes("missing"))
    }

    @Test fun unparseable_file_returns_null() {
        val lookup = DirectoryLyricLookup { "????".toByteArray() }
        assertNull(lookup.findLrcBytes("1"))
    }
}
