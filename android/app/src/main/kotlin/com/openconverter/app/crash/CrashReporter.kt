package com.openconverter.app.crash

import android.content.Context
import com.openconverter.app.log.OCLog
import java.io.File

/**
 * Installs a [Thread.UncaughtExceptionHandler] that writes a
 * [CrashReport] to `<externalFilesDir>/crash/` on every uncaught
 * exception, then chains the previously installed handler (so the
 * system "App has stopped" dialog still fires and any crash analytics
 * stays connected).
 *
 * Call from [android.app.Application.onCreate] — the earlier the better,
 * so any subsequent init failure is captured.
 */
object CrashReporter {
    private const val CRASH_SUBDIR = "crash"

    /**
     * @param context any [Context]; used only by the default
     *   [crashDirProvider] for [Context.getExternalFilesDir].
     * @param crashDirProvider overrides the default externalFilesDir
     *   lookup, so tests can inject a temp folder.
     * @param clock injected for tests; defaults to wall clock.
     * @param previous handler to chain after writing. If null, captures
     *   the current default handler at install time.
     */
    fun install(
        context: Context,
        crashDirProvider: (Context) -> File = { c ->
            File(c.getExternalFilesDir(null), CRASH_SUBDIR)
        },
        clock: () -> Long = { System.currentTimeMillis() },
        previous: Thread.UncaughtExceptionHandler? = null,
    ) {
        val chained = previous ?: Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = CrashReport.build(
                    throwable = throwable,
                    threadName = thread.name,
                    recentLog = OCLog.snapshot(),
                    nowMs = clock(),
                )
                CrashReportWriter.write(report, crashDirProvider(context))
            } catch (_: Throwable) {
                // Never let the reporter itself mask the original crash.
            } finally {
                chained?.uncaughtException(thread, throwable)
            }
        }
    }
}
