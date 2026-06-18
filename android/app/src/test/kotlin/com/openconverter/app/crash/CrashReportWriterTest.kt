package com.openconverter.app.crash

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrashReportWriterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `write creates file under dir with report fileName`() {
        val dir = tmp.newFolder("crash")
        val report = CrashReport(fileName = "crash-test-abcdef.txt", body = "hello\n")
        val out = CrashReportWriter.write(report, dir)
        assertTrue(out != null && out.exists())
        assertEquals("crash-test-abcdef.txt", out!!.name)
        assertTrue(out.readText(Charsets.UTF_8).contains("hello"))
    }

    @Test
    fun `write creates the dir if missing`() {
        val parent = tmp.newFolder("parent")
        val dir = File(parent, "crash-subdir-${System.nanoTime()}")
        assertTrue(!dir.exists())
        val report = CrashReport(fileName = "crash-x.txt", body = "body")
        val out = CrashReportWriter.write(report, dir)
        assertTrue(out != null && out.exists(), "writer should mkdirs and write")
        assertTrue(dir.isDirectory)
    }

    @Test
    fun `write returns null when dir is unwritable`() {
        // Use /proc which is read-only; mkdirs cannot create children there.
        val unwritable = File("/proc/should-not-write-here-${System.nanoTime()}")
        val report = CrashReport(fileName = "crash-x-y.txt", body = "body")
        val out = CrashReportWriter.write(report, unwritable)
        assertNull(out, "writer should return null on I/O failure, not throw")
    }

    @Test
    fun `write returns null when dir path is a regular file`() {
        val notADir = tmp.newFile("regular-file")
        val report = CrashReport(fileName = "crash-x.txt", body = "body")
        val out = CrashReportWriter.write(report, notADir)
        assertNull(out)
    }

    @Test
    fun `write overwrites existing file with same name`() {
        val dir = tmp.newFolder("crash")
        val a = CrashReport("crash-fixedhash-yz.txt", "first")
        val b = CrashReport("crash-fixedhash-yz.txt", "second")
        CrashReportWriter.write(a, dir)
        CrashReportWriter.write(b, dir)
        val written = File(dir, "crash-fixedhash-yz.txt").readText(Charsets.UTF_8)
        assertTrue(written.contains("second"))
        val matching = dir.listFiles { f -> f.name == "crash-fixedhash-yz.txt" }!!
        assertEquals(1, matching.size)
    }
}
