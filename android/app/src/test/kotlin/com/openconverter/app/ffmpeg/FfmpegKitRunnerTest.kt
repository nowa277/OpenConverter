package com.openconverter.app.ffmpeg

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FfmpegKitRunnerTest {
    private class Sessions {
        data class Session(val args: List<String>, val done: (Result<Unit>) -> Unit, val stats: (Double) -> Unit)
        val running = mutableListOf<Session>()
        val cancelled = mutableListOf<Long>()
        val runner = FfmpegKitRunner(
            startSession = { args, done, stats ->
                running += Session(args.toList(), done, stats)
                running.size.toLong()
            },
            cancelSession = { cancelled += it },
        )
    }

    @Test fun cancelling_waits_for_native_completion_before_cleanup() = runTest {
        val sessions = Sessions()
        val progress = mutableListOf<Int>()
        var cleanupFinished = false
        val job = launch {
            try {
                sessions.runner.execute("in", "out", "mp3", null, 1000, { progress += it })
            } finally {
                cleanupFinished = true
            }
        }
        runCurrent()
        job.cancel()
        runCurrent()
        assertEquals(listOf(1L), sessions.cancelled)
        assertFalse(cleanupFinished)
        sessions.running.single().stats(500.0)
        sessions.running.single().done(Result.success(Unit))
        job.join()
        assertTrue(cleanupFinished)
        assertTrue(progress.isEmpty())
    }

    @Test fun duplicate_completion_is_ignored() = runTest {
        val sessions = Sessions()
        val job = async { sessions.runner.execute("a.wav", "a.mp3", "mp3", null) }
        runCurrent()
        sessions.running.single().done(Result.success(Unit))
        sessions.running.single().done(Result.success(Unit))
        assertTrue(job.await().isSuccess)
    }

    @Test fun concurrent_sessions_keep_their_own_progress_and_completion() = runTest {
        val sessions = Sessions()
        val firstProgress = mutableListOf<Int>()
        val secondProgress = mutableListOf<Int>()
        val first = async { sessions.runner.execute("in a\".wav", "out a.mp3", "mp3", null, 1000, { firstProgress += it }) }
        val second = async { sessions.runner.execute("b.wav", "b.mp3", "mp3", null, 2000, { secondProgress += it }) }
        runCurrent()
        sessions.running[0].stats(500.0)
        sessions.running[1].stats(500.0)
        assertEquals(listOf(50), firstProgress)
        assertEquals(listOf(25), secondProgress)
        assertTrue(sessions.running[0].args.contains("in a\".wav"))
        sessions.running[0].done(Result.success(Unit))
        assertTrue(first.await().isSuccess)
        sessions.running[1].stats(1500.0)
        sessions.running[1].done(Result.success(Unit))
        assertTrue(second.await().isSuccess)
        assertEquals(listOf(50, 100), firstProgress)
        assertEquals(listOf(25, 75, 100), secondProgress)
        sessions.running[0].stats(999.0)
        assertEquals(listOf(50, 100), firstProgress)
    }

    @Test fun cancelling_one_session_leaves_the_other_and_a_new_session_working() = runTest {
        val sessions = Sessions()
        val first = async { sessions.runner.execute("a.wav", "a.mp3", "mp3", null) }
        val second = async { sessions.runner.execute("b.wav", "b.mp3", "mp3", null) }
        runCurrent()
        first.cancel()
        runCurrent()
        assertEquals(listOf(1L), sessions.cancelled)
        sessions.running[0].done(Result.failure(InterruptedException("cancelled")))
        first.join()
        sessions.running[1].done(Result.success(Unit))
        assertTrue(second.await().isSuccess)
        val next = async { sessions.runner.execute("c.wav", "c.mp3", "mp3", null) }
        runCurrent()
        sessions.running[2].done(Result.success(Unit))
        assertTrue(next.await().isSuccess)
    }
}
