package com.openconverter.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BatchProgressTest {
    @Test fun completed_files_survive_a_burst_and_a_late_subscriber() {
        var progress = BatchProgress("batch")
        progress = progress.record(ProgressEvent.Done("batch", 0, "out:a"))
        repeat(150) { progress = progress.record(ProgressEvent.Progress("batch", 1, it % 100)) }
        progress = progress.record(ProgressEvent.Done("batch", 1, "out:b"))
        progress = progress.record(ProgressEvent.BatchDone("batch"))
        assertEquals(ProgressEvent.Done("batch", 0, "out:a"), progress.files[0])
        assertEquals(ProgressEvent.Done("batch", 1, "out:b"), progress.files[1])
        assertEquals(ProgressEvent.BatchDone("batch"), progress.terminal)
    }

    @Test fun late_progress_preserves_a_terminal_file_and_batch() {
        val done = BatchProgress("batch").record(ProgressEvent.Done("batch", 0, "out:a"))
        assertSame(done, done.record(ProgressEvent.Progress("batch", 0, 50)))
        assertSame(done, done.record(ProgressEvent.Done("old", 0, "old")))
        val stopped = done.record(ProgressEvent.BatchCancelled("batch"))
        assertSame(stopped, stopped.record(ProgressEvent.Done("batch", 1, "late")))
    }
}
