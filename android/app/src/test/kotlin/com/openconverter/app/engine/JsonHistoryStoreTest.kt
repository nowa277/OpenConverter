package com.openconverter.app.engine

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class JsonHistoryStoreTest {

    private lateinit var tmpDir: File

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("history-test").toFile()
    }

    @After fun tearDown() {
        tmpDir.deleteRecursively()
    }

    private fun rec(name: String, status: HistoryStatus = HistoryStatus.SUCCESS) =
        HistoryRecord(
            ts = 1_700_000_000L,
            inputName = name,
            targetFormat = "flac",
            status = status,
            outputName = "$name.out",
            durationMs = 100L,
            error = null,
        )

    @Test fun append_then_readAll_returnsInReverseOrder() = runTest {
        val s = JsonHistoryStore(tmpDir)
        s.append(rec("a"))
        s.append(rec("b"))
        s.append(rec("c"))
        val got = s.readAll()
        assertEquals(listOf("c", "b", "a"), got.map { it.inputName })
    }

    @Test fun clear_removesFile() = runTest {
        val s = JsonHistoryStore(tmpDir)
        s.append(rec("a"))
        s.clear()
        assertTrue(s.readAll().isEmpty())
        assertTrue(!File(tmpDir, "history.jsonl").exists())
    }

    @Test fun trim_keepsNewest500_whenExceeding() = runTest {
        val s = JsonHistoryStore(tmpDir, maxEntries = 10)
        repeat(15) { i -> s.append(rec("n%02d".format(i))) }
        val got = s.readAll()
        assertEquals(10, got.size)
        // Newest first → last-appended ("n14") is at index 0
        assertEquals("n14", got[0].inputName)
        assertEquals("n05", got.last().inputName)
    }

    @Test fun append_isValidJsonObjectPerLine() = runTest {
        val s = JsonHistoryStore(tmpDir)
        s.append(rec("a", HistoryStatus.FAILED).copy(error = "boom"))
        val line = File(tmpDir, "history.jsonl").readLines().single()
        val obj = JSONObject(line)
        assertEquals("a", obj.getString("inputName"))
        assertEquals("failed", obj.getString("status"))
        assertEquals("boom", obj.getString("error"))
    }
}