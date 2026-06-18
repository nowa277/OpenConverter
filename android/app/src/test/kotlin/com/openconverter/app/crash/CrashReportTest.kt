package com.openconverter.app.crash

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CrashReportTest {
    private val fixedClockMs = 1_716_000_000_000L
    private val recentLog = listOf("INFO svc.onStart n=1", "INFO svc.file.start i=0")

    @Test
    fun `body contains app version, git, device, and stack trace sections`() {
        val t = RuntimeException("boom")
        val report = CrashReport.build(
            throwable = t,
            threadName = "main",
            recentLog = recentLog,
            nowMs = fixedClockMs,
        )
        assertTrue(report.body.contains("=== Stack Trace ==="))
        assertTrue(report.body.contains("RuntimeException"), "stack class missing in body")
        assertTrue(report.body.contains("boom"), "exception message missing in body")
        assertTrue(report.body.contains("App:"), "app version line missing")
        assertTrue(report.body.contains("Device:"), "device line missing")
        assertTrue(report.body.contains("Time:"), "time line missing")
    }

    @Test
    fun `body includes recent OCLog section with each line`() {
        val report = CrashReport.build(
            throwable = RuntimeException("x"),
            threadName = "main",
            recentLog = listOf("a", "b", "c"),
            nowMs = fixedClockMs,
        )
        val idx = report.body.indexOf("=== Recent OCLog")
        assertNotNull(idx.takeIf { it >= 0 }, "Recent OCLog header not found")
        val tail = report.body.substring(idx)
        assertTrue(tail.contains("a") && tail.contains("b") && tail.contains("c"))
    }

    @Test
    fun `fileName has crash- prefix, timestamp, and 6-char hash`() {
        val report = CrashReport.build(
            throwable = RuntimeException("boom"),
            threadName = "main",
            recentLog = emptyList(),
            nowMs = fixedClockMs,
        )
        assertTrue(report.fileName.startsWith("crash-"),
            "filename should start with crash-, got: ${report.fileName}")
        assertTrue(report.fileName.endsWith(".txt"),
            "filename should end with .txt, got: ${report.fileName}")
        assertTrue(report.fileName.matches(Regex("crash-\\d{8}-\\d{6}-[0-9a-f]{6}\\.txt")),
            "filename should match crash-yyyyMMdd-HHmmss-hash6.txt, got: ${report.fileName}")
    }

    @Test
    fun `same throwable produces same hash6 (same filename)`() {
        val a = CrashReport.build(RuntimeException("same msg"), "main", emptyList(), fixedClockMs)
        val b = CrashReport.build(RuntimeException("same msg"), "main", emptyList(), fixedClockMs)
        assertEquals(a.fileName, b.fileName)
    }

    @Test
    fun `body contains privacy hint at the top`() {
        val report = CrashReport.build(
            throwable = RuntimeException("x"),
            threadName = "main",
            recentLog = emptyList(),
            nowMs = fixedClockMs,
        )
        assertTrue(
            report.body.contains("请在分享前") || report.body.contains("敏感信息"),
            "report should remind user to check for sensitive info",
        )
    }
}
