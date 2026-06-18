package com.openconverter.app.crash

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrashLogStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `list returns empty when dir is missing`() {
        val store = CrashLogStore(File(tmp.newFolder("missing").parentFile, "does-not-exist"))
        assertEquals(emptyList(), store.list())
    }

    @Test
    fun `list returns files sorted by modified time descending`() {
        val dir = tmp.newFolder("crash")
        File(dir, "crash-old.txt").writeText("old", Charsets.UTF_8)
        File(dir, "crash-old.txt").setLastModified(1_000_000L)
        File(dir, "crash-new.txt").writeText("new", Charsets.UTF_8)
        File(dir, "crash-new.txt").setLastModified(2_000_000L)
        val list = CrashLogStore(dir).list()
        assertEquals(2, list.size)
        assertEquals("crash-new.txt", list[0].fileName)
        assertEquals("crash-old.txt", list[1].fileName)
    }

    @Test
    fun `read returns file contents`() {
        val dir = tmp.newFolder("crash")
        File(dir, "crash-x.txt").writeText("body", Charsets.UTF_8)
        assertEquals("body", CrashLogStore(dir).read("crash-x.txt"))
    }

    @Test
    fun `clear removes all crash files but leaves dir`() {
        val dir = tmp.newFolder("crash")
        File(dir, "a.txt").writeText("a")
        File(dir, "b.txt").writeText("b")
        CrashLogStore(dir).clear()
        assertTrue(dir.exists())
        assertEquals(emptyList(), dir.listFiles().orEmpty().toList())
    }
}
