package com.openconverter.app.crash

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashReporterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun resetDefaultHandler() {
        Thread.setDefaultUncaughtExceptionHandler(null)
    }

    @Test
    fun `install writes a file to crashDir and chains the previous handler`() {
        resetDefaultHandler()
        val crashDir = tmp.newFolder("crash")
        var previousCalled = false
        val previous = Thread.UncaughtExceptionHandler { _, _ -> previousCalled = true }

        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        CrashReporter.install(
            context = ctx,
            crashDirProvider = { crashDir },
            clock = { 1_716_000_000_000L },
            previous = previous,
        )

        val installed = Thread.getDefaultUncaughtExceptionHandler()
        assertNotNull(installed)
        installed!!.uncaughtException(Thread.currentThread(), RuntimeException("test boom"))

        val files = crashDir.listFiles().orEmpty()
        assertEquals(1, files.size, "exactly one crash file expected")
        val content = files[0].readText(Charsets.UTF_8)
        assertTrue(content.contains("test boom"), "exception message missing in: $content")
        assertTrue(previousCalled, "previous handler must be chained")
    }

    @Test
    fun `install tolerates a crashDir that cannot be created`() {
        resetDefaultHandler()
        val unwritable = File("/proc/should-not-exist-${System.nanoTime()}")
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        var previousCalled = false
        val previous = Thread.UncaughtExceptionHandler { _, _ -> previousCalled = true }

        CrashReporter.install(
            context = ctx,
            crashDirProvider = { unwritable },
            clock = { 0L },
            previous = previous,
        )

        val installed = Thread.getDefaultUncaughtExceptionHandler()
        assertNotNull(installed)
        // Must not throw, even though the writer cannot create /proc/...
        installed!!.uncaughtException(Thread.currentThread(), RuntimeException("boom2"))
        assertTrue(previousCalled, "previous handler still chained on writer failure")
    }

    @Test
    fun `install chains the default handler when 'previous' is null`() {
        resetDefaultHandler()
        var defaultCalled = false
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> defaultCalled = true }
        val crashDir = tmp.newFolder("crash")
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()

        CrashReporter.install(
            context = ctx,
            crashDirProvider = { crashDir },
            clock = { 1_716_000_000_000L },
        )

        val installed = Thread.getDefaultUncaughtExceptionHandler()
        installed!!.uncaughtException(Thread.currentThread(), RuntimeException("boom3"))
        assertTrue(defaultCalled, "previously-set default handler must be chained")
    }
}
